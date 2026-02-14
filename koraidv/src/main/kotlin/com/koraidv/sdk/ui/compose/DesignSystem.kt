package com.koraidv.sdk.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Color Tokens ────────────────────────────────────────────────────────────

object KoraColors {
    val Teal = Color(0xFF0D9488)
    val TealDark = Color(0xFF0F766E)
    val Cyan = Color(0xFF06B6D4)
    val TealBright = Color(0xFF2DD4BF)

    val TealGradient = Brush.linearGradient(listOf(Teal, TealDark))
    val TealGradient135 = Brush.linearGradient(
        listOf(Teal, TealDark),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val SuccessGreen = Color(0xFF16A34A)
    val SuccessGreenDark = Color(0xFF15803D)
    val SuccessGreenLight = Color(0xFFDCFCE7)
    val SuccessGreenBorder = Color(0xFFBBF7D0)

    val ErrorRed = Color(0xFFDC2626)
    val ErrorRedDark = Color(0xFFB91C1C)
    val ErrorRedLight = Color(0xFFFEF2F2)
    val ErrorRedBorder = Color(0xFFFECACA)

    val WarningAmber = Color(0xFFD97706)
    val WarningAmberDark = Color(0xFF92400E)
    val WarningAmberMid = Color(0xFFA16207)
    val WarningAmberLight = Color(0xFFFEF3C7)
    val WarningAmberBorder = Color(0xFFFDE68A)
    val WarningAmberYellow = Color(0xFFCA8A04)
    val WarningYellowLight = Color(0xFFFEF9C3)

    val InfoBlue = Color(0xFF0284C7)
    val InfoBlueDark = Color(0xFF0369A1)
    val InfoBlueLight = Color(0xFFE0F2FE)
    val InfoBlueBorder = Color(0xFFBAE6FD)

    val Purple = Color(0xFF9333EA)
    val PurpleLight = Color(0xFFF3E8FF)
    val Indigo = Color(0xFF4F46E5)
    val IndigoLight = Color(0xFFE0E7FF)

    val DarkBg = Color(0xFF111111)
    val DarkSurface = Color(0xFF1A1A1A)

    val LightBg = Color.White
    val Surface = Color(0xFFF8FAFB)
    val SurfaceLight = Color(0xFFF9FAFB)
    val SelectedBg = Color(0xFFF0FDFA)
    val CloseButtonBg = Color(0xFFF3F4F6)
    val BorderLight = Color(0xFFE5E7EB)
    val ProgressTrack = Color(0xFFE0E0E0)

    val TextPrimary = Color(0xFF111111)
    val TextSecondary = Color(0xFF888888)
    val TextTertiary = Color(0xFF666666)
    val TextMuted = Color(0xFF999999)
    val TextDark = Color(0xFF333333)
    val TextSubtle = Color(0xFF374151)

    val WhiteAlpha12 = Color.White.copy(alpha = 0.12f)
    val WhiteAlpha15 = Color.White.copy(alpha = 0.15f)
    val WhiteAlpha20 = Color.White.copy(alpha = 0.20f)
    val WhiteAlpha40 = Color.White.copy(alpha = 0.40f)
    val WhiteAlpha50 = Color.White.copy(alpha = 0.50f)
    val WhiteAlpha60 = Color.White.copy(alpha = 0.60f)
    val WhiteAlpha80 = Color.White.copy(alpha = 0.80f)

    val RedGradient = Brush.linearGradient(
        listOf(ErrorRed, ErrorRedDark),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val BlueGradient = Brush.linearGradient(
        listOf(InfoBlue, InfoBlueDark),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val SuccessGradient = Brush.linearGradient(
        listOf(SuccessGreen, SuccessGreenDark),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
}

// ─── Button Variants ─────────────────────────────────────────────────────────

enum class KoraButtonVariant { TealGradient, DarkOutline, WhiteOutline }

@Composable
fun KoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KoraButtonVariant = KoraButtonVariant.TealGradient,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)

    when (variant) {
        KoraButtonVariant.TealGradient -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .then(
                        if (enabled) Modifier.background(KoraColors.TealGradient135, shape)
                        else Modifier.background(KoraColors.Teal.copy(alpha = 0.4f), shape)
                    )
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = (-0.2).sp
                    )
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        trailingIcon()
                    }
                }
            }
        }
        KoraButtonVariant.DarkOutline -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .border(2.dp, KoraColors.WhiteAlpha15, shape)
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-0.2).sp
                )
            }
        }
        KoraButtonVariant.WhiteOutline -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .background(Color.White, shape)
                    .border(2.dp, KoraColors.BorderLight, shape)
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = KoraColors.TextSubtle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

// ─── Step Progress Bar ───────────────────────────────────────────────────────

@Composable
fun StepProgressBar(
    total: Int = 5,
    current: Int,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(total) { index ->
            val step = index + 1
            val trackColor = if (isDark) Color.White.copy(alpha = 0.15f) else KoraColors.ProgressTrack

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .then(
                        when {
                            step < current -> Modifier.background(KoraColors.Teal)
                            step == current -> Modifier.background(
                                Brush.horizontalGradient(
                                    0f to KoraColors.Teal,
                                    0.6f to KoraColors.Teal,
                                    0.6f to trackColor,
                                    1f to trackColor
                                )
                            )
                            else -> Modifier.background(trackColor)
                        }
                    )
            )
        }
    }
}

// ─── Score Card ──────────────────────────────────────────────────────────────

@Composable
fun ScoreCard(
    score: Int,
    badge: String,
    gradientBrush: Brush,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradientBrush)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overall Score",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W500
                )
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "$score%",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = (-1).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(score / 100f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

// ─── Score Metric Row ────────────────────────────────────────────────────────

enum class MetricStatus { PASS, FAIL, BORDERLINE }

data class ScoreMetric(
    val label: String,
    val score: Int,
    val icon: ImageVector,
    val status: MetricStatus,
    val errorMessage: String? = null
)

@Composable
fun ScoreMetricRow(
    metric: ScoreMetric,
    modifier: Modifier = Modifier
) {
    val bgColor = when (metric.status) {
        MetricStatus.PASS -> KoraColors.Surface
        MetricStatus.FAIL -> KoraColors.ErrorRedLight
        MetricStatus.BORDERLINE -> Color(0xFFFFFBEB)
    }
    val iconBgColor = when (metric.status) {
        MetricStatus.PASS -> KoraColors.SuccessGreenLight
        MetricStatus.FAIL -> KoraColors.ErrorRedBorder
        MetricStatus.BORDERLINE -> KoraColors.WarningAmberLight
    }
    val iconColor = when (metric.status) {
        MetricStatus.PASS -> KoraColors.SuccessGreen
        MetricStatus.FAIL -> KoraColors.ErrorRed
        MetricStatus.BORDERLINE -> KoraColors.WarningAmber
    }
    val scoreColor = when (metric.status) {
        MetricStatus.PASS -> KoraColors.SuccessGreen
        MetricStatus.FAIL -> KoraColors.ErrorRed
        MetricStatus.BORDERLINE -> KoraColors.WarningAmber
    }
    val barTrackColor = when (metric.status) {
        MetricStatus.PASS -> KoraColors.BorderLight
        MetricStatus.FAIL -> KoraColors.ErrorRedBorder
        MetricStatus.BORDERLINE -> KoraColors.WarningAmberBorder
    }
    val barFillColor = scoreColor
    val borderColor = when (metric.status) {
        MetricStatus.FAIL -> KoraColors.ErrorRed
        MetricStatus.BORDERLINE -> KoraColors.WarningAmber
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (borderColor != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = borderColor,
                            topLeft = Offset.Zero,
                            size = Size(3.dp.toPx(), size.height)
                        )
                    }
                } else Modifier
            )
            .background(bgColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = metric.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = KoraColors.TextPrimary
                )
                Text(
                    text = "${metric.score}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barTrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(metric.score / 100f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barFillColor)
                )
            }

            if (metric.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metric.errorMessage,
                    fontSize = 12.sp,
                    color = scoreColor
                )
            }
        }
    }
}

// ─── Dark Screen Header ──────────────────────────────────────────────────────

@Composable
fun DarkScreenHeader(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glass close button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(KoraColors.WhiteAlpha12)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
        }

        // Centered title
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.W600
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = KoraColors.WhiteAlpha50,
                    fontSize = 13.sp
                )
            }
        }

        // Spacer to balance close button
        Spacer(modifier = Modifier.size(40.dp))
    }
}

// ─── Guidance Pill ───────────────────────────────────────────────────────────

enum class GuidancePillVariant { Scanning, Ready }

@Composable
fun GuidancePill(
    text: String,
    variant: GuidancePillVariant = GuidancePillVariant.Scanning,
    modifier: Modifier = Modifier
) {
    val bgColor = when (variant) {
        GuidancePillVariant.Scanning -> KoraColors.Teal.copy(alpha = 0.15f)
        GuidancePillVariant.Ready -> Color(0xFF10B981).copy(alpha = 0.15f)
    }
    val textColor = when (variant) {
        GuidancePillVariant.Scanning -> KoraColors.TealBright
        GuidancePillVariant.Ready -> Color(0xFF34D399)
    }
    val dotColor = textColor

    val infiniteTransition = rememberInfiniteTransition(label = "pill_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.W500
        )
    }
}

// ─── Icon Circle ─────────────────────────────────────────────────────────────

@Composable
fun IconCircle(
    icon: ImageVector,
    bgColor: Color,
    iconColor: Color,
    size: Dp = 56.dp,
    iconSize: Dp = 36.dp,
    outerRingColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (outerRingColor != null) {
            Box(
                modifier = Modifier
                    .size(size + 16.dp)
                    .border(2.dp, outerRingColor, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconColor
            )
        }
    }
}

// ─── Oval Viewfinder ─────────────────────────────────────────────────────────

val OvalViewfinderShape = RoundedCornerShape(
    topStartPercent = 50,
    topEndPercent = 50,
    bottomStartPercent = 50,
    bottomEndPercent = 50
)

@Composable
fun OvalViewfinder(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .width(240.dp)
            .height(300.dp)
            .clip(OvalViewfinderShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A2332), Color(0xFF0F1720))
                ),
                OvalViewfinderShape
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ─── Light Header (Close / Back) ─────────────────────────────────────────────

@Composable
fun LightCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(KoraColors.CloseButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            modifier = Modifier.size(18.dp),
            tint = KoraColors.TextTertiary
        )
    }
}

@Composable
fun LightBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(KoraColors.CloseButtonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(18.dp),
            tint = KoraColors.TextTertiary
        )
    }
}

// ─── Review Quality Check ────────────────────────────────────────────────────

@Composable
fun ReviewQualityCheck(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF34D399)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = KoraColors.WhiteAlpha60
        )
    }
}

// ─── Step Pill (Front/Back indicators) ───────────────────────────────────────

enum class StepPillState { Active, Inactive, Done }

@Composable
fun StepPill(
    text: String,
    state: StepPillState,
    modifier: Modifier = Modifier
) {
    val bgColor = when (state) {
        StepPillState.Active -> KoraColors.Teal
        StepPillState.Inactive -> Color.White.copy(alpha = 0.08f)
        StepPillState.Done -> KoraColors.Teal.copy(alpha = 0.2f)
    }
    val textColor = when (state) {
        StepPillState.Active -> Color.White
        StepPillState.Inactive -> KoraColors.WhiteAlpha40
        StepPillState.Done -> KoraColors.TealBright
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = textColor
        )
    }
}

// ─── Challenge Progress Dots ─────────────────────────────────────────────────

enum class ChallengeDotState { Done, Current, Pending }

@Composable
fun ChallengeDots(
    total: Int = 3,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val state = when {
                index < currentIndex -> ChallengeDotState.Done
                index == currentIndex -> ChallengeDotState.Current
                else -> ChallengeDotState.Pending
            }
            val color = when (state) {
                ChallengeDotState.Done -> KoraColors.Teal
                ChallengeDotState.Current -> KoraColors.TealBright
                ChallengeDotState.Pending -> KoraColors.WhiteAlpha15
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ─── Countdown Badge ─────────────────────────────────────────────────────────

@Composable
fun CountdownBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(KoraColors.ErrorRed),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.W700
        )
    }
}

// ─── Review Badge ────────────────────────────────────────────────────────────

@Composable
fun ReviewBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color(0xFF10B981).copy(alpha = 0.2f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF34D399))
        )
        Text(
            text = text,
            color = Color(0xFF34D399),
            fontSize = 12.sp,
            fontWeight = FontWeight.W600
        )
    }
}
