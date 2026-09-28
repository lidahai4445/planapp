package com.planapp.study

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * AI 接口预留层：兼容 OpenAI Chat Completions 协议（DeepSeek / 通义 / Kimi / OpenAI / 本地 Ollama 等均可）。
 * 省 token 策略：
 *  - 只在 "距上次更新 ≥ aiAutoDays 天" 或用户手动点击时调用；
 *  - 请求只发送最小必要信息，要求模型只返回紧凑 JSON，max_tokens 严格限制；
 *  - 排程完全在本地完成，AI 只负责 "考试日期校准" 和 "生成/更新大纲单元"。
 */
class AiClient(private val s: Settings) {
    val configured get() = s.aiKey.isNotBlank() && s.aiBaseUrl.isNotBlank()

    private fun chat(system: String, user: String, maxTokens: Int): String {
        val url = URL(s.aiBaseUrl.trimEnd('/') + "/chat/completions")
        val body = JSONObject()
            .put("model", s.aiModel)
            .put("temperature", 0.2)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 60000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${s.aiKey}")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) error("HTTP $code: ${text.take(200)}")
        return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun extractJson(t: String): String {
        val a = t.indexOf('{'); val b = t.lastIndexOf('}')
        require(a >= 0 && b > a) { "AI 未返回 JSON" }
        return t.substring(a, b + 1)
    }

    data class ExamInfo(val name: String, val date: String, val note: String)

    /** 校准考试日期（非常省 token：约 150 输入 + 100 输出）。 */
    fun refreshExamDates(goals: List<Goal>): List<ExamInfo> {
        val names = goals.joinToString("、") { "${it.name}(当前记录 ${it.examDate})" }
        val out = chat(
            "你是中国教育考试日历助手。只输出 JSON，不要解释。",
            "今天是 ${LocalDate.now()}。请给出以下考试最近一次（未来）的官方或最可能日期：$names。" +
                "输出格式 {\"exams\":[{\"name\":\"原名\",\"date\":\"yyyy-MM-dd\",\"note\":\"官方已公布/预估，≤20字\"}]}",
            300)
        return JSONObject(extractJson(out)).getJSONArray("exams").objs().map {
            ExamInfo(it.getString("name"), it.getString("date"), it.optString("note"))
        }.filter { runCatching { LocalDate.parse(it.date) }.isSuccess }
    }

    /** 为某科生成复习单元（用户说明 + 剩余天数），返回新单元列表。 */
    fun generateUnits(goal: Goal, subject: Subject, hint: String): List<StudyUnit> {
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), goal.date)
        val out = chat(
            "你是考研/四六级备考规划师。只输出 JSON，不要解释。",
            "目标:${goal.name}，科目:${subject.name}，距考试 $days 天。用户说明:${hint.ifBlank { "无" }}。" +
                "已完成:${subject.units.filter { it.done }.joinToString("、") { it.title }.take(300)}。" +
                "请给出剩余需完成的复习单元（按先后顺序，每个 40-180 分钟，最多 30 个，标题≤16字），" +
                "格式 {\"units\":[{\"t\":\"标题\",\"m\":分钟}]}",
            1200)
        return JSONObject(extractJson(out)).getJSONArray("units").objs().map {
            StudyUnit(title = it.getString("t").take(24), minutes = it.optInt("m", 60).coerceIn(20, 240))
        }
    }

    /** 查询单个考试的日期（约 100 token）。 */
    fun suggestDate(name: String): ExamInfo? = refreshExamDates(listOf(Goal(name = name, examDate = LocalDate.now().toString()))).firstOrNull()

    /**
     * 根据目标 + 用户自述进度，一次性生成完整的科目/单元结构；已掌握内容标记 d=true，
     * 用户随后在"进度调查表"里复核。输出上限约 2.5k token，只在新建目标时调用一次。
     */
    fun buildGoal(name: String, examDate: String, progress: String, extra: String, dailyMinutes: Int): List<Subject> {
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(examDate))
        val out = chat(
            "你是资深备考规划师，熟悉考研、四六级、考公、雅思托福、教资、证书等各类考试。只输出 JSON，不要解释。",
            "目标:$name；考试日期:$examDate（剩 $days 天）；每天可学习 ${dailyMinutes} 分钟。" +
                "当前进度:${progress.ifBlank { "零基础" }}。补充:${extra.ifBlank { "无" }}。\n" +
                "请拆分为 2-6 个科目，每科按学习先后给出 6-24 个可打卡单元（标题≤16字，40-180分钟，包含基础、强化、真题/模考、冲刺阶段），" +
                "用户已完成的单元 d=true。需要每日坚持的（如背单词）用 habit 表示。总量应与剩余天数×每日时间大致匹配。" +
                "格式:{\"subjects\":[{\"n\":\"科目\",\"w\":1.0,\"habit\":\"背单词\",\"hm\":30,\"units\":[{\"t\":\"标题\",\"m\":90,\"d\":false}]}]}",
            3500)
        val palette = listOf(0xFF4F6BED, 0xFF22A06B, 0xFFE5484D, 0xFFF59E0B, 0xFF8B5CF6, 0xFF06B6D4)
        return JSONObject(extractJson(out)).getJSONArray("subjects").objs().take(6).mapIndexed { i, o ->
            Subject(name = o.getString("n").take(12), color = palette[i % palette.size],
                weight = o.optDouble("w", 1.0).coerceIn(0.3, 2.0),
                dailyHabit = o.optString("habit").ifBlank { null }?.take(12),
                habitMinutes = o.optInt("hm", 0).coerceIn(0, 90),
                units = o.getJSONArray("units").objs().take(30).map {
                    StudyUnit(title = it.getString("t").take(24), minutes = it.optInt("m", 60).coerceIn(20, 240),
                        done = it.optBoolean("d"), doneDate = if (it.optBoolean("d")) "早期" else null)
                })
        }.filter { it.units.isNotEmpty() || it.dailyHabit != null }
    }

    data class Adjust(val weights: Map<String, Double>, val add: List<Triple<String, StudyUnit, Boolean>>,
                      val remove: List<String>, val done: List<String>, val dailyMinutes: Int?, val note: String)

    /** 自然语言调整计划：只发送精简摘要，AI 返回操作指令，本地执行。 */
    fun adjust(goals: List<Goal>, s: Settings, request: String): Adjust {
        val summary = goals.filter { it.enabled }.joinToString("\n") { g ->
            "【${g.name} ${g.examDate}】" + g.subjects.joinToString("；") { sub ->
                "${sub.name}(权重${sub.weight},剩余:" + sub.units.filter { !it.done }.take(12).joinToString(",") { "${it.title}/${it.minutes}" } + ")"
            }
        }.take(2500)
        val out = chat(
            "你是学习计划调整助手。根据用户要求修改计划，只输出 JSON 操作，不要解释。",
            "今天 ${LocalDate.now()}，每日学习 ${s.dailyMinutes} 分钟。当前计划:\n$summary\n用户要求:$request\n" +
                "可用操作:{\"weights\":{\"科目\":0.3-2.0},\"add\":[{\"s\":\"科目\",\"t\":\"标题\",\"m\":60,\"front\":true}]," +
                "\"remove\":[\"单元标题\"],\"done\":[\"已完成的单元标题\"],\"daily_minutes\":null,\"note\":\"一句话说明\"}，不需要的字段可省略。",
            800)
        val o = JSONObject(extractJson(out))
        val w = o.optJSONObject("weights")
        return Adjust(
            weights = w?.keys()?.asSequence()?.associateWith { w.getDouble(it).coerceIn(0.3, 2.0) } ?: emptyMap(),
            add = o.optJSONArray("add")?.objs()?.map { Triple(it.getString("s"), StudyUnit(title = it.getString("t").take(24),
                minutes = it.optInt("m", 60).coerceIn(20, 240)), it.optBoolean("front")) } ?: emptyList(),
            remove = o.optJSONArray("remove")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            done = o.optJSONArray("done")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            dailyMinutes = if (o.has("daily_minutes") && !o.isNull("daily_minutes")) o.getInt("daily_minutes").coerceIn(60, 840) else null,
            note = o.optString("note"),
        )
    }
}

