package com.planapp.study.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Dot(c: Long, size: Int = 10) = Box(Modifier.size(size.dp).clip(CircleShape).background(Color(c)))

@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Chip(text: String, c: Long) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(c).copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = Color(c), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h${if (m % 60 > 0) "${m % 60}m" else ""}" else "${m}m"

