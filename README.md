# PlanApp · 学习规划与打卡

一款 Android「学习规划 / 每日打卡」App。告诉它你的目标和当前进度，它会自动排出每天的学习任务，你只需每天打卡。

- 技术栈：Kotlin · Jetpack Compose · Material 3（深色模式、Edge-to-edge）
- 包名：`com.planapp.study`
- 当前版本：**1.1.0**（versionCode 2），minSdk 26 / targetSdk 36

## 功能

**目标向导**
- 预设目标：考研 / 四级 / 六级 / 考公 / 雅思 / 托福 / 教资，也可自定义任意目标
- 考试日期可手动填写，或由 AI 查询
- 用自然语言描述当前进度，AI 一次性生成 2–6 个科目及学习单元，并根据进度预先勾选已完成内容
- **进度调查表**：复核生成结果，支持勾选、"到此都已完成"、删除、添加
- 未配置 AI 时可用内置模板（考研 / 四六级 / 雅思 / 考公）或手动创建

**今日**
- 每天第一次打开弹出"今日任务表"，逐项打卡

**计划**
- 本地排程引擎自动分配每日任务：
  - 紧迫度 = 剩余时长 / 剩余天数 × 权重，各科轮转，长期未排到的科目自动加权
  - 每日不超量、时间段不重叠、避开午饭 / 晚饭、穿插休息、考前一天回顾
- **AI 调整计划**：用自然语言提要求（如"多安排数学"），AI 返回调整操作（权重、增删单元、每日时长），本地执行

**进度**
- 查看与编辑各科完成情况，新建学习目标

**提醒**
- 早上推送当日计划、晚上提醒打卡；开机后自动重设（非精确闹钟）

**设置**
- 每日学习时长、开始时间、AI 配置（OpenAI 兼容 `/chat/completions`）
- 提醒开关与测试、"重新开始"（清空目标和打卡记录，需二次确认）

> AI 为低频调用以节省 token，仅在生成目标、查询日期、调整计划时使用。所有数据保存在本地 `planapp_state.json`。

## 代码结构

```
app/src/main/java/com/planapp/study/
  Model.kt         数据类 + JSON 序列化
  Store.kt         本地状态原子保存
  Templates.kt     内置目标模板与考试日期
  Scheduler.kt     本地排程引擎
  AiClient.kt      AI 接口：日期查询 / 目标生成 / 计划调整
  AppViewModel.kt  状态管理、每日计划冻结与跨天重排、打卡
  Reminder.kt      每日提醒通知
  ui/              Onboarding、GoalWizard、Today、Plan、Progress、Settings
app/src/test/.../SchedulerTest.kt   排程单元测试（模拟 85 天）
```

## 构建

需要 JDK 17 与 Android SDK（AGP 9.0.1、Kotlin 2.3.20、Compose BOM 2026.03.01）。

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。APK 不随源码提交，请从 Releases 获取。

升级时使用 `adb install -r` 原位安装，保留已有数据。

## 版本历史

| 版本 | 主要变化 |
|---|---|
| 1.1.0 | 通用目标 + AI 目标向导、进度调查表、AI 调整计划、每日任务弹窗、重新开始 |
| 1.0 | 考研 + 四六级规划、本地排程引擎、每日提醒 |

## 待办

- 新向导的真机 UI 验证
- 可选：桌面小组件、统计图表、数据备份
