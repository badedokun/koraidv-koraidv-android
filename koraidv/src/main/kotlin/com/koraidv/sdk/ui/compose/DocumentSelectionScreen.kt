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
import com.koraidv.sdk.DocumentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSelectionScreen(
    allowedTypes: List<DocumentType>,
    onSelect: (DocumentType) -> Unit,
    onCancel: () -> Unit
) {
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Document Type") },
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

            // Group documents by category
            val groupedTypes = allowedTypes.groupBy { getCategory(it) }

            groupedTypes.forEach { (category, types) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(types) { type ->
                    DocumentTypeItem(
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
private fun DocumentTypeItem(
    type: DocumentType,
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
                imageVector = getDocumentIcon(type),
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
                if (type.requiresBack) {
                    Text(
                        text = "Front and back required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun getCategory(type: DocumentType): String {
    return when (type) {
        DocumentType.US_PASSPORT, DocumentType.US_DRIVERS_LICENSE, DocumentType.US_STATE_ID -> "United States"
        DocumentType.INTERNATIONAL_PASSPORT -> "International"
        DocumentType.UK_PASSPORT -> "United Kingdom"
        DocumentType.EU_ID_GERMANY, DocumentType.EU_ID_FRANCE, DocumentType.EU_ID_SPAIN, DocumentType.EU_ID_ITALY -> "European Union"
        DocumentType.GHANA_CARD, DocumentType.NIGERIA_NIN, DocumentType.KENYA_ID, DocumentType.SOUTH_AFRICA_ID -> "Africa"
    }
}

private fun getDocumentIcon(type: DocumentType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        DocumentType.US_PASSPORT, DocumentType.INTERNATIONAL_PASSPORT, DocumentType.UK_PASSPORT -> Icons.Default.Book
        DocumentType.US_DRIVERS_LICENSE -> Icons.Default.DirectionsCar
        else -> Icons.Default.Badge
    }
}
