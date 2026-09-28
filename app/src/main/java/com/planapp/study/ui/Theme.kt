package com.planapp.study.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF4F6BED)

@Composable
fun PlanTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF9DB0FF), secondary = Color(0xFF6EE7B7),
        background = Color(0xFF0F1115), surface = Color(0xFF171A21), surfaceVariant = Color(0xFF222632),
    ) else lightColorScheme(
        primary = Brand, secondary = Color(0xFF22A06B),
        background = Color(0xFFF5F6FA), surface = Color.White, surfaceVariant = Color(0xFFEEF0F6),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

