package com.planapp.study.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planapp.study.AppViewModel

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(Tab("今日", Icons.Filled.Home), Tab("计划", Icons.Filled.DateRange),
    Tab("进度", Icons.AutoMirrored.Filled.List), Tab("设置", Icons.Filled.Settings))

@Composable
fun App(vm: AppViewModel = viewModel()) {
    PlanTheme {
        // 回到前台时检查是否跨天，跨天自动重排
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.ensureToday() }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
        if (!vm.state.onboarded) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Onboarding(vm) }
            return@PlanTheme
        }
        if (vm.wizardOpen) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { GoalWizard(vm) { vm.wizardOpen = false } }
            return@PlanTheme
        }
        if (vm.showDailyPopup) DailyPopup(vm)
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label) })
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> TodayScreen(vm)
                    1 -> PlanScreen(vm)
                    2 -> ProgressScreen(vm)
                    else -> SettingsScreen(vm)
                }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter))
            }
        }
    }
}

/** 每天第一次打开时弹出的今日任务表。 */
@Composable
fun DailyPopup(vm: AppViewModel) {
    val items = vm.state.today?.items.orEmpty()
    AlertDialog(onDismissRequest = { vm.dismissPopup() },
        title = { Text("今日任务表 · ${fmtMin(items.sumOf { it.minutes })}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { i ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(i.start, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
                        Dot(i.color)
                        Text(i.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton({ vm.dismissPopup() }) { Text("开始今天 💪") } })
}

