package com.planapp.study

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    var state by mutableStateOf(store.load() ?: AppState())
        private set
    var busy by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
    var wizardOpen by mutableStateOf(false)

    init {
        ensureToday()
        Reminder.scheduleAll(app, state.settings)
        maybeAutoAi()
    }

    private fun update(f: (AppState) -> AppState) { state = f(state); store.save(state) }

    // ---------------- 初始化 / 目标向导 ----------------
    fun saveBasics(n: Settings) = update { it.copy(settings = n) }

    /** 向导确认：加入目标（首个目标同时完成初始化）。 */
    fun addGoalFull(g: Goal) {
        update { it.copy(goals = it.goals + g, onboarded = true) }
        ensureToday(force = true)
        Reminder.scheduleAll(getApplication(), state.settings)
        toast = "「${g.name}」已加入，计划已生成"
    }

    fun aiSuggestDate(name: String, cb: (String, String) -> Unit) {
        val c = AiClient(state.settings)
        if (!c.configured) { toast = "未配置 AI，请手动填写日期"; return }
        busy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { c.suggestDate(name) } }
            busy = false
            r.getOrNull()?.let { cb(it.date, it.note); log("查询 $name 日期: ${it.date}") } ?: run { toast = "查询失败：${r.exceptionOrNull()?.message ?: "无结果"}" }
        }
    }

    fun aiBuildGoal(name: String, date: String, progress: String, extra: String, cb: (List<Subject>) -> Unit) {
        val c = AiClient(state.settings)
        if (!c.configured) { toast = "未配置 AI：可用内置模板或手动创建"; return }
        busy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { c.buildGoal(name, date, progress, extra, state.settings.dailyMinutes) } }
            busy = false
            r.onSuccess { log("AI 生成「$name」${it.size} 科 ${it.sumOf { s -> s.units.size }} 单元"); cb(it) }
             .onFailure { toast = "AI 生成失败：${it.message}"; log("生成失败: ${it.message}") }
        }
    }

    fun aiAdjust(request: String, cb: (String) -> Unit) {
        val c = AiClient(state.settings)
        if (!c.configured) { toast = "请先在设置里配置 AI"; return }
        busy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { c.adjust(state.goals, state.settings, request) } }
            busy = false
            r.onSuccess { a ->
                update { s ->
                    val goals = s.goals.map { g -> if (!g.enabled) g else g.copy(subjects = g.subjects.map { sub ->
                        var units = sub.units.filter { u -> !u.done && a.remove.none { it == u.title } || u.done }
                        units = units.map { u -> if (!u.done && a.done.any { it == u.title }) u.copy(done = true, doneDate = LocalDate.now().toString()) else u }
                        val front = a.add.filter { it.first == sub.name && it.third }.map { it.second }
                        val back = a.add.filter { it.first == sub.name && !it.third }.map { it.second }
                        val firstTodo = units.indexOfFirst { !it.done }.let { if (it < 0) units.size else it }
                        units = units.take(firstTodo) + front + units.drop(firstTodo) + back
                        sub.copy(units = units, weight = a.weights[sub.name] ?: sub.weight)
                    }) }
                    s.copy(goals = goals, settings = a.dailyMinutes?.let { m -> s.settings.copy(dailyMinutes = m) } ?: s.settings)
                }
                log("AI 调整：$request → ${a.note}")
                ensureToday(force = true)
                cb(a.note.ifBlank { "已调整" })
            }.onFailure { toast = "AI 调整失败：${it.message}" }
        }
    }

    /** 每天首次打开弹出今日任务表。 */
    val showDailyPopup get() = state.onboarded && state.popupDate != LocalDate.now().toString() && (state.today?.items?.isNotEmpty() == true)
    fun dismissPopup() = update { it.copy(popupDate = LocalDate.now().toString()) }

    // ---------------- 旧版初始化 ----------------
    fun onboard(kaoyan: Boolean, cet: Int, dailyMinutes: Int, doneBefore: Boolean) {
        val goals = buildList {
            if (kaoyan) add(Templates.kaoyan())
            if (cet == 4 || cet == 46) add(Templates.cet(4))
            if (cet == 6 || cet == 46) add(Templates.cet(6))
        }
        update { it.copy(goals = goals, onboarded = true, today = null, settings = it.settings.copy(dailyMinutes = dailyMinutes)) }
        ensureToday(force = true)
    }

    // ---------------- 今日计划 ----------------
    /** 今天的计划一旦生成就冻结（打卡时不会乱跳）；跨天后自动根据最新进度重排。 */
    fun ensureToday(force: Boolean = false) {
        if (!state.onboarded) return
        val today = LocalDate.now().toString()
        if (!force && state.today?.date == today) return
        val keepDone = if (state.today?.date == today) state.today!!.items.filter { it.done } else emptyList()
        val fresh = Scheduler.plan(state.goals, state.settings, LocalDate.now(), 1,
            excluded = keepDone.mapNotNull { it.unitId }.toSet()).first()
        val items = if (keepDone.isEmpty()) fresh.items else keepDone + fresh.items.filter { f -> keepDone.none { it.unitId == null && it.title == f.title } }
        update { it.copy(today = DayPlan(today, items.sortedBy { i -> i.start })) }
    }

    fun toggle(item: PlanItem) {
        val now = !item.done
        val date = LocalDate.now().toString()
        update { s ->
            val items = s.today?.items?.map { if (it.id == item.id) it.copy(done = now) else it } ?: emptyList()
            val goals = if (item.unitId == null) s.goals else s.goals.map { g -> g.copy(subjects = g.subjects.map { sub ->
                sub.copy(units = sub.units.map { u -> if (u.id == item.unitId) u.copy(done = now, doneDate = if (now) date else null) else u })
            }) }
            val mins = items.filter { it.done }.sumOf { it.minutes }
            s.copy(goals = goals, today = s.today?.copy(items = items), checkins = s.checkins + (date to mins))
        }
    }

    fun upcoming(days: Int = 14): List<DayPlan> {
        val t = state.today ?: return emptyList()
        val excluded = t.items.mapNotNull { it.unitId }.toSet()
        return listOf(t) + Scheduler.plan(state.goals, state.settings, LocalDate.now().plusDays(1), days - 1, excluded)
    }

    fun streak(): Int {
        var d = LocalDate.now(); var n = 0
        if ((state.checkins[d.toString()] ?: 0) == 0) d = d.minusDays(1)
        while ((state.checkins[d.toString()] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    // ---------------- 进度编辑 ----------------
    private fun editSubject(goalId: String, subId: String, f: (Subject) -> Subject) = update { s ->
        s.copy(goals = s.goals.map { g -> if (g.id != goalId) g else g.copy(subjects = g.subjects.map { if (it.id == subId) f(it) else it }) })
    }
    fun setUnitDone(goalId: String, subId: String, unitId: String, done: Boolean) =
        editSubject(goalId, subId) { sub -> sub.copy(units = sub.units.map { if (it.id == unitId) it.copy(done = done, doneDate = if (done) "早期" else null) else it }) }
    fun markAllBefore(goalId: String, subId: String, unitId: String) =
        editSubject(goalId, subId) { sub -> val idx = sub.units.indexOfFirst { it.id == unitId }
            sub.copy(units = sub.units.mapIndexed { i, u -> if (i <= idx && !u.done) u.copy(done = true, doneDate = "早期") else u }) }
    fun addUnit(goalId: String, subId: String, title: String, minutes: Int) =
        editSubject(goalId, subId) { it.copy(units = it.units + StudyUnit(title = title, minutes = minutes)) }
    fun deleteUnit(goalId: String, subId: String, unitId: String) =
        editSubject(goalId, subId) { it.copy(units = it.units.filter { u -> u.id != unitId }) }
    fun setWeight(goalId: String, subId: String, w: Double) = editSubject(goalId, subId) { it.copy(weight = w) }
    fun setExamDate(goalId: String, date: String) = update { s -> s.copy(goals = s.goals.map { if (it.id == goalId) it.copy(examDate = date, dateNote = "手动设置") else it }) }
    fun toggleGoal(goalId: String) = update { s -> s.copy(goals = s.goals.map { if (it.id == goalId) it.copy(enabled = !it.enabled) else it }) }
    fun addGoal(name: String, date: String) = update { s -> s.copy(goals = s.goals + Goal(name = name, examDate = date,
        subjects = listOf(Subject(name = name, color = 0xFF64748B)))) }
    fun addSubject(goalId: String, name: String) = update { s -> s.copy(goals = s.goals.map { if (it.id == goalId)
        it.copy(subjects = it.subjects + Subject(name = name, color = listOf(0xFF0EA5E9, 0xFFEC4899, 0xFF84CC16, 0xFF64748B).random())) else it }) }
    fun deleteGoal(goalId: String) = update { s -> s.copy(goals = s.goals.filter { it.id != goalId }) }
    fun addTemplate(which: String) = update { s -> s.copy(goals = s.goals + when (which) { "cet4" -> Templates.cet(4); "cet6" -> Templates.cet(6); else -> Templates.kaoyan() }) }

    /** 清空所有目标与打卡记录（保留 AI 与作息设置），回到首次引导。 */
    fun resetAll() { update { AppState(settings = it.settings) }; toast = "已清空，重新开始" }

    fun replan() { ensureToday(force = true); toast = "已按最新进度重新排好今天" }

    fun saveSettings(n: Settings) { update { it.copy(settings = n) }; ensureToday(force = true); Reminder.scheduleAll(getApplication(), n) }
    fun testNotify() = Reminder.notify(getApplication(), "提醒测试", "通知正常 ✅ 每天 ${state.settings.morningTime} 推送计划，${state.settings.eveningTime} 提醒打卡")

    // ---------------- AI ----------------
    private fun log(msg: String) = update { it.copy(aiLog = (listOf("${LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} $msg") + it.aiLog).take(30)) }

    private fun maybeAutoAi() {
        val s = state.settings
        if (!state.onboarded || s.aiKey.isBlank()) return
        val last = runCatching { LocalDate.parse(s.lastAiUpdate) }.getOrNull()
        if (last == null || last.plusDays(s.aiAutoDays.toLong()) <= LocalDate.now()) aiRefreshDates(auto = true)
    }

    fun aiRefreshDates(auto: Boolean = false) {
        val client = AiClient(state.settings)
        if (!client.configured) { toast = "请先在设置里填写 AI 接口地址和 Key"; return }
        busy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { client.refreshExamDates(state.goals) } }
            busy = false
            r.onSuccess { list ->
                var changed = 0
                update { s -> s.copy(settings = s.settings.copy(lastAiUpdate = LocalDate.now().toString()), goals = s.goals.map { g ->
                    val hit = list.firstOrNull { it.name.contains(g.name) || g.name.contains(it.name) }
                    if (hit != null && hit.date != g.examDate && LocalDate.parse(hit.date) >= LocalDate.now()) { changed++; g.copy(examDate = hit.date, dateNote = "AI: " + hit.note) }
                    else if (hit != null) g.copy(dateNote = "AI 校验: " + hit.note) else g
                }) }
                log((if (auto) "自动" else "手动") + "校准考试日期，更新 $changed 项")
                toast = "考试日期已校准（更新 $changed 项）"
                if (changed > 0) ensureToday(force = true)
            }.onFailure { log("日期校准失败: ${it.message}"); if (!auto) toast = "AI 调用失败：${it.message}" }
        }
    }

    fun aiGenerateUnits(goalId: String, subId: String, hint: String, replace: Boolean) {
        val client = AiClient(state.settings)
        if (!client.configured) { toast = "请先在设置里填写 AI 接口地址和 Key"; return }
        val g = state.goals.first { it.id == goalId }; val sub = g.subjects.first { it.id == subId }
        busy = true
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { client.generateUnits(g, sub, hint) } }
            busy = false
            r.onSuccess { units ->
                editSubject(goalId, subId) { it.copy(units = (if (replace) it.units.filter { u -> u.done } else it.units) + units) }
                log("为 ${g.name}/${sub.name} 生成 ${units.size} 个单元")
                toast = "已生成 ${units.size} 个单元"
                ensureToday(force = true)
            }.onFailure { log("大纲生成失败: ${it.message}"); toast = "AI 调用失败：${it.message}" }
        }
    }
}

