package com.planapp.study.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planapp.study.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel) {
    val s = vm.state
    val items = s.today?.items.orEmpty()
    val total = items.sumOf { it.minutes }
    val done = items.filter { it.done }.sumOf { it.minutes }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Title(today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                        if (items.isEmpty()) "今天没有安排，好好休息" else "今日 ${fmtMin(total)} · 已完成 ${fmtMin(done)}")
                }
                IconButton(onClick = { vm.replan() }, Modifier.padding(end = 12.dp)) { Icon(Icons.Filled.Refresh, "重排") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card2(Modifier.weight(1f)) {
                    Text("今日完成度", style = MaterialTheme.typography.labelMedium)
                    val p = if (total == 0) 0f else done.toFloat() / total
                    Text("${(p * 100).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                Card2(Modifier.weight(1f)) {
                    Text("连续打卡", style = MaterialTheme.typography.labelMedium)
                    Text("${vm.streak()} 天", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(if (done >= total && total > 0) "今天全部完成 🎉" else "完成任意任务即算打卡",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.goals.filter { it.enabled }.forEach { g ->
                    val d = ChronoUnit.DAYS.between(today, g.date)
                    AssistChip(onClick = {}, label = { Text("${g.name} ${if (d >= 0) "还有 $d 天" else "已结束"}") })
                }
            }
        }
        items(items, key = { it.id }) { it2 ->
            Card2(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateContentSize()) {
                Row(Modifier.fillMaxWidth().clickable(enabled = it2.minutes > 0) { vm.toggle(it2) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(56.dp)) {
                        Text(it2.start, fontWeight = FontWeight.SemiBold)
                        if (it2.minutes > 0) Text(it2.end, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(it2.subjectName, it2.color)
                            if (it2.minutes > 0) Text(fmtMin(it2.minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(it2.title, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Medium,
                            textDecoration = if (it2.done) TextDecoration.LineThrough else null,
                            color = if (it2.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    }
                    if (it2.minutes > 0) Checkbox(it2.done, { _ -> vm.toggle(it2) })
                }
            }
        }
        item {
            Text("没做完的任务不用担心：明天会根据进度自动重新分配。",
                Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

