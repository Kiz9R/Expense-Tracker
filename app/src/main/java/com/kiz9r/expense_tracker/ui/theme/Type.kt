package com.kiz9r.expense_tracker.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val figure = TextStyle(fontFamily=FontFamily.Default,fontWeight=FontWeight.SemiBold,fontFeatureSettings="tnum",letterSpacing=(-1).sp)
val Typography = Typography(
    displaySmall=figure.copy(fontSize=40.sp,lineHeight=48.sp),
    headlineLarge=figure.copy(fontSize=34.sp,lineHeight=42.sp),
    headlineMedium=figure.copy(fontSize=28.sp,lineHeight=36.sp),
    headlineSmall=figure.copy(fontSize=24.sp,lineHeight=32.sp),
    titleLarge=TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    labelMedium=TextStyle(fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium)
)
