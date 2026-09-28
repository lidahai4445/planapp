package com.planapp.study.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.planapp.study.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s0 = vm.state.settings
    var hours by remember(s0) { mutableFloatStateOf(s0.dailyMinutes / 60f) }
    var start by remember(s0) { mutableStateOf(s0.startTime) }
    var lunch by remember(s0) { mutableStateOf(s0.lunch) }
    var dinner by remember(s0) { mutableStateOf(s0.dinner) }
    var rest by remember(s0) { mutableIntStateOf(s0.restWeekday) }
    var base by remember(s0) { mutableStateOf(s0.aiBaseUrl) }
    var key by remember(s0) { mutableStateOf(s0.aiKey) }
    var model by remember(s0) { mutableStateOf(s0.aiModel) }
    var autoDays by remember(s0) { mutableStateOf(s0.aiAutoDays.toString()) }
    var remindOn by remember(s0) { mutableStateOf(s0.remindOn) }
    var morning by remember(s0) { mutableStateOf(s0.morningTime) }
    var evening by remember(s0) { mutableStateOf(s0.eveningTime) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Title("设置")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card2 {
                Text("作息与时长", fontWeight = FontWeight.SemiBold)
                Text("每天学习 ${"%.1f".format(hours)} 小时")
                Slider(hours, { hours = (it * 2).toInt() / 2f }, valueRange = 1f..14f)
                OutlinedTextField(start, { start = it }, label = { Text("开始时间 HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lunch, { lunch = it }, label = { Text("午饭/午休 HH:mm-HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dinner, { dinner = it }, label = { Text("晚饭 HH:mm-HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("每周轻松日（任务量减半）", Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("无", "一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, l ->
                        FilterChip(rest == i, { rest = i }, { Text(l) })
                    }
                }
            }
            Card2 {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("每日提醒", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(remindOn, { remindOn = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(morning, { morning = it }, label = { Text("早上推送计划") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(evening, { evening = it }, label = { Text("晚上提醒打卡") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton({ vm.testNotify() }) { Text("发送一条测试通知") }
            }
            Card2 {
                Text("AI 接口（OpenAI 兼容）", fontWeight = FontWeight.SemiBold)
                Text("支持 DeepSeek / 通义千问 / Kimi / OpenAI 等。仅用于校准考试日期和生成大纲；日常排程在本地完成，不消耗 token。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(autoDays, { autoDays = it.filter(Char::isDigit) }, label = { Text("自动校准间隔（天）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("上次 AI 更新：${s0.lastAiUpdate.ifBlank { "从未" }}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ vm.aiRefreshDates() }, enabled = !vm.busy) { Text("立即用 AI 校准考试日期") }
            }
            Button({
                vm.saveSettings(s0.copy(dailyMinutes = (hours * 60).toInt(), startTime = start.trim(), lunch = lunch.trim(), dinner = dinner.trim(),
                    restWeekday = rest, aiBaseUrl = base.trim(), aiKey = key.trim(), aiModel = model.trim(),
                    aiAutoDays = autoDays.toIntOrNull()?.coerceIn(1, 90) ?: 14,
                    remindOn = remindOn, morningTime = morning.trim(), eveningTime = evening.trim()))
                vm.toast = "已保存并重新排程"
            }, Modifier.fillMaxWidth().height(50.dp)) { Text("保存") }
            var confirmReset by remember { mutableStateOf(false) }
            TextButton({ confirmReset = true }, Modifier.fillMaxWidth()) { Text("重新开始（清空目标和打卡记录）", color = MaterialTheme.colorScheme.error) }
            if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("确定清空？") },
                text = { Text("所有目标、进度和打卡记录都会被删除，AI 和作息设置保留。此操作不可恢复。") },
                confirmButton = { TextButton({ confirmReset = false; vm.resetAll() }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton({ confirmReset = false }) { Text("取消") } })
            if (vm.state.aiLog.isNotEmpty()) Card2 {
                Text("AI 调用记录", fontWeight = FontWeight.SemiBold)
                vm.state.aiLog.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

