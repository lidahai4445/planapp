package com.planapp.study.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planapp.study.AppViewModel
import com.planapp.study.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlanScreen(vm: AppViewModel) {
    val days = remember(vm.state) { vm.upcoming(14) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Title("未来两周", "根据剩余任务量与考试日期自动均衡分配") }
        item {
            Card2(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("进度评估", fontWeight = FontWeight.SemiBold)
                vm.state.goals.filter { it.enabled }.forEach { g ->
                    Text("• ${g.name}：${Scheduler.feasibility(g, vm.state.settings, today)}",
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { AiAdjustCard(vm) }
        items(days, key = { it.date }) { d ->
            val date = LocalDate.parse(d.date)
            Card2(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (date == today) "今天" else date.format(DateTimeFormatter.ofPattern("M/d EEE", Locale.CHINA)),
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(fmtMin(d.items.sumOf { it.minutes }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (d.items.isEmpty()) Text("休息 / 无任务", style = MaterialTheme.typography.bodySmall)
                d.items.forEach { i ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (i.minutes > 0) "${i.start}-${i.end}" else i.start, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                        Dot(i.color)
                        Text(i.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAdjustCard(vm: AppViewModel) {
    var req by rememberSaveable { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    Card2(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("✨ 让 AI 调整计划", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(req, { req = it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2,
            placeholder = { Text("例：线代我已经学完了；这周多安排英语作文；每天改成 5 小时；加一个每天 20 分钟的错题回顾") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(reply, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Button({ vm.aiAdjust(req.trim()) { reply = it; req = "" } }, enabled = req.isNotBlank() && !vm.busy) { Text(if (vm.busy) "调整中…" else "调整") }
        }
    }
}

