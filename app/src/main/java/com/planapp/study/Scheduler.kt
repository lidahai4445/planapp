package com.planapp.study

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 本地排程引擎（不消耗 token）：
 * 1. 每科 "紧迫度" = 剩余分钟 / 剩余可用天数 × 权重；越接近考试、剩余越多，越优先。
 * 2. 每天容量 = settings.dailyMinutes（轻松日减半），先放每日习惯，再按紧迫度贪心挑单元，
 *    同一科一天最多 2 个大单元，避免单调。
 * 3. 生成时间段：从起始时间开始顺排，自动跳过午饭 / 晚饭时段并插入休息，保证不冲突。
 * 4. 考前 1 天不再排新内容，只安排 "回顾 + 早睡"。
 */
object Scheduler {
    private data class Pick(val goal: Goal, val subject: Subject, val unit: StudyUnit?, val title: String, val minutes: Int)

    fun capacity(date: LocalDate, s: Settings): Int =
        if (s.restWeekday != 0 && date.dayOfWeek.value == s.restWeekday) s.dailyMinutes / 2 else s.dailyMinutes

    /** 生成从 from 开始 days 天的计划预览。 excluded：已排进今天、不应再出现在后续日期的单元。 */
    fun plan(goals: List<Goal>, s: Settings, from: LocalDate, days: Int, excluded: Set<String> = emptySet()): List<DayPlan> {
        val used = excluded.toMutableSet()
        val idle = mutableMapOf<String, Int>() // 科目 -> 连续几天没排到
        return (0 until days).map { d ->
            val date = from.plusDays(d.toLong())
            val picks = pickForDay(goals, s, date, used, idle)
            picks.mapNotNull { it.unit?.id }.forEach { used += it }
            val hit = picks.filter { it.unit != null }.map { it.subject.id }.toSet()
            goals.flatMap { it.subjects }.forEach { sub -> idle[sub.id] = if (sub.id in hit) 0 else (idle[sub.id] ?: 0) + 1 }
            DayPlan(date.toString(), toSlots(picks, s))
        }
    }

    private fun pickForDay(goals: List<Goal>, s: Settings, date: LocalDate, used: Set<String>, idle: Map<String, Int>): List<Pick> {
        val active = goals.filter { it.enabled && !it.date.isBefore(date) }
        var cap = capacity(date, s)
        val out = mutableListOf<Pick>()
        // 考试当天 / 前一天
        active.filter { it.date == date }.forEach { g -> out += Pick(g, g.subjects.first(), null, "🎯 ${g.name} 考试日，加油！", 0) }
        active.filter { it.date == date.plusDays(1) }.forEach { g ->
            out += Pick(g, g.subjects.first(), null, "${g.name} 考前回顾：错题/模板/准考证", 90); cap -= 90
        }
        val normal = active.filter { ChronoUnit.DAYS.between(date, it.date) >= 2 }
        // 每日习惯
        normal.forEach { g -> g.subjects.filter { it.dailyHabit != null && it.habitMinutes > 0 }.forEach { sub ->
            out += Pick(g, sub, null, sub.dailyHabit!!, sub.habitMinutes); cap -= sub.habitMinutes
        } }
        // 候选队列
        data class Q(val g: Goal, val s: Subject, val rest: ArrayDeque<StudyUnit>, var taken: Int = 0)
        val queues = normal.flatMap { g -> g.subjects.map { sub ->
            Q(g, sub, ArrayDeque(sub.units.filter { !it.done && it.id !in used }))
        } }.filter { it.rest.isNotEmpty() }
        fun urgency(q: Q): Double {
            val daysLeft = max(1L, ChronoUnit.DAYS.between(date, q.g.date) - 1)
            // 紧迫度 × 权重；连续几天没学到的科目逐日加权，保证各科轮转、不偏科
            return q.rest.sumOf { it.minutes } / daysLeft.toDouble() * q.s.weight * (1 + 0.6 * (idle[q.s.id] ?: 0))
        }
        // 第一轮：每科最多 1 个单元（按紧迫度）；第二轮：还有空余时再允许第 2 个
        for (round in 1..2) {
            for (q in queues.sortedByDescending { urgency(it) }) {
                if (cap < 30 || q.rest.isEmpty() || q.taken >= round) continue
                val u = q.rest.first()
                if (u.minutes > cap + 20) continue
                q.rest.removeFirst(); q.taken++
                out += Pick(q.g, q.s, u, u.title, u.minutes); cap -= u.minutes
            }
        }
        // 一个都没排进（容量太小）：把最紧迫单元拆出一部分
        if (out.none { it.unit != null } && cap >= 30) {
            queues.maxByOrNull { urgency(it) }?.let { q -> val u = q.rest.first()
                out += Pick(q.g, q.s, u, u.title + "（部分）", cap) }
        }
        return out
    }

    private fun parseRange(r: String): Pair<LocalTime, LocalTime>? = runCatching {
        val (a, b) = r.split("-"); LocalTime.parse(a.trim()) to LocalTime.parse(b.trim())
    }.getOrNull()

    private fun toSlots(picks: List<Pick>, s: Settings): List<PlanItem> {
        val blocks = listOfNotNull(parseRange(s.lunch), parseRange(s.dinner)).sortedBy { it.first }
        var t = runCatching { LocalTime.parse(s.startTime) }.getOrDefault(LocalTime.of(8, 30))
        val out = mutableListOf<PlanItem>()
        picks.filter { it.minutes == 0 }.forEach { out += item(it, t, t) }
        val rest = picks.filter { it.minutes > 0 }.toMutableList()
        var sinceBreak = 0
        var guard = 0
        while (rest.isNotEmpty() && guard++ < 100) {
            blocks.forEach { (a, b) -> if (!t.isBefore(a) && t.isBefore(b)) { t = b; sinceBreak = 0 } }
            val next = blocks.firstOrNull { it.first.isAfter(t) }
            val gap = next?.let { ChronoUnit.MINUTES.between(t, it.first) } ?: Long.MAX_VALUE
            // 选第一个能在下一个饭点前完成的任务（习惯类优先保持原顺序）；都放不下就跳到饭后
            val p = rest.firstOrNull { it.minutes <= gap }
            if (p == null) { t = next!!.second; sinceBreak = 0; continue }
            rest.remove(p)
            val start = t
            val end = t.plusMinutes(p.minutes.toLong())
            out += item(p, start, end)
            sinceBreak += p.minutes
            t = if (sinceBreak >= 100) { sinceBreak = 0; end.plusMinutes(max(s.breakMinutes, 15).toLong()) }
                else end.plusMinutes(min(s.breakMinutes, 10).toLong())
            if (t.isBefore(start)) break // 跨午夜兜底
        }
        return out
    }

    private fun item(p: Pick, a: LocalTime, b: LocalTime) = PlanItem(
        goalId = p.goal.id, subjectId = p.subject.id, unitId = p.unit?.id, title = p.title,
        subjectName = if (p.unit == null && p.minutes > 0 && p.subject.dailyHabit == p.title) "每日" else p.subject.name,
        color = p.subject.color, start = a.toString(), end = b.toString(), minutes = p.minutes,
    )

    /** 剩余量预估：若按当前容量完成全部剩余需要多少天，与离考试的天数比较，给出人性化提示。 */
    fun feasibility(g: Goal, s: Settings, today: LocalDate): String {
        val remain = g.subjects.sumOf { sub -> sub.units.filter { !it.done }.sumOf { it.minutes } }
        val days = ChronoUnit.DAYS.between(today, g.date).coerceAtLeast(0)
        if (remain == 0) return "全部内容已完成，进入自由复习 ✨"
        val need = remain / 60.0
        val have = (days - 1).coerceAtLeast(0) * s.dailyMinutes / 60.0
        return when {
            have <= 0 -> "已到考前，保持状态"
            need <= have * 0.8 -> "剩余约 %.0f 小时，时间充裕，有余力可加练".format(need)
            need <= have -> "剩余约 %.0f 小时，节奏刚好，坚持每天打卡".format(need)
            else -> "剩余约 %.0f 小时 > 可用 %.0f 小时，建议增加每日时长或删减内容".format(need, have)
        }
    }
}

