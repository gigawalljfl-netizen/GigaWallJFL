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
import com.gigawalljfl.geolock.data.db.Difficulty
import com.gigawalljfl.geolock.data.db.EnvironmentalLock
import com.gigawalljfl.geolock.data.db.TimeCapsule
import com.gigawalljfl.geolock.data.repository.UnlockResult
import com.gigawalljfl.geolock.gamification.GamificationState
import com.gigawalljfl.geolock.gamification.Medal
import com.gigawalljfl.geolock.gamification.XpValues
import com.gigawalljfl.geolock.location.GeoFix
import com.gigawalljfl.geolock.location.LocationRepository
import com.gigawalljfl.geolock.qr.CapsuleQrCodec
import com.gigawalljfl.geolock.sensors.SensorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        .combine(capsuleRepository.observeUnlockedCount()) { all, unlockedCount ->
            val created = all.count { it.isOwnedByCreator }
            val medals = buildSet {
                if (created >= 1) add(Medal.PIONEIRO_GEOLOCK)
                if (unlockedCount >= 5) add(Medal.EXPLORADOR_AVIDO)
                if (all.any { it.isUnlocked && it.environmentalLock != EnvironmentalLock.NONE }) add(Medal.MESTRE_DO_CLIMA)
                if (all.any { it.isOwnedByCreator && it.difficulty == Difficulty.ESPECIALISTA }) add(Medal.GUARDIAO_QUANTICO)
            }
            GamificationState(
                xp = created * XpValues.XP_PER_CAPSULE_SEALED + unlockedCount * XpValues.XP_PER_CAPSULE_UNLOCKED,
                capsulesCreated = created,
                capsulesUnlocked = unlockedCount,
                unlockedMedals = medals
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
     * Lê qualquer arquivo escolhido pelo usuário (via ActivityResultContracts.GetContent)
     * a partir do seu Uri de conteúdo — nome, mimetype e bytes — sem exigir
     * permissão persistente, já que o conteúdo é lido e cifrado imediatamente.
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
            _sealState.value = SealState.Saving
            capsuleRepository.sealCapsule(
                title = title,
                hint = hint,
                secretMessage = secretMessage,
                latitude = averagedFix.latitude,
                longitude = averagedFix.longitude,
                altitudeMeters = averagedFix.altitudeMeters,
                unlockAtEpochMillis = unlockAtEpochMillis,
                unlockRadiusMeters = unlockRadiusMeters,
                environmentalLock = environmentalLock,
                difficulty = difficulty,
                attachmentBytes = attachment?.bytes,
                attachmentFileName = attachment?.fileName,
                attachmentMimeType = attachment?.mimeType
            )
            _sealState.value = SealState.Success
        }
    }

    fun resetSealState() {
        _sealState.value = SealState.Idle
    }

    /**
     * Calcula se a condição ambiental exigida pela cápsula está satisfeita
     * agora, a partir de uma leitura de lux ao vivo (para DARKNESS/SUNLIGHT)
     * e da altitude do GeoFix atual comparada à altitude de selamento (para
     * ALTITUDE, usando GPS/altimetria de satélite conforme a spec permite).
     */
    fun isEnvironmentalConditionMet(capsule: TimeCapsule, liveLux: Float?): Boolean {
        return when (capsule.environmentalLock) {
            EnvironmentalLock.NONE -> true
            EnvironmentalLock.DARKNESS -> (liveLux ?: Float.MAX_VALUE) < SensorRepository.DARKNESS_THRESHOLD_LUX
            EnvironmentalLock.SUNLIGHT -> (liveLux ?: 0f) > SensorRepository.SUNLIGHT_THRESHOLD_LUX
            EnvironmentalLock.ALTITUDE -> {
                val current = _currentFix.value?.altitudeMeters
                val sealed = capsule.sealAltitudeMeters
                if (current != null && sealed != null) {
                    (current - sealed) >= SensorRepository.ALTITUDE_GAIN_METERS
                } else false
            }
        }
    }

    fun observeLiveLux() = sensorRepository.observeLight()

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
            val success = try {
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
                    val file = File(downloadsDir, fileName)
                    file.writeBytes(bytes)
                    true
                }
            } catch (e: Exception) {
                false
            }
            _saveFeedback.value = if (success) "Salvo em Downloads/$fileName" else "Não foi possível salvar o arquivo."
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
}
