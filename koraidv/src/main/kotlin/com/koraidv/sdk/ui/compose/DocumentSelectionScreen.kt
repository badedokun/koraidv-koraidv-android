package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koraidv.sdk.api.CountryInfo
import com.koraidv.sdk.api.DocumentTypeInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSelectionScreen(
    documentTypes: List<DocumentTypeInfo>,
    selectedCountry: CountryInfo?,
    onSelect: (DocumentTypeInfo) -> Unit,
    onCancel: () -> Unit
) {
    var selectedType by remember { mutableStateOf<DocumentTypeInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Select Document Type")
                        if (selectedCountry != null) {
                            Text(
                                text = selectedCountry.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            Surface {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Button(
                        onClick = { selectedType?.let { onSelect(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedType != null
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Choose the type of ID you'll use for verification",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // Group documents by category if available
            val grouped = documentTypes.groupBy { it.category ?: "Documents" }

            grouped.forEach { (category, types) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(types) { type ->
                    DocumentTypeInfoItem(
                        type = type,
                        isSelected = selectedType == type,
                        onClick = { selectedType = type }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DocumentTypeInfoItem(
    type: DocumentTypeInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getDocumentIcon(type.code),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (type.description != null) {
                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (type.requiresBack) {
                    Text(
                        text = "Front and back required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

private fun getDocumentIcon(code: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        code.contains("passport", ignoreCase = true) -> Icons.Default.Book
        code.contains("driver", ignoreCase = true) -> Icons.Default.DirectionsCar
        code.contains("green_card", ignoreCase = true) -> Icons.Default.CardMembership
        else -> Icons.Default.Badge
    }
}
