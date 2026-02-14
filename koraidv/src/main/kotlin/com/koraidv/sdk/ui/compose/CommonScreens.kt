package com.koraidv.sdk.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koraidv.sdk.KoraException
import com.koraidv.sdk.Verification
import com.koraidv.sdk.VerificationStatus
import com.koraidv.sdk.ui.ProcessingStep
import com.koraidv.sdk.ui.ScoreBreakdown
import com.koraidv.sdk.ui.VerificationViewModel

// ─── Loading Screen ──────────────────────────────────────────────────────────

@Composable
fun LoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .then(Modifier),
                tint = KoraColors.Teal.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = KoraColors.Teal,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preparing verification...",
                fontSize = 15.sp,
                color = KoraColors.TextSecondary
            )
        }
    }
}

// ─── Processing Screen ───────────────────────────────────────────────────────

@Composable
fun ProcessingScreen(step: ProcessingStep = ProcessingStep.ANALYZING) {
    // 3 spinning rings at different speeds
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "outer"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "inner"
    )
    val coreRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "core"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KoraColors.DarkBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spinning rings with shield icon
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring
            Canvas(modifier = Modifier.fillMaxSize().rotate(outerRotation)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = KoraColors.Teal,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // Inner ring
            Canvas(
                modifier = Modifier
                    .size(96.dp)
                    .rotate(innerRotation)
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = KoraColors.Cyan,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // Core ring
            Canvas(
                modifier = Modifier
                    .size(72.dp)
                    .rotate(coreRotation)
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = KoraColors.TealBright,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // Shield icon
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = KoraColors.Teal
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Verifying your identity",
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
            color = Color.White,
            letterSpacing = (-0.3).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This will only take a moment",
            fontSize = 15.sp,
            color = KoraColors.WhiteAlpha40
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3-step list
        Column(
            modifier = Modifier.padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProcessingStep.entries.forEach { s ->
                val isDone = s.ordinal < step.ordinal
                val isActive = s == step
                val isPending = s.ordinal > step.ordinal

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> KoraColors.Teal.copy(alpha = 0.2f)
                                    isActive -> Color.Transparent
                                    else -> Color.White.copy(alpha = 0.06f)
                                }
                            )
                            .then(
                                if (isActive) Modifier.border(2.dp, KoraColors.Teal, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = KoraColors.TealBright
                            )
                        }
                    }

                    Text(
                        text = s.label,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.W500 else FontWeight.W400,
                        color = when {
                            isDone -> KoraColors.WhiteAlpha50
                            isActive -> Color.White
                            else -> Color.White.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}

// ─── Success Screen ──────────────────────────────────────────────────────────

@Composable
fun SuccessScreen(
    verification: Verification,
    onDone: () -> Unit
) {
    val scores = VerificationViewModel.computeScoreBreakdown(verification)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Icon + title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconCircle(
                    icon = Icons.Default.Check,
                    bgColor = Color(0xFFDCFCE7).copy(alpha = 0.8f),
                    iconColor = KoraColors.SuccessGreen,
                    size = 56.dp,
                    iconSize = 36.dp,
                    outerRingColor = KoraColors.SuccessGreenBorder
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Verification approved",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.5).sp,
                    color = KoraColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your identity has been verified successfully.",
                    fontSize = 14.sp,
                    color = KoraColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teal score card
            ScoreCard(
                score = scores.overallScore,
                badge = "PASSED",
                gradientBrush = KoraColors.TealGradient135
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metric rows
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Liveness",
                        score = scores.liveness,
                        icon = Icons.Default.Visibility,
                        status = if (scores.liveness >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Name Match",
                        score = scores.nameMatch,
                        icon = Icons.Default.Check,
                        status = if (scores.nameMatch >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Document Quality",
                        score = scores.documentQuality,
                        icon = Icons.Default.CreditCard,
                        status = if (scores.documentQuality >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Selfie Match",
                        score = scores.selfieMatch,
                        icon = Icons.Default.Person,
                        status = if (scores.selfieMatch >= 70) MetricStatus.PASS
                                else if (scores.selfieMatch >= 50) MetricStatus.BORDERLINE
                                else MetricStatus.FAIL
                    )
                )
            }
        }

        // Done button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(text = "Done", onClick = onDone)
        }
    }
}

// ─── Rejected Screen ─────────────────────────────────────────────────────────

@Composable
fun RejectedScreen(
    verification: Verification,
    onRetry: () -> Unit
) {
    val scores = VerificationViewModel.computeScoreBreakdown(verification)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconCircle(
                    icon = Icons.Default.Close,
                    bgColor = KoraColors.ErrorRedLight,
                    iconColor = KoraColors.ErrorRed,
                    size = 56.dp,
                    iconSize = 36.dp,
                    outerRingColor = KoraColors.ErrorRedBorder
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Verification rejected",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.5).sp,
                    color = KoraColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Some checks didn't meet the required threshold.",
                    fontSize = 14.sp,
                    color = KoraColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Red score card
            ScoreCard(
                score = scores.overallScore,
                badge = "REJECTED",
                gradientBrush = KoraColors.RedGradient
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val livenessStatus = if (scores.liveness >= 70) MetricStatus.PASS else MetricStatus.FAIL
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Liveness",
                        score = scores.liveness,
                        icon = if (livenessStatus == MetricStatus.PASS) Icons.Default.Visibility else Icons.Default.Close,
                        status = livenessStatus,
                        errorMessage = if (livenessStatus == MetricStatus.FAIL) "Failed — possible spoof detected" else null
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Name Match",
                        score = scores.nameMatch,
                        icon = Icons.Default.Check,
                        status = if (scores.nameMatch >= 70) MetricStatus.PASS else MetricStatus.FAIL
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Document Quality",
                        score = scores.documentQuality,
                        icon = if (scores.documentQuality >= 70) Icons.Default.Check else Icons.Default.Close,
                        status = if (scores.documentQuality >= 70) MetricStatus.PASS else MetricStatus.FAIL
                    )
                )
                val selfieStatus = if (scores.selfieMatch >= 70) MetricStatus.PASS else MetricStatus.FAIL
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Selfie Match",
                        score = scores.selfieMatch,
                        icon = if (selfieStatus == MetricStatus.PASS) Icons.Default.Person else Icons.Default.Close,
                        status = selfieStatus,
                        errorMessage = if (selfieStatus == MetricStatus.FAIL) "Face does not match ID photo" else null
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(text = "Try again", onClick = onRetry)
        }
    }
}

// ─── Expired Document Screen ─────────────────────────────────────────────────

@Composable
fun ExpiredDocumentScreen(
    verification: Verification,
    onRetry: () -> Unit
) {
    val docType = verification.documentVerification?.documentType ?: "Document"
    val country = verification.documentVerification?.issuingCountry ?: ""
    val expirationDate = verification.documentVerification?.expirationDate ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Icon + title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconCircle(
                    icon = Icons.Default.Info,
                    bgColor = KoraColors.WarningAmberLight,
                    iconColor = KoraColors.WarningAmber,
                    size = 56.dp,
                    iconSize = 36.dp,
                    outerRingColor = KoraColors.WarningAmberBorder
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Document expired",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.5).sp,
                    color = KoraColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The document you submitted has expired and cannot be used for verification.",
                    fontSize = 14.sp,
                    color = KoraColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }

            // Expiration details card
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KoraColors.WarningAmberLight)
                    .border(1.dp, KoraColors.WarningAmberBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = KoraColors.WarningAmberDark
                        )
                        Column {
                            Text(
                                text = docType,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = KoraColors.WarningAmberDark
                            )
                            if (country.isNotEmpty()) {
                                Text(
                                    text = country,
                                    fontSize = 13.sp,
                                    color = KoraColors.WarningAmberMid
                                )
                            }
                        }
                    }

                    if (expirationDate.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = KoraColors.WarningAmberBorder)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "EXPIRED ON",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.W500,
                                    color = KoraColors.WarningAmberMid
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = expirationDate,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.W700,
                                    color = KoraColors.WarningAmberDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guidance tips
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "What you can do:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = KoraColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                GuidanceTip(
                    number = "1",
                    title = "Check your document's expiration date",
                    subtitle = "Look for the expiry date printed on your ID card or license."
                )
                Spacer(modifier = Modifier.height(10.dp))
                GuidanceTip(
                    number = "2",
                    title = "Use a different valid document",
                    subtitle = "Try a passport, state ID, or any other unexpired government-issued ID."
                )
                Spacer(modifier = Modifier.height(10.dp))
                GuidanceTip(
                    number = "3",
                    title = "Renew your document first",
                    subtitle = "Visit your local DMV or government office to renew, then try again."
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(text = "Try with a valid document", onClick = onRetry)
        }
    }
}

@Composable
private fun GuidanceTip(
    number: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KoraColors.Surface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(KoraColors.InfoBlueLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = KoraColors.InfoBlue
            )
        }
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = KoraColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = KoraColors.TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Manual Review Screen ────────────────────────────────────────────────────

@Composable
fun ManualReviewScreen(
    verification: Verification,
    onDone: () -> Unit
) {
    val scores = VerificationViewModel.computeScoreBreakdown(verification)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconCircle(
                    icon = Icons.Default.Schedule,
                    bgColor = KoraColors.InfoBlueLight,
                    iconColor = KoraColors.InfoBlue,
                    size = 56.dp,
                    iconSize = 36.dp,
                    outerRingColor = KoraColors.InfoBlueBorder
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Under review",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.5).sp,
                    color = KoraColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your submission needs manual review. This typically takes 1-2 business days.",
                    fontSize = 14.sp,
                    color = KoraColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Blue score card
            ScoreCard(
                score = scores.overallScore,
                badge = "REVIEW",
                gradientBrush = KoraColors.BlueGradient
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Liveness",
                        score = scores.liveness,
                        icon = Icons.Default.Check,
                        status = if (scores.liveness >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Name Match",
                        score = scores.nameMatch,
                        icon = Icons.Default.Check,
                        status = if (scores.nameMatch >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Document Quality",
                        score = scores.documentQuality,
                        icon = Icons.Default.Check,
                        status = if (scores.documentQuality >= 70) MetricStatus.PASS else MetricStatus.BORDERLINE
                    )
                )
                val selfieStatus = when {
                    scores.selfieMatch >= 70 -> MetricStatus.PASS
                    scores.selfieMatch >= 50 -> MetricStatus.BORDERLINE
                    else -> MetricStatus.FAIL
                }
                ScoreMetricRow(
                    metric = ScoreMetric(
                        label = "Selfie Match",
                        score = scores.selfieMatch,
                        icon = if (selfieStatus == MetricStatus.BORDERLINE) Icons.Default.Info else Icons.Default.Check,
                        status = selfieStatus,
                        errorMessage = if (selfieStatus == MetricStatus.BORDERLINE)
                            "Below auto-approval threshold — requires human review" else null
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(text = "Got it", onClick = onDone)
        }
    }
}

// ─── Error Screen ────────────────────────────────────────────────────────────

@Composable
fun ErrorScreen(
    error: KoraException,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val (errorTitle, errorGuidance) = getErrorDetails(error)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        IconCircle(
            icon = Icons.Default.Error,
            bgColor = KoraColors.ErrorRedLight,
            iconColor = KoraColors.ErrorRed,
            size = 80.dp,
            iconSize = 48.dp,
            outerRingColor = KoraColors.ErrorRedBorder
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = errorTitle,
            fontSize = 22.sp,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center,
            color = KoraColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message,
            fontSize = 15.sp,
            color = KoraColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        if (errorGuidance != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KoraColors.Surface)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = KoraColors.Teal
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorGuidance,
                        fontSize = 13.sp,
                        color = KoraColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (canRetry) {
            KoraButton(text = "Try Again", onClick = onRetry)
            Spacer(modifier = Modifier.height(10.dp))
        }

        KoraButton(
            text = "Cancel",
            onClick = onCancel,
            variant = KoraButtonVariant.WhiteOutline
        )
    }
}

private fun getErrorDetails(error: KoraException): Pair<String, String?> {
    return when (error) {
        is KoraException.QualityValidationFailed -> Pair(
            "Quality Issue",
            "Place the document on a flat, well-lit surface. Avoid shadows and glare. Hold your device steady."
        )
        is KoraException.FaceNotDetected -> Pair(
            "Face Not Detected",
            "Make sure your face is clearly visible. Use good lighting and look directly at the camera."
        )
        is KoraException.LivenessCheckFailed -> Pair(
            "Liveness Check Failed",
            "Follow the on-screen instructions carefully. Make sure your face is well-lit and clearly visible."
        )
        is KoraException.NoInternet -> Pair(
            "No Connection",
            "Check your Wi-Fi or cellular connection and try again."
        )
        is KoraException.Timeout -> Pair(
            "Request Timed Out",
            "The server took too long to respond. Please try again."
        )
        is KoraException.Unauthorized -> Pair(
            "Authentication Error",
            null
        )
        else -> Pair("Something went wrong", null)
    }
}
