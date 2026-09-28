package com.planapp.study

/** 内置模板：用户第一次打开时生成，之后可在"进度"页勾选已完成、增删单元，也可让 AI 重新生成大纲。 */
object Templates {
    // 2027 考研初试：教育部《2027年全国硕士研究生招生工作管理规定》公布为 2026-12-19 ~ 20
    const val KAOYAN_DATE = "2026-12-19"
    // 2026 年下半年四六级：官方通常 9 月公告，12 月第二个周六，此为预估，可用 AI 更新
    const val CET_DATE = "2026-12-12"

    private fun units(prefix: String, names: List<String>, m: Int) = names.map { StudyUnit(title = "$prefix$it", minutes = m) }
    private fun range(fmt: String, r: IntProgression, m: Int) = r.map { StudyUnit(title = fmt.format(it), minutes = m) }

    fun kaoyan(): Goal = Goal(
        name = "考研", examDate = KAOYAN_DATE, dateNote = "教育部已公布：2026-12-19 至 20 日",
        subjects = listOf(
            Subject(name = "数学一", color = 0xFF4F6BED, weight = 1.4, units =
                units("高数·", listOf("函数极限连续", "一元微分学", "一元积分学", "多元微分学", "二重积分", "微分方程", "无穷级数", "三重/曲线曲面积分"), 120) +
                units("线代·", listOf("行列式", "矩阵", "向量", "线性方程组", "特征值特征向量", "二次型"), 100) +
                units("概率·", listOf("随机事件与概率", "随机变量分布", "多维随机变量", "数字特征", "大数定律中心极限", "数理统计"), 100) +
                range("真题 %d 年（套卷+订正）", 2010..2025, 180) +
                range("模拟卷 第%d套", 1..6, 180)),
            Subject(name = "英语", color = 0xFF22A06B, weight = 1.0, dailyHabit = "背单词", habitMinutes = 30, units =
                range("阅读真题 %d 年（4篇精读）", 2010..2025, 90) +
                units("", listOf("新题型专项 1", "新题型专项 2", "翻译专项 1", "翻译专项 2", "完形专项"), 60) +
                units("作文·", listOf("小作文书信模板", "大作文图画模板", "大作文图表模板", "作文真题默写 1", "作文真题默写 2", "作文真题默写 3"), 60)),
            Subject(name = "政治", color = 0xFFE5484D, weight = 0.9, units =
                units("", listOf("马原 精讲+1000题", "毛中特 精讲+1000题", "史纲 精讲+1000题", "思修法基 精讲+1000题", "时政专题"), 150) +
                units("", listOf("1000题 二刷错题", "选择题真题 5 年", "肖八 选择", "肖四 选择", "肖四 大题背诵 1", "肖四 大题背诵 2", "肖四 大题背诵 3"), 120)),
            Subject(name = "专业课", color = 0xFFF59E0B, weight = 1.2, units =
                range("教材第 %d 章 + 课后题", 1..10, 120) +
                range("专业课真题 第%d套", 1..6, 180)),
        ),
    )

    fun cet(level: Int): Goal = Goal(
        name = if (level == 4) "英语四级" else "英语六级", examDate = CET_DATE, dateNote = "预估日期，可点 AI 更新",
        subjects = listOf(
            Subject(name = "CET-$level", color = if (level == 4) 0xFF06B6D4 else 0xFF8B5CF6, weight = 0.8,
                dailyHabit = "CET-$level 单词", habitMinutes = 20, units =
                range("听力真题 第%d套", 1..8, 40) +
                range("阅读真题 第%d套", 1..8, 50) +
                units("", listOf("翻译专项 1", "翻译专项 2", "写作模板整理", "写作练习 1", "写作练习 2"), 40) +
                range("整套模考 第%d套", 1..4, 130)),
        ),
    )

    fun ielts(): Goal = Goal(name = "雅思", examDate = java.time.LocalDate.now().plusMonths(4).toString(), dateNote = "雅思可自选考期，请修改",
        subjects = listOf(
            Subject(name = "听力", color = 0xFF06B6D4, dailyHabit = "雅思单词", habitMinutes = 30, units = range("剑雅 听力 第%d套+精听", 1..16, 80)),
            Subject(name = "阅读", color = 0xFF22A06B, units = range("剑雅 阅读 第%d套+复盘", 1..16, 80)),
            Subject(name = "写作", color = 0xFFF59E0B, units = units("", listOf("小作文图表类型", "大作文题型结构", "同义替换积累"), 60) + range("写作练习 第%d篇", 1..12, 60)),
            Subject(name = "口语", color = 0xFFE5484D, units = range("口语 Part1/2/3 题库 第%d组", 1..10, 50) + range("模拟口试 第%d次", 1..4, 40)),
        ))

    fun gongkao(): Goal = Goal(name = "考公", examDate = java.time.LocalDate.now().plusMonths(3).toString(), dateNote = "请按国考/省考公告修改",
        subjects = listOf(
            Subject(name = "行测", color = 0xFF4F6BED, weight = 1.3, dailyHabit = "常识/言语积累", habitMinutes = 20, units =
                units("", listOf("言语理解", "数量关系", "判断推理-图形", "判断推理-逻辑", "资料分析", "常识判断"), 120) +
                range("行测真题 第%d套", 1..12, 120)),
            Subject(name = "申论", color = 0xFFE5484D, units =
                units("", listOf("归纳概括", "综合分析", "提出对策", "贯彻执行", "大作文立意", "大作文结构"), 90) +
                range("申论真题 第%d套", 1..8, 150)),
        ))

    /** 向导里的预设目标：名称 -> 内置模板（无 AI 时兜底）。 */
    val presets = listOf("考研", "英语四级", "英语六级", "考公", "雅思", "托福", "教资", "自定义")
    fun byName(name: String): Goal? = when (name) {
        "考研" -> kaoyan(); "英语四级" -> cet(4); "英语六级" -> cet(6); "考公" -> gongkao(); "雅思" -> ielts(); else -> null
    }
}

