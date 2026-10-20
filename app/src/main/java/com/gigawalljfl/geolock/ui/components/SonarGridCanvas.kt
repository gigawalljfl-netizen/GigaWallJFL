package com.gigawalljfl.geolock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fundo decorativo de radar sonar: anéis concêntricos, retículo, raios em
 * 8 direções, e um feixe de varredura girando continuamente com rastro
 * esmaecendo — usado tanto no Radar tático quanto no HUD de AR, pra dar a
 * mesma linguagem visual "imersiva" nos dois lugares. Puramente decorativo;
 * cada tela desenha seus próprios blips por cima, na mesma orientação.
 */
@Composable
fun SonarGridCanvas(
    modifier: Modifier = Modifier,
    ringColor: Color,
    sweepColor: Color,
    ringCount: Int = 5,
    sweepDurationMillis: Int = 4200
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(sweepDurationMillis, easing = LinearEasing)
        ),
        label = "sweep_angle"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val thinStrokePx = 1.dp.toPx()
        val ringStrokePx = 1.2.dp.toPx()
        val sweepTrailStrokePx = 1.2.dp.toPx()
        val sweepMainStrokePx = 2.5.dp.toPx()
        val centerDotRadiusPx = 3.dp.toPx()

        for (i in 1..ringCount) {
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = maxRadius * (i.toFloat() / ringCount),
                center = center,
                style = Stroke(width = ringStrokePx)
            )
        }

        drawLine(
            ringColor.copy(alpha = 0.25f),
            Offset(center.x - maxRadius, center.y),
            Offset(center.x + maxRadius, center.y),
            strokeWidth = thinStrokePx
        )
        drawLine(
            ringColor.copy(alpha = 0.25f),
            Offset(center.x, center.y - maxRadius),
            Offset(center.x, center.y + maxRadius),
            strokeWidth = thinStrokePx
        )

        for (angleDeg in 0 until 360 step 45) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val end = Offset(
                center.x + (maxRadius * cos(rad)).toFloat(),
                center.y + (maxRadius * sin(rad)).toFloat()
            )
            drawLine(ringColor.copy(alpha = 0.18f), center, end, strokeWidth = thinStrokePx)
        }

        // Feixe de varredura: várias linhas em leque com alfa decrescente,
        // simulando um rastro que se apaga atrás da linha principal
        val trailSteps = 18
        for (t in trailSteps downTo 0) {
            val trailAngle = sweepAngle - t * 1.5f
            val rad = Math.toRadians(trailAngle.toDouble())
            val end = Offset(
                center.x + (maxRadius * cos(rad)).toFloat(),
                center.y + (maxRadius * sin(rad)).toFloat()
            )
            val alpha = (1f - t.toFloat() / trailSteps) * 0.5f
            drawLine(sweepColor.copy(alpha = alpha), center, end, strokeWidth = if (t == 0) sweepMainStrokePx else sweepTrailStrokePx)
        }

        drawCircle(color = sweepColor, radius = centerDotRadiusPx, center = center)
    }
}
