package com.planapp.study.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.planapp.study.AppViewModel
import com.planapp.study.Goal
import com.planapp.study.Subject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private sealed interface Dlg {
    data class Date(val g: Goal) : Dlg
    data class AddUnit(val g: Goal, val s: Subject) : Dlg
    data class Ai(val g: Goal, val s: Subject) : Dlg
    data class AddSubject(val g: Goal) : Dlg
    data object AddGoal : Dlg
}

@Composable
fun ProgressScreen(vm: AppViewModel) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Title("学习进度", "随时勾选已完成内容（含以前学过的），计划自动重排") }
        vm.state.goals.forEach { g ->
            item(key = g.id) {
                Card2(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("考试 ${g.examDate} · 还有 ${ChronoUnit.DAYS.between(today, g.date)} 天",
                                Modifier.clickable { dlg = Dlg.Date(g) }, color = MaterialTheme.colorScheme.primary)
                            if (g.dateNote.isNotBlank()) Text(g.dateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(g.enabled, { vm.toggleGoal(g.id) })
                    }
                    g.subjects.forEach { sub ->
                        val open = sub.id in expanded
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (open) expanded - sub.id else expanded + sub.id },
                            verticalAlignment = Alignment.CenterVertically) {
                            Dot(sub.color, 12)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(sub.name, fontWeight = FontWeight.SemiBold)
                                LinearProgressIndicator(progress = { sub.progress }, Modifier.fillMaxWidth().padding(top = 4.dp),
                                    color = Color(sub.color))
                            }
                            Text("${sub.units.count { it.done }}/${sub.units.size}", style = MaterialTheme.typography.labelMedium)
                            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                        }
                        if (open) {
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("优先级 ${"%.1f".format(sub.weight)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(76.dp))
                                Slider(sub.weight.toFloat(), { vm.setWeight(g.id, sub.id, (it * 10).toInt() / 10.0) },
                                    valueRange = 0.3f..2f, modifier = Modifier.weight(1f))
                            }
                            sub.units.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(u.done, { vm.setUnitDone(g.id, sub.id, u.id, it) })
                                    Column(Modifier.weight(1f)) {
                                        Text(u.title, textDecoration = if (u.done) TextDecoration.LineThrough else null,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(fmtMin(u.minutes) + (u.doneDate?.let { " · 完成于 $it" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!u.done) TextButton({ vm.markAllBefore(g.id, sub.id, u.id) }) { Text("到此都已完成") }
                                    IconButton({ vm.deleteUnit(g.id, sub.id, u.id) }) { Icon(Icons.Filled.Close, "删除", Modifier.size(18.dp)) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ dlg = Dlg.AddUnit(g, sub) }) { Icon(Icons.Filled.Add, null); Text("添加单元") }
                                OutlinedButton({ dlg = Dlg.Ai(g, sub) }) { Icon(Icons.Filled.Star, null); Text("AI 生成") }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp)) {
                        TextButton({ dlg = Dlg.AddSubject(g) }) { Text("+ 科目") }
                        Spacer(Modifier.weight(1f))
                        TextButton({ vm.deleteGoal(g.id) }) { Text("删除目标", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.wizardOpen = true }, Modifier.fillMaxWidth().height(50.dp)) { Text("＋ 新建学习目标（AI 按进度生成）") }
            }
        }
    }

    when (val d = dlg) {
        null -> {}
        is Dlg.Date -> InputDialog("修改考试日期", listOf("日期 (yyyy-MM-dd)" to d.g.examDate), { dlg = null }) { v ->
            if (runCatching { LocalDate.parse(v[0]) }.isSuccess) { vm.setExamDate(d.g.id, v[0]); true } else false }
        is Dlg.AddUnit -> InputDialog("添加到 ${d.s.name}", listOf("内容" to "", "预计分钟" to "60"), { dlg = null }) { v ->
            val m = v[1].toIntOrNull(); if (v[0].isNotBlank() && m != null && m in 10..300) { vm.addUnit(d.g.id, d.s.id, v[0], m); true } else false }
        is Dlg.AddSubject -> InputDialog("添加科目", listOf("科目名" to ""), { dlg = null }) { v ->
            if (v[0].isNotBlank()) { vm.addSubject(d.g.id, v[0]); true } else false }
        Dlg.AddGoal -> InputDialog("自定义目标", listOf("名称（如 教资、驾照）" to "", "考试日期 yyyy-MM-dd" to LocalDate.now().plusMonths(3).toString()), { dlg = null }) { v ->
            if (v[0].isNotBlank() && runCatching { LocalDate.parse(v[1]) }.isSuccess) { vm.addGoal(v[0], v[1]); true } else false }
        is Dlg.Ai -> {
            var replace by remember { mutableStateOf(true) }
            InputDialog("AI 生成「${d.s.name}」剩余单元", listOf("补充说明（如 数学二、408、用张宇18讲）" to ""), { dlg = null },
                extra = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replace, { replace = it }); Text("替换未完成单元") } }) { v ->
                vm.aiGenerateUnits(d.g.id, d.s.id, v[0], replace); true }
        }
    }
}

@Composable
fun InputDialog(title: String, fields: List<Pair<String, String>>, onDismiss: () -> Unit,
                extra: @Composable () -> Unit = {}, onOk: (List<String>) -> Boolean) {
    val values = remember { mutableStateListOf(*fields.map { it.second }.toTypedArray()) }
    var err by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, (label, _) ->
                    OutlinedTextField(values[i], { values[i] = it; err = false }, label = { Text(label) }, singleLine = true, isError = err)
                }
                extra()
                if (err) Text("输入格式不正确", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton({ if (onOk(values.toList())) onDismiss() else err = true }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

