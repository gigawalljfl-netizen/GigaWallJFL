package com.gigawalljfl.geolock.ui

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigawalljfl.geolock.GeoLockApp
import com.gigawalljfl.geolock.auth.AuthRepository
import com.gigawalljfl.geolock.auth.AuthResult
import com.gigawalljfl.geolock.backup.BackupManager
import com.gigawalljfl.geolock.cloud.CloudStorageRepository
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EarnedMedal
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.data.repository.UnlockResult
import com.gigawalljfl.geolock.gamification.GamificationState
import com.gigawalljfl.geolock.gamification.Medal
import com.gigawalljfl.geolock.gamification.XpValues
import com.gigawalljfl.geolock.location.GeoFix
import com.gigawalljfl.geolock.location.LocationRepository
import com.gigawalljfl.geolock.media.ImageCompressor
import com.gigawalljfl.geolock.qr.CapsuleQrCodec
import com.gigawalljfl.geolock.sensors.SensorRepository
import com.gigawalljfl.geolock.util.SecureBackupPassphraseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Um anexo escolhido localmente na tela de selamento, ainda não cifrado. */
data class PendingAttachment(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

sealed class SealState {
    object Idle : SealState()
    object Calibrating : SealState()
    object Saving : SealState()
    object Success : SealState()
    object NoGpsFix : SealState()
}

class GeoLockViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GeoLockApp
    private val capsuleRepository = app.capsuleRepository
    private val authRepository = app.authRepository
    private val cloudStorageRepository = app.cloudStorageRepository

    val currentUser = authRepository.currentUser
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, authRepository.currentUserSnapshot())

    private val _authFeedback = MutableStateFlow<String?>(null)
    val authFeedback: StateFlow<String?> = _authFeedback.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authFeedback.value = "Preenche e-mail e senha."
            return
        }
        _isAuthLoading.value = true
        viewModelScope.launch {
            when (val result = authRepository.signUp(email, password)) {
                is AuthResult.Success -> _authFeedback.value = "Conta criada — já estás autenticado."
                is AuthResult.Error -> _authFeedback.value = result.message
            }
            _isAuthLoading.value = false
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authFeedback.value = "Preenche e-mail e senha."
            return
        }
        _isAuthLoading.value = true
        viewModelScope.launch {
            when (val result = authRepository.signIn(email, password)) {
                is AuthResult.Success -> _authFeedback.value = "Login feito com sucesso."
                is AuthResult.Error -> _authFeedback.value = result.message
            }
            _isAuthLoading.value = false
        }
    }

    fun signOut() {
        authRepository.signOut()
        _authFeedback.value = null
    }

    fun clearAuthFeedback() {
        _authFeedback.value = null
    }
    private val locationRepository = LocationRepository(application)
    private val sensorRepository = SensorRepository(application)

    val capsules: StateFlow<List<TimeCapsule>> = capsuleRepository.observeAll()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _currentFix = MutableStateFlow<GeoFix?>(null)
    val currentFix: StateFlow<GeoFix?> = _currentFix.asStateFlow()

    private val _selectedCapsuleId = MutableStateFlow<String?>(null)
    val selectedCapsuleId: StateFlow<String?> = _selectedCapsuleId.asStateFlow()

    private val _unlockResult = MutableStateFlow<UnlockResult?>(null)
    val unlockResult: StateFlow<UnlockResult?> = _unlockResult.asStateFlow()

    private val _decryptedMediaBitmap = MutableStateFlow<Bitmap?>(null)
    val decryptedMediaBitmap: StateFlow<Bitmap?> = _decryptedMediaBitmap.asStateFlow()

    private val _decryptedAttachmentBytes = MutableStateFlow<ByteArray?>(null)
    val decryptedAttachmentBytes: StateFlow<ByteArray?> = _decryptedAttachmentBytes.asStateFlow()

    private val _sealState = MutableStateFlow<SealState>(SealState.Idle)
    val sealState: StateFlow<SealState> = _sealState.asStateFlow()

    private val _isCalibratingUnlock = MutableStateFlow(false)
    val isCalibratingUnlock: StateFlow<Boolean> = _isCalibratingUnlock.asStateFlow()

    private val _saveFeedback = MutableStateFlow<String?>(null)
    val saveFeedback: StateFlow<String?> = _saveFeedback.asStateFlow()

    private val _importFeedback = MutableStateFlow<String?>(null)
    val importFeedback: StateFlow<String?> = _importFeedback.asStateFlow()

    val gamificationState: StateFlow<GamificationState> = capsules
        .combine(capsuleRepository.observeUnlockedCount()) { all, unlockedCount -> all to unlockedCount }
        .combine(capsuleRepository.observeEarnedMedals()) { (all, unlockedCount), earned ->
            val created = all.count { it.isOwnedByCreator }
            val nowEligible = buildSet {
                if (created >= 1) add(Medal.PIONEIRO_GEOLOCK)
                if (unlockedCount >= 5) add(Medal.EXPLORADOR_AVIDO)
                if (all.any { it.isUnlocked && it.environmentalLock != EnvironmentalLock.NONE }) add(Medal.MESTRE_DO_CLIMA)
                if (all.any { it.isOwnedByCreator && it.difficulty == Difficulty.ESPECIALISTA }) add(Medal.GUARDIAO_QUANTICO)
            }
            // Toda medalha recém-elegível e ainda não persistida é gravada
            // agora — dali em diante ela nunca mais "some", mesmo que a
            // cápsula que a rendeu seja apagada depois.
            val earnedIds = earned.mapNotNull { it.medal?.name }.toSet()
            val newlyEarned = nowEligible.filter { it.name !in earnedIds }
            if (newlyEarned.isNotEmpty()) {
                viewModelScope.launch {
                    newlyEarned.forEach { capsuleRepository.recordMedalEarned(it.name) }
                }
            }
            val persistedMedals = earned.mapNotNull { it.medal }.toSet() + nowEligible

            GamificationState(
                xp = created * XpValues.XP_PER_CAPSULE_SEALED + unlockedCount * XpValues.XP_PER_CAPSULE_UNLOCKED,
                capsulesCreated = created,
                capsulesUnlocked = unlockedCount,
                unlockedMedals = persistedMedals
            )
        }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            GamificationState(0, 0, 0, emptySet())
        )

    fun startTrackingLocation() {
        viewModelScope.launch {
            locationRepository.observeHighPrecisionFixes().collect { fix ->
                _currentFix.value = fix
            }
        }
    }

    fun selectCapsule(id: String?) {
        _selectedCapsuleId.value = id
        _unlockResult.value = null
        _decryptedMediaBitmap.value = null
        _decryptedAttachmentBytes.value = null
    }

    /**
     * Lê um arquivo bruto a partir do seu Uri — usado hoje só pra ler o
     * arquivo de backup cifrado (.glbk) na hora de restaurar (ver
     * importBackup). Anexos de cápsula não usam mais isso — desde que a
     * opção de anexar arquivo qualquer foi removida, anexos são sempre
     * imagem e passam por compressImageFromUri/compressBitmapForAttachment.
     */
    suspend fun readAttachmentFromUri(uri: Uri): PendingAttachment? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var fileName = "arquivo"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        PendingAttachment(bytes, fileName, mimeType)
    }

    private val _isProcessingImage = MutableStateFlow(false)
    val isProcessingImage: StateFlow<Boolean> = _isProcessingImage.asStateFlow()

    /** Comprime uma foto tirada agora pela câmera antes de anexar à cápsula. */
    suspend fun compressBitmapForAttachment(bitmap: Bitmap): PendingAttachment {
        _isProcessingImage.value = true
        try {
            val bytes = withContext(Dispatchers.Default) { ImageCompressor.compressBitmap(bitmap) }
            return PendingAttachment(bytes, "foto_${System.currentTimeMillis()}.jpg", "image/jpeg")
        } finally {
            _isProcessingImage.value = false
        }
    }

    /** Lê e comprime uma imagem escolhida na galeria antes de anexar à cápsula. */
    suspend fun compressImageFromUri(uri: Uri): PendingAttachment? {
        _isProcessingImage.value = true
        try {
            val bytes = withContext(Dispatchers.IO) {
                ImageCompressor.loadAndCompressFromUri(getApplication(), uri)
            } ?: return null
            return PendingAttachment(bytes, "imagem_${System.currentTimeMillis()}.jpg", "image/jpeg")
        } finally {
            _isProcessingImage.value = false
        }
    }

    fun sealCapsule(
        title: String,
        hint: String,
        secretMessage: String,
        unlockAtEpochMillis: Long,
        unlockRadiusMeters: Double,
        environmentalLock: EnvironmentalLock,
        difficulty: Difficulty,
        attachment: PendingAttachment? = null
    ) {
        if (_currentFix.value == null) {
            _sealState.value = SealState.NoGpsFix
            return
        }
        _sealState.value = SealState.Calibrating
        viewModelScope.launch {
            val averagedFix = locationRepository.getAveragedFix()
            if (averagedFix == null) {
                _sealState.value = SealState.NoGpsFix
                return@launch
            }
            val sealPressure = readPressureOnce()
            _sealState.value = SealState.Saving
            val created = capsuleRepository.sealCapsule(
                title = title,
                hint = hint,
                secretMessage = secretMessage,
                latitude = averagedFix.latitude,
                longitude = averagedFix.longitude,
                altitudeMeters = averagedFix.altitudeMeters,
                sealPressureHpa = sealPressure,
                unlockAtEpochMillis = unlockAtEpochMillis,
                unlockRadiusMeters = unlockRadiusMeters,
                environmentalLock = environmentalLock,
                difficulty = difficulty,
                attachmentBytes = attachment?.bytes,
                attachmentFileName = attachment?.fileName,
                attachmentMimeType = attachment?.mimeType
            )
            _sealState.value = SealState.Success

            // Se estiver logado, sobe a imagem JÁ CIFRADA (nunca a original em
            // texto claro) pra nuvem em segundo plano — falha aqui nunca afeta
            // o selamento local, que já está garantido no passo acima.
            created.encryptedMediaPath?.let { path ->
                uploadEncryptedAttachmentToCloud(created.id, path)
            }

            // Se houver senha de backup salva (ver SecureBackupPassphraseStore),
            // sincroniza a coleção inteira sozinho — sem isso, "recuperar todo
            // o progresso" dependia de lembrar de tocar num botão manualmente.
            autoSyncBackupIfEnabled()
        }
    }

    /** Sobe o backup completo pra nuvem automaticamente, usando a senha salva no Keystore — silencioso, sem travar nada se falhar. */
    private fun autoSyncBackupIfEnabled() {
        val uid = authRepository.currentUserSnapshot()?.uid ?: return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            // A leitura do Keystore precisa estar AQUI DENTRO, depois do
            // Dispatchers.IO — rodar isso direto na thread principal (como
            // estava antes) trava a interface e o Android mata o app (ANR).
            val passphrase = SecureBackupPassphraseStore.load(context) ?: return@launch
            val token = authRepository.getIdToken() ?: return@launch
            try {
                val bytes = BackupManager.export(capsules.value, passphrase)
                cloudStorageRepository.upload("$uid/backup.glbk", bytes, "application/octet-stream", token)
            } catch (e: Exception) {
                // Sincronização automática silenciosa — falha aqui não deve
                // incomodar quem só quer selar uma cápsula. O botão manual em
                // Configurações continua disponível como reforço.
            }
        }
    }

    private val _autoSyncEnabled = MutableStateFlow(false)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun refreshAutoSyncStatus() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = SecureBackupPassphraseStore.hasSavedPassphrase(context)
            _autoSyncEnabled.value = enabled
        }
    }

    /** Salva a senha cifrada no Keystore e liga a sincronização automática — dispara um envio imediato de conferência. */
    fun enableAutoSync(passphrase: String) {
        if (passphrase.length < 6) {
            _backupFeedback.value = "Usa uma senha de pelo menos 6 caracteres."
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val success = SecureBackupPassphraseStore.save(context, passphrase)
            if (success) {
                _autoSyncEnabled.value = true
                _backupFeedback.value = "Sincronização automática ativada."
                autoSyncBackupIfEnabled()
            } else {
                _backupFeedback.value = "Não foi possível ativar — o aparelho recusou guardar a senha cifrada."
            }
        }
    }

    fun disableAutoSync() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            SecureBackupPassphraseStore.clear(context)
            _autoSyncEnabled.value = false
            _backupFeedback.value = "Sincronização automática desativada."
        }
    }

    private fun uploadEncryptedAttachmentToCloud(capsuleId: String, localEncryptedPath: String) {
        val uid = authRepository.currentUserSnapshot()?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val token = authRepository.getIdToken() ?: return@launch
            val encryptedBytes = try {
                File(localEncryptedPath).readBytes()
            } catch (e: Exception) {
                return@launch
            }
            cloudStorageRepository.upload("$uid/images/$capsuleId.enc", encryptedBytes, "application/octet-stream", token)
        }
    }

    /** Colhe uma única leitura do barômetro (quando disponível), com timeout curto. */
    /**
     * Colhe uma única leitura do barômetro (quando disponível), com timeout
     * curto. Usa firstOrNull() — não first() — de propósito: em aparelhos
     * sem barômetro (comum em modelos de entrada), observePressure() fecha
     * o fluxo imediatamente sem emitir nada, e first() lança
     * NoSuchElementException nesse caso, uma exceção que o withTimeoutOrNull
     * NÃO captura (só captura timeout, não "fluxo terminou vazio") —
     * derrubava o app inteiro ao selar, em qualquer aparelho sem esse
     * sensor.
     */
    private suspend fun readPressureOnce(): Float? = withTimeoutOrNull(1200L) {
        sensorRepository.observePressure().firstOrNull()
    }

    fun resetSealState() {
        _sealState.value = SealState.Idle
    }

    /**
     * Calcula se a condição ambiental exigida pela cápsula está satisfeita
     * agora, a partir de uma leitura de lux ao vivo (para DARKNESS/SUNLIGHT)
     * e, para ALTITUDE, de uma FUSÃO barômetro+GPS: o barômetro é muito
     * mais sensível a pequenos ganhos de altura (útil em prédios, onde o
     * GPS erra facilmente), então é usado como sinal primário quando
     * disponível — a condição também aceita o ganho medido só por GPS
     * como alternativa, pra não travar em aparelhos sem barômetro ou
     * quando o sensor falhar.
     */
    fun isEnvironmentalConditionMet(capsule: TimeCapsule, liveLux: Float?, livePressureHpa: Float? = null): Boolean {
        return when (capsule.environmentalLock) {
            EnvironmentalLock.NONE -> true
            EnvironmentalLock.DARKNESS -> (liveLux ?: Float.MAX_VALUE) < SensorRepository.DARKNESS_THRESHOLD_LUX
            EnvironmentalLock.SUNLIGHT -> (liveLux ?: 0f) > SensorRepository.SUNLIGHT_THRESHOLD_LUX
            EnvironmentalLock.ALTITUDE -> {
                val barometricGain = if (livePressureHpa != null && capsule.sealPressureHpa != null) {
                    SensorRepository.pressureToAltitude(livePressureHpa, capsule.sealPressureHpa)
                } else null
                val gpsGain = _currentFix.value?.altitudeMeters?.let { current ->
                    capsule.sealAltitudeMeters?.let { sealed -> current - sealed }
                }
                val metByBarometer = barometricGain != null && barometricGain >= SensorRepository.ALTITUDE_GAIN_METERS
                val metByGps = gpsGain != null && gpsGain >= SensorRepository.ALTITUDE_GAIN_METERS
                metByBarometer || metByGps
            }
        }
    }

    fun observeLiveLux() = sensorRepository.observeLight()
    fun observeLivePressure() = sensorRepository.observePressure()

    fun attemptUnlock(capsuleId: String, environmentalConditionMet: Boolean) {
        if (_currentFix.value == null) {
            _unlockResult.value = UnlockResult.NoGpsFix
            return
        }
        _isCalibratingUnlock.value = true
        viewModelScope.launch {
            val fix = locationRepository.getAveragedFix()
            _isCalibratingUnlock.value = false
            if (fix == null) {
                _unlockResult.value = UnlockResult.NoGpsFix
                return@launch
            }
            val result = capsuleRepository.attemptUnlock(
                capsuleId = capsuleId,
                currentLatitude = fix.latitude,
                currentLongitude = fix.longitude,
                currentEpochMillis = System.currentTimeMillis(),
                environmentalConditionMet = environmentalConditionMet
            )
            _unlockResult.value = result
        }
    }

    fun loadDecryptedMedia(capsule: TimeCapsule) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = capsuleRepository.decryptMediaIfAny(capsule)
            _decryptedAttachmentBytes.value = bytes
            val isImage = capsule.attachmentMimeType?.startsWith("image/") == true
            _decryptedMediaBitmap.value = if (isImage && bytes != null) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        }
    }

    /**
     * Salva o anexo já decifrado (deve estar em decryptedAttachmentBytes)
     * na pasta Downloads pública do dispositivo — usa MediaStore em
     * Android 10+ e escrita direta em versões mais antigas.
     */
    fun saveDecryptedAttachmentToDownloads(capsule: TimeCapsule) {
        val bytes = _decryptedAttachmentBytes.value ?: return
        val fileName = capsule.attachmentFileName ?: "geolock_anexo_${capsule.id}"
        val mimeType = capsule.attachmentMimeType ?: "application/octet-stream"

        viewModelScope.launch(Dispatchers.IO) {
            val success = saveBytesToDownloads(bytes, fileName, mimeType)
            _saveFeedback.value = if (success) "Salvo em Downloads/$fileName" else "Não foi possível salvar o arquivo."
        }
    }

    /** Grava bytes arbitrários em Downloads — usado tanto pra anexos quanto pra backups. */
    private fun saveBytesToDownloads(bytes: ByteArray, fileName: String, mimeType: String): Boolean {
        return try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                uri != null
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, fileName).writeBytes(bytes)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private val _backupFeedback = MutableStateFlow<String?>(null)
    val backupFeedback: StateFlow<String?> = _backupFeedback.asStateFlow()

    /** Gera um backup cifrado por senha de TODAS as cápsulas (mensagens; anexos não entram) e salva em Downloads. */
    fun exportBackup(passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = BackupManager.export(capsules.value, passphrase)
                val fileName = "geolock_backup_${System.currentTimeMillis()}.glbk"
                val success = saveBytesToDownloads(bytes, fileName, "application/octet-stream")
                _backupFeedback.value = if (success) "Backup salvo em Downloads/$fileName" else "Não foi possível salvar o backup."
            } catch (e: Exception) {
                _backupFeedback.value = "Erro ao gerar backup — tenta de novo."
            }
        }
    }

    /** Lê um arquivo de backup escolhido pelo usuário, decifra com a senha e restaura as cápsulas. */
    fun importBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileData = readAttachmentFromUri(uri)
            if (fileData == null) {
                _backupFeedback.value = "Não foi possível ler o arquivo."
                return@launch
            }
            val restored = BackupManager.import(fileData.bytes, passphrase)
            if (restored == null) {
                _backupFeedback.value = "Senha incorreta ou arquivo de backup inválido."
                return@launch
            }
            restored.forEach { capsuleRepository.restoreCapsule(it) }
            _backupFeedback.value = "${restored.size} cápsula(s) restaurada(s) com sucesso."
        }
    }

    fun clearBackupFeedback() {
        _backupFeedback.value = null
    }

    /** Envia o backup cifrado por senha pro Supabase (exige login) em vez de salvar localmente. */
    fun uploadBackupToCloud(passphrase: String) {
        val uid = authRepository.currentUserSnapshot()?.uid
        if (uid == null) {
            _backupFeedback.value = "Entra na tua conta primeiro."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val token = authRepository.getIdToken()
            if (token == null) {
                _backupFeedback.value = "Sessão expirada — entra de novo."
                return@launch
            }
            try {
                val bytes = BackupManager.export(capsules.value, passphrase)
                val success = cloudStorageRepository.upload("$uid/backup.glbk", bytes, "application/octet-stream", token)
                _backupFeedback.value = if (success) "Backup enviado pra nuvem com sucesso." else "Não foi possível enviar — confere a conexão."
            } catch (e: Exception) {
                _backupFeedback.value = "Erro ao gerar backup — tenta de novo."
            }
        }
    }

    /**
     * Baixa o backup do Supabase, decifra com a senha, restaura as cápsulas
     * e — pra cada uma que tinha imagem — baixa também a imagem cifrada
     * correspondente e a religa localmente (o caminho antigo, de outro
     * aparelho, não existe mais aqui).
     */
    fun restoreBackupFromCloud(passphrase: String) {
        val uid = authRepository.currentUserSnapshot()?.uid
        if (uid == null) {
            _backupFeedback.value = "Entra na tua conta primeiro."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val token = authRepository.getIdToken()
            if (token == null) {
                _backupFeedback.value = "Sessão expirada — entra de novo."
                return@launch
            }
            val backupBytes = cloudStorageRepository.download("$uid/backup.glbk", token)
            if (backupBytes == null) {
                _backupFeedback.value = "Nenhum backup encontrado na nuvem pra essa conta."
                return@launch
            }
            val restored = BackupManager.import(backupBytes, passphrase)
            if (restored == null) {
                _backupFeedback.value = "Senha incorreta ou backup corrompido."
                return@launch
            }

            var imagesRestored = 0
            restored.forEach { capsule ->
                var toInsert = capsule
                if (capsule.attachmentFileName != null) {
                    val imageBytes = cloudStorageRepository.download("$uid/images/${capsule.id}.enc", token)
                    if (imageBytes != null) {
                        val localFile = File(getApplication<Application>().filesDir, "capsule_${capsule.id}.attachment.enc")
                        localFile.writeBytes(imageBytes)
                        toInsert = capsule.copy(encryptedMediaPath = localFile.absolutePath)
                        imagesRestored++
                    }
                }
                capsuleRepository.restoreCapsule(toInsert)
            }
            _backupFeedback.value = "${restored.size} cápsula(s) restaurada(s), $imagesRestored imagem(ns) recuperada(s)."
        }
    }

    fun clearSaveFeedback() {
        _saveFeedback.value = null
    }

    fun exportCapsuleAsQrPayload(capsule: TimeCapsule): String = CapsuleQrCodec.encode(capsule)

    fun importCapsuleFromQr(payload: String) {
        viewModelScope.launch {
            val capsule = CapsuleQrCodec.decode(payload)
            if (capsule != null) {
                capsuleRepository.importCapsule(capsule)
                _importFeedback.value = "Cápsula \"${capsule.title}\" importada com sucesso."
            } else {
                _importFeedback.value = "QR Code inválido — não é uma cápsula GeoLock."
            }
        }
    }

    fun clearImportFeedback() {
        _importFeedback.value = null
    }

    fun distanceToCapsule(capsule: TimeCapsule): Double? {
        val fix = _currentFix.value ?: return null
        return LocationRepository.haversineMeters(
            capsule.sealLatitude, capsule.sealLongitude,
            fix.latitude, fix.longitude
        )
    }

    val earnedMedals: StateFlow<List<EarnedMedal>> = capsuleRepository.observeEarnedMedals()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun deleteCapsule(capsule: TimeCapsule) {
        viewModelScope.launch {
            capsuleRepository.delete(capsule)
        }
        if (_selectedCapsuleId.value == capsule.id) {
            _selectedCapsuleId.value = null
        }
    }

    fun updateCapsuleTitleAndHint(capsule: TimeCapsule, newTitle: String, newHint: String) {
        viewModelScope.launch {
            capsuleRepository.updateTitleAndHint(capsule, newTitle, newHint)
        }
    }
}
