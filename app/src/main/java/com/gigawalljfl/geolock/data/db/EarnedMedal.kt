package com.gigawalljfl.geolock.data.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.gigawalljfl.geolock.gamification.Medal

/**
 * Registro persistido de uma medalha conquistada, com a data em que foi
 * ganha. Diferente de recalcular medalhas a partir do estado atual das
 * cápsulas, isso garante que uma medalha, uma vez ganha, nunca "some" —
 * mesmo que a cápsula que a desbloqueou seja apagada depois.
 */
@Entity(tableName = "earned_medals")
data class EarnedMedal(
    @PrimaryKey val medalId: String,
    val earnedAtEpochMillis: Long
) {
    // @Ignore garante que o Room nunca tente tratar isso como uma coluna —
    // é só uma conveniência computada a partir de medalId, nunca persistida.
    @Ignore
    val medal: Medal? = Medal.entries.find { it.name == medalId }
}
