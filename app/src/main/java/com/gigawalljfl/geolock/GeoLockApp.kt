package com.gigawalljfl.geolock

import android.app.Application
import com.gigawalljfl.geolock.data.db.GeoLockDatabase
import com.gigawalljfl.geolock.data.repository.CapsuleRepository
import org.osmdroid.config.Configuration
import java.io.File

class GeoLockApp : Application() {

    lateinit var capsuleRepository: CapsuleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = GeoLockDatabase.getInstance(this)
        capsuleRepository = CapsuleRepository(db.timeCapsuleDao(), db.earnedMedalDao(), applicationContext)

        // Configuração do osmdroid: cache de tiles em pasta interna do app,
        // evitando a necessidade de permissão de armazenamento externo.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles").also { it.mkdirs() }
            osmdroidBasePath = File(cacheDir, "osmdroid").also { it.mkdirs() }
        }
    }
}
