package com.koraidv.sdk.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.koraidv.sdk.R
import com.koraidv.sdk.api.CountryInfo

@Composable
fun CountrySelectionScreen(
    countries: List<CountryInfo>,
    onSelect: (CountryInfo) -> Unit,
    onCancel: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }

    val filteredCountries = remember(searchQuery, countries) {
        if (searchQuery.isBlank()) {
            countries
        } else {
            countries.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                text = stringResource(R.string.koraidv_country_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = (-0.3).sp,
                color = KoraColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }

        // Subtitle
        Text(
            text = stringResource(R.string.koraidv_country_subtitle),
            fontSize = 14.sp,
            color = KoraColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // Selected country indicator
        val selected = selectedCountry
        if (selected != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .border(2.dp, KoraColors.Teal, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(KoraColors.SelectedBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selected.flagEmoji ?: countryCodeToFlag(selected.code),
                        fontSize = 28.sp,
                        lineHeight = 28.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selected.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = KoraColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = KoraColors.Teal
                    )
                }
            }
        }

        // Search bar
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
        ) {
            val searchLabel = stringResource(R.string.koraidv_country_search)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = searchLabel },
                placeholder = {
                    Text(
                        searchLabel,
                        color = Color(0xFFAAAAAA),
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = KoraColors.SurfaceLight,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = KoraColors.BorderLight,
                    focusedBorderColor = KoraColors.Teal
                )
            )
        }

        // Country list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filteredCountries) { country ->
                val isSelected = selectedCountry == country
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(KoraColors.SelectedBg)
                                    .selectedLeftBorder()
                            } else Modifier
                        )
                        .clickable { selectedCountry = country }
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                        .semantics {
                            contentDescription = country.name
                            if (isSelected) stateDescription = "Selected"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = country.flagEmoji ?: countryCodeToFlag(country.code),
                        fontSize = 24.sp,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = country.name,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W500,
                        color = if (isSelected) KoraColors.Teal else KoraColors.TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = KoraColors.Teal
                        )
                    }
                }
            }
        }

        // Continue button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            KoraButton(
                text = stringResource(R.string.koraidv_continue),
                onClick = { selectedCountry?.let { onSelect(it) } },
                enabled = selectedCountry != null
            )
        }
    }
}

private fun Modifier.selectedLeftBorder(): Modifier {
    return this.drawBehind {
        drawRect(
            color = Color(0xFF0D9488),
            topLeft = Offset.Zero,
            size = Size(3.dp.toPx(), size.height)
        )
    }
}

private fun countryCodeToFlag(countryCode: String): String {
    if (countryCode.length != 2) return countryCode
    val first = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
