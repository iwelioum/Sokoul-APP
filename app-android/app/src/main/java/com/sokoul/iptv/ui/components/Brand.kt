package com.sokoul.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.iptv.ui.theme.Radius
import com.sokoul.iptv.ui.theme.SokoulColors

/** Monogramme « S » de Sokoul : un seul dessin pour toute l'app (onglets, rail tablette, barre téléphone). */
@Composable
fun SokoulMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).background(Brush.linearGradient(listOf(SokoulColors.Accent, SokoulColors.AccentEnd)), Radius.Card),
        contentAlignment = Alignment.Center,
    ) { Text("S", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.56f).sp) }
}
