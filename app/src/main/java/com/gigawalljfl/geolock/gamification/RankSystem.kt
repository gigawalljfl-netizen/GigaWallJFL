package com.gigawalljfl.geolock.gamification

enum class CyberRank(val displayName: String, val emoji: String, val minUnlocked: Int) {
    RECRUTA_DO_TEMPO("Recruta do Tempo", "🔰", 0),
    DECODIFICADOR_QUANTICO("Decodificador Quântico", "🛡️", 1),
    MESTRE_DAS_ANOMALIAS("Mestre das Anomalias", "⚡", 3),
    ESPECIALISTA_TEMPORAL("Especialista Temporal", "👑", 5);

    companion object {
        fun forUnlockedCount(count: Int): CyberRank =
            entries.sortedByDescending { it.minUnlocked }.first { count >= it.minUnlocked }
    }
}

enum class Medal(val displayName: String, val description: String, val emoji: String) {
    PIONEIRO_GEOLOCK("Pioneiro GeoLock", "Selou a primeira cápsula", "🥇"),
    EXPLORADOR_AVIDO("Explorador Ávido", "Desbloqueou 5 cápsulas", "🧭"),
    MESTRE_DO_CLIMA("Mestre do Clima", "Desbloqueou uma cápsula com bloqueio ambiental", "🌦️"),
    GUARDIAO_QUANTICO("Guardião Quântico", "Criou uma cápsula de dificuldade Especialista", "🔮")
}

data class GamificationState(
    val xp: Int,
    val capsulesCreated: Int,
    val capsulesUnlocked: Int,
    val unlockedMedals: Set<Medal>
) {
    val rank: CyberRank get() = CyberRank.forUnlockedCount(capsulesUnlocked)
}

object XpValues {
    const val XP_PER_CAPSULE_SEALED = 20
    const val XP_PER_CAPSULE_UNLOCKED = 50
}
