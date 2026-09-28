package com.planapp.study

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

/** 一个可完成的学习单元（章节 / 一套真题 / 一轮复习），完成即打钩。 */
data class StudyUnit(
    val id: String = newId(),
    val title: String,
    val minutes: Int,
    val done: Boolean = false,
    val doneDate: String? = null,
)

/** 科目：属于某个目标（考研 / 四级 / 六级 …），有一串按顺序完成的单元 + 可选的每日习惯。 */
data class Subject(
    val id: String = newId(),
    val name: String,
    val color: Long,
    val weight: Double = 1.0,
    val dailyHabit: String? = null,
    val habitMinutes: Int = 0,
    val units: List<StudyUnit> = emptyList(),
) {
    val totalMinutes get() = units.sumOf { it.minutes }
    val doneMinutes get() = units.filter { it.done }.sumOf { it.minutes }
    val progress get() = if (totalMinutes == 0) 0f else doneMinutes.toFloat() / totalMinutes
}

data class Goal(
    val id: String = newId(),
    val name: String,
    val examDate: String,
    val dateNote: String = "",
    val enabled: Boolean = true,
    val subjects: List<Subject> = emptyList(),
) {
    val date: LocalDate get() = LocalDate.parse(examDate)
}

data class Settings(
    val dailyMinutes: Int = 360,
    val startTime: String = "08:30",
    val breakMinutes: Int = 10,
    val restWeekday: Int = 0,          // 0=不休息, 1..7 = 周一..周日 轻松日
    val lunch: String = "12:00-13:30",
    val dinner: String = "17:30-18:30",
    val aiBaseUrl: String = "https://api.deepseek.com/v1",
    val aiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiAutoDays: Int = 14,
    val lastAiUpdate: String = "",
    val remindOn: Boolean = true,
    val morningTime: String = "07:50",
    val eveningTime: String = "21:30",
)

/** 某天的一条计划任务。unitId 为空表示每日习惯。 */
data class PlanItem(
    val id: String = newId(),
    val goalId: String,
    val subjectId: String,
    val unitId: String?,
    val title: String,
    val subjectName: String,
    val color: Long,
    val start: String,
    val end: String,
    val minutes: Int,
    val done: Boolean = false,
)

data class DayPlan(val date: String, val items: List<PlanItem>)

data class AppState(
    val goals: List<Goal> = emptyList(),
    val settings: Settings = Settings(),
    val today: DayPlan? = null,
    val checkins: Map<String, Int> = emptyMap(), // 日期 -> 完成分钟数
    val aiLog: List<String> = emptyList(),
    val onboarded: Boolean = false,
    val popupDate: String = "",
)

// ---------------- JSON 序列化（只用系统自带 org.json，零额外依赖） ----------------
object Json {
    fun unit(u: StudyUnit) = JSONObject().put("id", u.id).put("t", u.title).put("m", u.minutes)
        .put("d", u.done).put("dd", u.doneDate ?: "")
    fun unit(o: JSONObject) = StudyUnit(o.getString("id"), o.getString("t"), o.getInt("m"),
        o.optBoolean("d"), o.optString("dd").ifBlank { null })

    fun subject(s: Subject) = JSONObject().put("id", s.id).put("n", s.name).put("c", s.color)
        .put("w", s.weight).put("h", s.dailyHabit ?: "").put("hm", s.habitMinutes)
        .put("u", JSONArray(s.units.map { unit(it) }))
    fun subject(o: JSONObject) = Subject(o.getString("id"), o.getString("n"), o.getLong("c"),
        o.optDouble("w", 1.0), o.optString("h").ifBlank { null }, o.optInt("hm"),
        o.getJSONArray("u").objs().map { unit(it) })

    fun goal(g: Goal) = JSONObject().put("id", g.id).put("n", g.name).put("e", g.examDate)
        .put("dn", g.dateNote).put("on", g.enabled).put("s", JSONArray(g.subjects.map { subject(it) }))
    fun goal(o: JSONObject) = Goal(o.getString("id"), o.getString("n"), o.getString("e"),
        o.optString("dn"), o.optBoolean("on", true), o.getJSONArray("s").objs().map { subject(it) })

    fun settings(s: Settings) = JSONObject().put("dm", s.dailyMinutes).put("st", s.startTime)
        .put("bm", s.breakMinutes).put("rw", s.restWeekday).put("l", s.lunch).put("di", s.dinner)
        .put("ab", s.aiBaseUrl).put("ak", s.aiKey).put("am", s.aiModel).put("ad", s.aiAutoDays)
        .put("la", s.lastAiUpdate).put("ro", s.remindOn).put("mt", s.morningTime).put("et", s.eveningTime)
    fun settings(o: JSONObject) = Settings(o.optInt("dm", 360), o.optString("st", "08:30"),
        o.optInt("bm", 10), o.optInt("rw", 0), o.optString("l", "12:00-13:30"),
        o.optString("di", "17:30-18:30"), o.optString("ab", Settings().aiBaseUrl), o.optString("ak"),
        o.optString("am", Settings().aiModel), o.optInt("ad", 14), o.optString("la"),
        o.optBoolean("ro", true), o.optString("mt", "07:50"), o.optString("et", "21:30"))

    fun item(i: PlanItem) = JSONObject().put("id", i.id).put("g", i.goalId).put("s", i.subjectId)
        .put("u", i.unitId ?: "").put("t", i.title).put("sn", i.subjectName).put("c", i.color)
        .put("a", i.start).put("b", i.end).put("m", i.minutes).put("d", i.done)
    fun item(o: JSONObject) = PlanItem(o.getString("id"), o.getString("g"), o.getString("s"),
        o.optString("u").ifBlank { null }, o.getString("t"), o.getString("sn"), o.getLong("c"),
        o.getString("a"), o.getString("b"), o.getInt("m"), o.optBoolean("d"))

    fun state(s: AppState): String = JSONObject()
        .put("v", 1)
        .put("goals", JSONArray(s.goals.map { goal(it) }))
        .put("settings", settings(s.settings))
        .put("today", s.today?.let { JSONObject().put("date", it.date).put("items", JSONArray(it.items.map { x -> item(x) })) })
        .put("checkins", JSONObject(s.checkins as Map<*, *>))
        .put("aiLog", JSONArray(s.aiLog))
        .put("onboarded", s.onboarded)
        .put("popupDate", s.popupDate)
        .toString()

    fun state(text: String): AppState {
        val o = JSONObject(text)
        val ck = o.optJSONObject("checkins")
        return AppState(
            goals = o.getJSONArray("goals").objs().map { goal(it) },
            settings = settings(o.getJSONObject("settings")),
            today = o.optJSONObject("today")?.let { DayPlan(it.getString("date"), it.getJSONArray("items").objs().map { x -> item(x) }) },
            checkins = ck?.keys()?.asSequence()?.associateWith { ck.getInt(it) } ?: emptyMap(),
            aiLog = o.optJSONArray("aiLog")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            onboarded = o.optBoolean("onboarded"),
            popupDate = o.optString("popupDate"),
        )
    }
}

fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

