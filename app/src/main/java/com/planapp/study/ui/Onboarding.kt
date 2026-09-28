package com.planapp.study.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planapp.study.AppViewModel

/** 首次引导：欢迎+基础设置+AI 配置 → 目标向导（含进度调查表）。 */
@Composable
fun Onboarding(vm: AppViewModel) {
    var step by remember { mutableIntStateOf(0) }
    if (step == 1) { GoalWizard(vm, onClose = null); return }
    val s0 = vm.state.settings
    var hours by remember { mutableFloatStateOf(s0.dailyMinutes / 60f) }
    var start by remember { mutableStateOf(s0.startTime) }
    var base by remember { mutableStateOf(s0.aiBaseUrl) }
    var key by remember { mutableStateOf(s0.aiKey) }
    var model by remember { mutableStateOf(s0.aiModel) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("你好 👋", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("任何考试、任何学习目标都可以交给我。先做两件小事：", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card2 {
            Text("每天可投入学习：${hours.toInt()} 小时", fontWeight = FontWeight.SemiBold)
            Slider(hours, { hours = it }, valueRange = 1f..14f, steps = 12)
            OutlinedTextField(start, { start = it }, label = { Text("每天开始学习时间 HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Card2 {
            Text("连接 AI（推荐）", fontWeight = FontWeight.SemiBold)
            Text("AI 用来根据你的进度生成计划、查考试日期、调整计划。支持 OpenAI 兼容接口，默认是 DeepSeek（便宜）。日常排程在本地完成，不消耗 token。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API Key（可稍后在设置里填）") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button({
            vm.saveBasics(s0.copy(dailyMinutes = hours.toInt() * 60, startTime = start.trim(), aiBaseUrl = base.trim(), aiKey = key.trim(), aiModel = model.trim()))
            step = 1
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("下一步：填写目标与进度") }
    }
}

