package com.planapp.study.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planapp.study.AppViewModel
import com.planapp.study.Goal
import com.planapp.study.StudyUnit
import com.planapp.study.Subject
import com.planapp.study.Templates
import java.time.LocalDate

/**
 * 通用目标向导：
 * 第 1 步 目标 + 日期 + 进度自述 → AI 生成（或模板 / 手动）
 * 第 2 步 进度调查表：逐项勾选已完成内容，确认后加入计划
 */
@Composable
fun GoalWizard(vm: AppViewModel, onClose: (() -> Unit)?) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusMonths(3).toString()) }
    var dateNote by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var subjects by remember { mutableStateOf<List<Subject>>(emptyList()) }
    val aiReady = vm.state.settings.aiKey.isNotBlank()

    fun pickPreset(p: String) {
        name = if (p == "自定义") "" else p
        Templates.byName(p)?.let { date = it.examDate; dateNote = it.dateNote }
    }

    BackHandler(enabled = step == 2 || onClose != null) { if (step == 2) step = 1 else onClose?.invoke() }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (step == 2) IconButton({ step = 1 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            else if (onClose != null) IconButton(onClose) { Icon(Icons.Filled.Close, "关闭") }
            Text(if (step == 1) "新建学习目标" else "进度调查表", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp))
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (step == 1) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card2 {
                    Text("1. 你要准备什么？", fontWeight = FontWeight.SemiBold)
                    FlowRowChips(Templates.presets, name) { pickPreset(it) }
                    OutlinedTextField(name, { name = it }, label = { Text("目标名称（任意：考研/考公/雅思/驾照/编程…）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Card2 {
                    Text("2. 考试 / 截止日期", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(date, { date = it; dateNote = "" }, label = { Text("yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton({ vm.aiSuggestDate(name) { d, n -> date = d; dateNote = n } }, enabled = aiReady && name.isNotBlank() && !vm.busy) { Text("AI 查询") }
                    }
                    if (dateNote.isNotBlank()) Text(dateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Card2 {
                    Text("3. 你现在学到哪了？", fontWeight = FontWeight.SemiBold)
                    Text("越具体越好，AI 会据此跳过已掌握的内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(progress, { progress = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：数学一，高数基础已过完一轮，线代刚开始；英语单词背完一轮，真题没做；政治还没开始") })
                    OutlinedTextField(extra, { extra = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("补充（选填）：目标院校/分数、用的教材（如张宇18讲、1000题）、薄弱项、偏好…") })
                }
                val valid = name.isNotBlank() && runCatching { LocalDate.parse(date) }.getOrNull()?.isAfter(LocalDate.now()) == true
                Button({ vm.aiBuildGoal(name.trim(), date, progress, extra) { subjects = it; step = 2 } },
                    enabled = valid && aiReady && !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (vm.busy) "AI 正在规划…" else "✨ AI 生成我的计划")
                }
                if (!aiReady) Text("未配置 AI Key（设置页 / 首次引导中填写）。也可以用下面的方式：",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tpl = Templates.byName(name.trim())
                    OutlinedButton({ subjects = tpl!!.subjects; step = 2 }, enabled = valid && tpl != null, modifier = Modifier.weight(1f)) { Text("用内置模板") }
                    OutlinedButton({ subjects = listOf(Subject(name = name.trim(), color = 0xFF4F6BED)); step = 2 }, enabled = valid, modifier = Modifier.weight(1f)) { Text("手动创建") }
                }
            }
        } else {
            ProgressSurvey(subjects, onChange = { subjects = it }, modifier = Modifier.weight(1f))
            Button({
                vm.addGoalFull(Goal(name = name.trim(), examDate = date, dateNote = dateNote, subjects = subjects))
                onClose?.invoke()
            }, Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text("确认，开始规划") }
        }
    }
}

@Composable
private fun FlowRowChips(items: List<String>, selected: String, onPick: (String) -> Unit) {
    // 简易换行：每行 4 个
    items.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { FilterChip(selected == it || (it == "自定义" && selected.isNotBlank() && selected !in items), { onPick(it) }, { Text(it) }) }
        }
    }
}

/** 进度调查表：勾选已完成；"到此都已完成"快速标记；可删除/新增单元。 */
@Composable
fun ProgressSurvey(subjects: List<Subject>, onChange: (List<Subject>) -> Unit, modifier: Modifier = Modifier) {
    var adding by remember { mutableStateOf<Int?>(null) }
    fun edit(si: Int, f: (Subject) -> Subject) = onChange(subjects.mapIndexed { i, s -> if (i == si) f(s) else s })
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 8.dp)) {
        item {
            Text("请勾选你已经完成/掌握的内容，未勾选的将被安排进后续计划。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        subjects.forEachIndexed { si, sub ->
            item(key = "h$si") {
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(sub.color, 12)
                    Text("  ${sub.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("${sub.units.count { it.done }}/${sub.units.size}", style = MaterialTheme.typography.labelMedium)
                }
                if (sub.dailyHabit != null) Text("每日习惯：${sub.dailyHabit} ${sub.habitMinutes} 分钟", style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(sub.units, key = { _, u -> u.id }) { ui, u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(u.done, { c -> edit(si) { s -> s.copy(units = s.units.map { if (it.id == u.id) it.copy(done = c, doneDate = if (c) "早期" else null) else it }) } })
                    Column(Modifier.weight(1f)) {
                        Text(u.title, textDecoration = if (u.done) TextDecoration.LineThrough else null,
                            color = if (u.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                        Text(fmtMin(u.minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!u.done) TextButton({ edit(si) { s -> s.copy(units = s.units.mapIndexed { i, x -> if (i <= ui) x.copy(done = true, doneDate = "早期") else x }) } }) { Text("到此都完成") }
                    IconButton({ edit(si) { s -> s.copy(units = s.units.filter { it.id != u.id }) } }) { Icon(Icons.Filled.Close, "删除", Modifier.size(18.dp)) }
                }
            }
            item(key = "a$si") { TextButton({ adding = si }) { Text("+ 给「${sub.name}」添加单元") } }
        }
    }
    adding?.let { si ->
        InputDialog("添加单元", listOf("内容" to "", "预计分钟" to "60"), { adding = null }) { v ->
            val m = v[1].toIntOrNull()
            if (v[0].isNotBlank() && m != null && m in 10..300) { edit(si) { it.copy(units = it.units + StudyUnit(title = v[0], minutes = m)) }; true } else false
        }
    }
}

