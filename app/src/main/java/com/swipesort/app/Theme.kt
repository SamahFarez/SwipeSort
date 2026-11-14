package com.swipesort.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pastel blue × pink palette. */
object Palette {
    val Blue = Color(0xFF8CCBFF)
    val BlueDeep = Color(0xFF5AAEF2)
    val BlueSoft = Color(0xFFDCEFFF)
    val Pink = Color(0xFFFF9CCB)
    val PinkDeep = Color(0xFFEC6FA9)
    val PinkSoft = Color(0xFFFFE3F0)
    val Lilac = Color(0xFFEDE4FB)
    val Ink = Color(0xFF3A3452)
    val InkSoft = Color(0xFF8C86A6)
    val Rose = Color(0xFFFF7A93)
    val Glass = Color(0xC7FFFFFF)
    val MediaBg = Color(0xFF221E33)
}

val BrandGradient: Brush = Brush.linearGradient(listOf(Palette.Blue, Palette.Pink))
val BrandGradientDeep: Brush = Brush.linearGradient(listOf(Palette.BlueDeep, Palette.PinkDeep))

val DisplayFont = FontFamily.Serif

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Palette.PinkDeep,
        onPrimary = Color.White,
        primaryContainer = Palette.PinkSoft,
        onPrimaryContainer = Palette.Ink,
        secondary = Palette.BlueDeep,
        onSecondary = Color.White,
        secondaryContainer = Palette.BlueSoft,
        onSecondaryContainer = Palette.Ink,
        background = Palette.Lilac,
        onBackground = Palette.Ink,
        surface = Color.White,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.Lilac,
        onSurfaceVariant = Palette.InkSoft,
        surfaceContainerHigh = Color.White,
        outline = Palette.InkSoft,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Full-screen dreamy gradient with two soft colour blobs. */
@Composable
fun GradientBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFD3EBFF), Color(0xFFF1E8FC), Color(0xFFFFDCEB)),
                    start = Offset.Zero, end = Offset.Infinite
                )
            )
    ) {
        Box(
            Modifier.size(360.dp).offset(x = (-120).dp, y = (-80).dp)
                .background(Brush.radialGradient(listOf(Palette.Blue.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(380.dp).align(Alignment.BottomEnd).offset(x = 130.dp, y = 110.dp)
                .background(Brush.radialGradient(listOf(Palette.Pink.copy(alpha = 0.55f), Color.Transparent)), CircleShape)
        )
        content()
    }
}

/** Frosted white card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = Palette.Pink.copy(alpha = 0.4f), spotColor = Palette.BlueDeep.copy(alpha = 0.25f))
            .clip(shape)
            .background(Palette.Glass)
            .border(1.dp, Color.White, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content
    )
}

/** Pill button filled with the blue→pink gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    brush: Brush = BrandGradientDeep,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .shadow(if (enabled) 12.dp else 0.dp, shape, spotColor = Palette.PinkDeep, ambientColor = Palette.Blue)
            .clip(shape)
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color(0xFFD9D5E4), Color(0xFFE6E1EE))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            letterSpacing = 0.5.sp, maxLines = 1
        )
    }
}

/** Round frosted icon button. */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .size(size)
            .shadow(8.dp, CircleShape, ambientColor = Palette.Pink, spotColor = Palette.BlueDeep.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(if (enabled) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.45f))
            .border(1.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun SmallCapsLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.InkSoft) {
    Text(
        text.uppercase(), modifier,
        style = TextStyle(fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, color = color)
    )
}
