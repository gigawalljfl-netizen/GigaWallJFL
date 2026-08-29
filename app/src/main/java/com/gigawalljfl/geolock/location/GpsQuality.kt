package com.gigawalljfl.geolock.location

import androidx.compose.ui.graphics.Color
import com.gigawalljfl.geolock.ui.theme.MatrixGreen
import com.gigawalljfl.geolock.ui.theme.Magenta
import com.gigawalljfl.geolock.ui.theme.NeonCyan

/**
 * Classifica a precisão de um GeoFix em faixas legíveis, usadas de forma
 * consistente no Radar, Selamento, Detalhe e Mapa — pra nunca mais dar a
 * sensação de "não sei se isso tá bom o suficiente".
 */
object GpsQuality {
    fun label(accuracyMeters: Float): String = when {
        accuracyMeters <= 5f -> "🛰️ Excelente ±${accuracyMeters.toInt()}m"
        accuracyMeters <= 15f -> "🛰️ Boa ±${accuracyMeters.toInt()}m"
        else -> "🛰️ Fraca ±${accuracyMeters.toInt()}m — busque céu aberto"
    }

    fun color(accuracyMeters: Float): Color = when {
        accuracyMeters <= 5f -> MatrixGreen
        accuracyMeters <= 15f -> NeonCyan
        else -> Magenta
    }
}
