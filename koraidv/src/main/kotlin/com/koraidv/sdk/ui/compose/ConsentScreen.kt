package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Progress bar (step 1/5)
        StepProgressBar(total = 5, current = 1)

        // Close button header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            LightCloseButton(onClick = onDecline)
        }

        // Scrollable body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Teal gradient icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(KoraColors.Teal, KoraColors.Cyan),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(
                                Float.POSITIVE_INFINITY,
                                Float.POSITIVE_INFINITY
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Verify your identity",
                fontSize = 26.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.5).sp,
                color = KoraColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "We need to verify your identity to comply with regulations and keep your account secure.",
                fontSize = 15.sp,
                color = KoraColors.TextTertiary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Consent items
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConsentItem(
                    icon = Icons.Default.CreditCard,
                    iconBg = KoraColors.InfoBlueLight,
                    iconTint = KoraColors.InfoBlue,
                    title = "Government-issued ID",
                    subtitle = "Photo of your passport or front & back of your ID"
                )
                ConsentItem(
                    icon = Icons.Default.Person,
                    iconBg = KoraColors.SuccessGreenLight,
                    iconTint = KoraColors.SuccessGreen,
                    title = "Selfie photo",
                    subtitle = "A quick selfie to match your ID"
                )
                ConsentItem(
                    icon = Icons.Default.Visibility,
                    iconBg = KoraColors.PurpleLight,
                    iconTint = KoraColors.Purple,
                    title = "Liveness check",
                    subtitle = "Quick video to confirm it's really you"
                )
            }
        }

        // Bottom action area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(
                text = "Get started",
                onClick = onAccept,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "By continuing, you agree to our Privacy Policy and consent to biometric processing.",
                fontSize = 12.sp,
                color = KoraColors.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConsentItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KoraColors.Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }

        Column {
            Text(
                text = title,
                fontSize = 15.sp,
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
