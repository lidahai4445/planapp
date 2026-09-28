package com.planapp.study

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 每日提醒：早上推送"今日计划"，晚上若还有未打卡任务则提醒。
 * 使用 AlarmManager 非精确闹钟（setAndAllowWhileIdle），无需精确闹钟权限，省电；开机后自动重设。
 */
object Reminder {
    private const val CHANNEL = "daily"
    const val EXTRA_KIND = "kind" // morning / evening

    fun scheduleAll(ctx: Context, s: Settings) {
        schedule(ctx, "morning", s.morningTime, s.remindOn)
        schedule(ctx, "evening", s.eveningTime, s.remindOn)
    }

    private fun pi(ctx: Context, kind: String) = PendingIntent.getBroadcast(ctx, kind.hashCode(),
        Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, kind: String, time: String, on: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, kind)
        am.cancel(p)
        if (!on) return
        val t = runCatching { LocalTime.parse(time) }.getOrNull() ?: return
        var at = LocalDateTime.of(LocalDate.now(), t)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
    }

    fun notify(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "每日学习提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(title.hashCode(), n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val state = Store(ctx).load() ?: return
        val s = state.settings
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminder.scheduleAll(ctx, s); return }
        val kind = intent.getStringExtra(Reminder.EXTRA_KIND) ?: return
        // 今天的计划：若已生成就用它，否则现场计算一份（不改动存储）
        val today = LocalDate.now()
        val items = state.today?.takeIf { it.date == today.toString() }?.items
            ?: Scheduler.plan(state.goals, s, today, 1).first().items
        val work = items.filter { it.minutes > 0 }
        val left = work.filter { !it.done }
        if (kind == "morning" && work.isNotEmpty()) {
            Reminder.notify(ctx, "今天共 ${work.size} 项 · ${work.sumOf { it.minutes } / 60.0} 小时".replace(".0 ", " "),
                work.take(4).joinToString("\n") { "${it.start} ${it.title}" } + if (work.size > 4) "\n…" else "")
        } else if (kind == "evening" && left.isNotEmpty()) {
            Reminder.notify(ctx, "还有 ${left.size} 项没打卡", "做完的记得打钩；没做完的明天会自动重排，不用焦虑 🌙")
        }
        Reminder.schedule(ctx, kind, if (kind == "morning") s.morningTime else s.eveningTime, s.remindOn)
    }
}

