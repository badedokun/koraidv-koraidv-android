package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koraidv.sdk.api.CountryInfo
import com.koraidv.sdk.api.DocumentTypeInfo

@Composable
fun DocumentSelectionScreen(
    documentTypes: List<DocumentTypeInfo>,
    selectedCountry: CountryInfo?,
    onSelect: (DocumentTypeInfo) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Progress bar (step 2/5)
        StepProgressBar(total = 5, current = 2)

        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LightBackButton(onClick = onCancel)
            Text(
                text = "Choose your document",
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = (-0.3).sp,
                color = KoraColors.TextPrimary
            )
        }

        // Country indicator
        if (selectedCountry != null) {
            Text(
                text = "${selectedCountry.flagEmoji ?: ""} ${selectedCountry.name}",
                fontSize = 14.sp,
                color = KoraColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        // Document list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(documentTypes) { docType ->
                DocumentCard(
                    docType = docType,
                    onClick = { onSelect(docType) }
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    docType: DocumentTypeInfo,
    onClick: () -> Unit
) {
    val (iconBg, iconTint) = getDocumentStyle(docType.code)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KoraColors.Surface)
            .border(2.dp, Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getDocumentIcon(docType.code),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
        }

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = docType.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = KoraColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (docType.requiresBack) "Front & back required" else "Photo page only",
                fontSize = 13.sp,
                color = KoraColors.TextSecondary
            )
        }

        // Arrow chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFFCCCCCC)
        )
    }
}

private fun getDocumentIcon(code: String): ImageVector {
    return when {
        code.contains("passport", ignoreCase = true) -> Icons.Default.MenuBook
        code.contains("driver", ignoreCase = true) -> Icons.Default.CreditCard
        code.contains("green_card", ignoreCase = true) || code.contains("resident", ignoreCase = true) -> Icons.Default.Badge
        code.contains("state_id", ignoreCase = true) || code.contains("national", ignoreCase = true) -> Icons.Default.CreditCard
        else -> Icons.Default.CreditCard
    }
}

private fun getDocumentStyle(code: String): Pair<Color, Color> {
    return when {
        code.contains("passport", ignoreCase = true) -> Pair(KoraColors.WarningAmberLight, KoraColors.WarningAmber)
        code.contains("green_card", ignoreCase = true) || code.contains("resident", ignoreCase = true) -> Pair(KoraColors.IndigoLight, KoraColors.Indigo)
        else -> Pair(KoraColors.InfoBlueLight, KoraColors.InfoBlue)
    }
}
