package com.planapp.study

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class SchedulerTest {
    @Test
    fun planIsFeasibleAndConflictFree() {
        val goals = listOf(Templates.kaoyan(), Templates.cet(6))
        val s = Settings(dailyMinutes = 480, restWeekday = 7)
        val days = Scheduler.plan(goals, s, LocalDate.of(2026, 9, 28), 85)
        val sb = StringBuilder()
        days.forEach { d ->
            val cap = Scheduler.capacity(LocalDate.parse(d.date), s)
            val total = d.items.sumOf { it.minutes }
            assertTrue("${d.date} 超量 $total > $cap", total <= cap + 20)
            val timed = d.items.filter { it.minutes > 0 }.sortedBy { it.start }
            timed.zipWithNext().forEach { (a, b) ->
                assertTrue("${d.date} 冲突 ${a.title} / ${b.title}", !LocalTime.parse(a.end).isAfter(LocalTime.parse(b.start)))
            }
            val meals = listOf(LocalTime.of(12, 0) to LocalTime.of(13, 30), LocalTime.of(17, 30) to LocalTime.of(18, 30))
            timed.forEach { x -> meals.forEach { (ma, mb) ->
                assertTrue("${d.date} 占用饭点 ${x.title}", !(LocalTime.parse(x.start).isBefore(mb) && LocalTime.parse(x.end).isAfter(ma)))
            } }
            sb.append("${d.date} [${total}m]\n")
            d.items.forEach { sb.append("   ${it.start}-${it.end} ${it.subjectName} | ${it.title}\n") }
        }
        val usedUnits = days.flatMap { d -> d.items.mapNotNull { it.unitId } }
        assertTrue("单元重复排程", usedUnits.size == usedUnits.toSet().size)
        File("build/scheduler_preview.txt").writeText(sb.toString())
    }
}

