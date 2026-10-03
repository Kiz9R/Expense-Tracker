package com.kiz9r.expense_tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Emerald = Color(0xFF65DDB0)
val Rose = Color(0xFFFF9BAA)
val Blue = Color(0xFF9BBDFF)
val Amber = Color(0xFFF0CA7C)
private val Dark = darkColorScheme(
    primary=Emerald, onPrimary=Color(0xFF073C2C), primaryContainer=Color(0xFF194E3E), onPrimaryContainer=Color(0xFFB0F5D9),
    secondary=Color(0xFFA3CBBB), onSecondary=Color(0xFF173B2E), secondaryContainer=Color(0xFF243A33), onSecondaryContainer=Color(0xFFD6EBDD),
    tertiary=Blue, background=Color(0xFF0D1114), onBackground=Color(0xFFF2F3ED),
    surface=Color(0xFF0D1114), onSurface=Color(0xFFF2F3ED), surfaceVariant=Color(0xFF242E32), onSurfaceVariant=Color(0xFFA9B6B9),
    surfaceContainerLowest=Color(0xFF0A0E10), surfaceContainerLow=Color(0xFF141B1F), surfaceContainer=Color(0xFF192226),
    surfaceContainerHigh=Color(0xFF212C30), surfaceContainerHighest=Color(0xFF2A353A),
    outline=Color(0xFF738288), outlineVariant=Color(0xFF303C41), error=Rose, errorContainer=Color(0xFF512D35), onErrorContainer=Color(0xFFFFD9DF)
)
private val Light = lightColorScheme(
    primary=Color(0xFF006B4B), onPrimary=Color.White, primaryContainer=Color(0xFFB7F1D8), onPrimaryContainer=Color(0xFF063D2B),
    secondary=Color(0xFF496356), secondaryContainer=Color(0xFFDCECE1), onSecondaryContainer=Color(0xFF243A2D),
    tertiary=Color(0xFF335DA1), background=Color(0xFFF4F6F3), onBackground=Color(0xFF15231E),
    surface=Color(0xFFF4F6F3), onSurface=Color(0xFF15231E), surfaceVariant=Color(0xFFE1E8E2), onSurfaceVariant=Color(0xFF53615A),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFFDFEFA), surfaceContainer=Color(0xFFEBF0E9),
    surfaceContainerHigh=Color(0xFFE4EBE3), surfaceContainerHighest=Color(0xFFDDE5DD), outlineVariant=Color(0xFFC9D3CB),
    error=Color(0xFFAC354E), errorContainer=Color(0xFFFFD9E0), onErrorContainer=Color(0xFF471020)
)
@Composable fun ExpensetrackerTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, mode: String? = null, content: @Composable () -> Unit) {
    val dark = when(mode) { "light" -> false; "system" -> isSystemInDarkTheme(); "dark" -> true; else -> darkTheme }
    CompositionLocalProvider(LocalFinancialPalette provides if(dark) DarkFinancialPalette else LightFinancialPalette) {
        MaterialTheme(colorScheme=if(dark) Dark else Light, typography=Typography,
            shapes=Shapes(small=RoundedCornerShape(16.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(24.dp)), content=content)
    }
}
