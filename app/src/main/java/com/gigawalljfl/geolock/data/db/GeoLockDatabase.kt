package com.gigawalljfl.geolock.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromEnvironmentalLock(value: EnvironmentalLock): String = value.name

    @TypeConverter
    fun toEnvironmentalLock(value: String): EnvironmentalLock = EnvironmentalLock.valueOf(value)

    @TypeConverter
    fun fromDifficulty(value: Difficulty): String = value.name

    @TypeConverter
    fun toDifficulty(value: String): Difficulty = Difficulty.valueOf(value)
}

@Database(entities = [TimeCapsule::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GeoLockDatabase : RoomDatabase() {

    abstract fun timeCapsuleDao(): TimeCapsuleDao

    companion object {
        @Volatile
        private var INSTANCE: GeoLockDatabase? = null

        fun getInstance(context: Context): GeoLockDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GeoLockDatabase::class.java,
                    "geolock.db"
                )
                    // Migrations automáticas conforme convenção do projeto;
                    // versão inicial não requer migração ainda.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
