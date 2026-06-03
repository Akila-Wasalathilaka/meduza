package com.example.meduza.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meduza.data.model.HomeChip
import com.example.meduza.ui.theme.*

/**
 * Horizontal scrollable chip row for home page category filters.
 * Mirrors ArchiveTune's ChipsRow with Meduza's immersive neon-dark aesthetic.
 *
 * Active chip: neon pink glow background + white text.
 * Inactive chip: subtle dark card border + muted text.
 */
@Composable
fun HomeChipRow(
    chips: List<HomeChip>,
    selectedChip: HomeChip?,
    onChipSelected: (HomeChip) -> Unit,
) {
    if (chips.isEmpty()) return

    LazyRow(
        contentPadding      = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        itemsIndexed(chips) { _, chip ->
            val isSelected  = chip == selectedChip
            val bgColor by animateColorAsState(
                targetValue   = if (isSelected) MeduzaPink else MeduzaCard,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "chipBg",
            )
            val textColor by animateColorAsState(
                targetValue   = if (isSelected) Color.White else MeduzaTextSecondary,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "chipText",
            )
            val borderColor by animateColorAsState(
                targetValue   = if (isSelected) MeduzaPink else MeduzaBorder,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "chipBorder",
            )
            val glowAlpha by animateColorAsState(
                targetValue   = if (isSelected) MeduzaPink.copy(alpha = 0.25f) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label         = "chipGlow",
            )

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .drawBehind {
                        // Soft neon glow halo behind selected chip
                        if (isSelected) {
                            drawCircle(
                                color = glowAlpha,
                                radius = size.maxDimension * 0.6f,
                            )
                        }
                    }
                    .background(bgColor, RoundedCornerShape(17.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(17.dp))
                    .clickable { onChipSelected(chip) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = chip.title,
                    color      = textColor,
                    fontSize   = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines   = 1,
                )
            }
        }
    }
}
