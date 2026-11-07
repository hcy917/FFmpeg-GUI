package com.ffmpeg.gui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FfmpegGreen = Color(0xFF086B3A)
val FfmpegGreenLight = Color(0xFF8CF8B5)
val FfmpegMint = Color(0xFFBDF3D1)
val FfmpegInk = Color(0xFF0D1F15)
val FfmpegAmber = Color(0xFFFFC85C)
val FfmpegCoral = Color(0xFFFF8F7A)
val FfmpegSky = Color(0xFF9FD8FF)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF6BA),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4D6353),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D5),
    onSecondaryContainer = Color(0xFF0A1F14),
    tertiary = Color(0xFF3A6373),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBEEAFE),
    onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceTint = Color(0xFF006D3B),
    outline = Color(0xFF727970),
    outlineVariant = Color(0xFFC1C9BF),
    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF2EA),
    inversePrimary = Color(0xFF80DAA0),
    surfaceDim = Color(0xFFD6DBD4),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE4EAE1),
    surfaceContainerHighest = Color(0xFFDEE4DB),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF80DAA0),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF9CF6BA),
    secondary = Color(0xFFB3CCBA),
    onSecondary = Color(0xFF1F3527),
    secondaryContainer = Color(0xFF354B3C),
    onSecondaryContainer = Color(0xFFCFE9D5),
    tertiary = Color(0xFFA2CDDF),
    onTertiary = Color(0xFF033543),
    tertiaryContainer = Color(0xFF204C5B),
    onTertiaryContainer = Color(0xFFBEEAFE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1510),
    onBackground = Color(0xFFE1E4DD),
    surface = Color(0xFF0E1510),
    onSurface = Color(0xFFE1E4DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF80DAA0),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    inverseSurface = Color(0xFFE1E4DD),
    inverseOnSurface = Color(0xFF2D322D),
    inversePrimary = Color(0xFF006D3B),
    surfaceDim = Color(0xFF0E1510),
    surfaceBright = Color(0xFF343B34),
    surfaceContainerLowest = Color(0xFF090F0B),
    surfaceContainerLow = Color(0xFF161D17),
    surfaceContainer = Color(0xFF1A211B),
    surfaceContainerHigh = Color(0xFF252C25),
    surfaceContainerHighest = Color(0xFF303730),
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 62.sp,
        letterSpacing = (-2.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FFmpegGuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
