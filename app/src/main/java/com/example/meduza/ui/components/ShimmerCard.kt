package com.example.meduza.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        Color(0xFF2A2A2A),
        Color(0xFF3A3A3A),
        Color(0xFF2A2A2A),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue   = 0f,
        targetValue    = 1000f,
        animationSpec  = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label          = "shimmerX",
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 300f, 0f),
        end    = Offset(translateAnim, 0f),
    )
}

@Composable
fun ShimmerTrackCard(width: Dp, height: Dp) {
    val brush = shimmerBrush()
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(brush)
    )
}

@Composable
fun ShimmerSongRow() {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
    }
}

/** Shimmer for a section header row (title + optional label line) */
@Composable
fun ShimmerSectionHeader() {
    val brush = shimmerBrush()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(180.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

/** Shimmer row of chips */
@Composable
fun ShimmerChipRow() {
    val brush = shimmerBrush()
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled     = false,
    ) {
        items(5) {
            Box(
                Modifier
                    .width(72.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(brush)
            )
        }
    }
}

/** A full shimmer section: header + horizontal card row */
@Composable
fun ShimmerSection(cardSize: Dp = 180.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShimmerSectionHeader()
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled     = false,
        ) {
            items(4) { ShimmerTrackCard(width = cardSize, height = cardSize) }
        }
    }
}

