package com.gigawalljfl.geolock

import android.app.Application
import com.gigawalljfl.geolock.data.db.GeoLockDatabase
import com.gigawalljfl.geolock.data.repository.CapsuleRepository

class GeoLockApp : Application() {

    lateinit var capsuleRepository: CapsuleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = GeoLockDatabase.getInstance(this)
        capsuleRepository = CapsuleRepository(db.timeCapsuleDao())
    }
}
