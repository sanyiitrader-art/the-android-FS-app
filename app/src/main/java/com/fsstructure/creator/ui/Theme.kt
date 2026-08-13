package com.fsstructure.creator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Exact Charcoal and Mint visual identity based on the Windows reference image
private val Mint = Color(0xFF00E5A0)
private val MintDark = Color(0xFF00B585)
private val CharcoalBg = Color(0xFF1E1E1E)
private val CharcoalSurface = Color(0xFF2B2B2B)
private val CharcoalSurfaceVariant = Color(0xFF333333)
private val LightText = Color(0xFFEAEAEA)
private val DimText = Color(0xFFA0A0A0)

private val AppColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color.Black,
    primaryContainer = MintDark,
    onPrimaryContainer = Color.White,
    secondary = MintDark,
    onSecondary = Color.White,
    background = CharcoalBg,
    onBackground = LightText,
    surface = CharcoalSurface,
    onSurface = LightText,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = DimText,
    outline = CharcoalSurfaceVariant
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun FSAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}