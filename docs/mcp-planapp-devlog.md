# PlanApp 开发交接日志

> 用途：任务中断时，任何 AI / 开发者读取本文件即可无缝接手。每一步操作后追加记录。

## 1. 总体目的

为用户制作 Android「学习规划 / 任务打卡」App（考研 + 四六级）：

- 简洁美观（Kotlin + Jetpack Compose + Material 3，支持深色模式、Edge-to-edge）。
- 用户只需：①在「进度」页勾选以前已完成的内容；②每天在「今日」页打卡。
- 本地排程引擎自动分配每天任务（量合适、时间段不冲突、避开饭点、穿插休息、轻松日）。
- 预留 AI 接口（OpenAI 兼容协议），低频调用以节省 token：校准考试日期、生成科目大纲单元。
- 所有文件在 `C:\Users\21671\Desktop\workbody\planapp`；最终交付 APK 路径。

## 2. 环境事实（已探测）

- 通过 ShunCode MCP Bridge 操作 Windows；工作区根 = `C:\Users\21671\Desktop\workbody`（MCP 相对路径 `planapp/...`）。
- run_command 使用 Git Bash；PTY 输出常丢失 → 做法：命令输出重定向到 `planapp/_probe.txt`，再用 read_files 读取。
- JDK 17：`C:/Users/21671/AppData/Local/Programs/PaperlyToolchain/jdk17.0.20_12`（PATH 中无 java，需手动设 JAVA_HOME）。
- SDK：`C:/Users/21671/AppData/Local/Android/Sdk`（platforms 28/35/36/37，build-tools 36.0.0，无系统镜像 → 无模拟器）。
- Gradle 缓存：9.1.0、9.5.0。参考项目 `workbody/app`（Paperly）可成功构建：AGP 9.0.1、Kotlin 2.3.20、Compose BOM 2026.03.01。本项目沿用相同版本以复用离线缓存。
- Android CLI 1.0.16406183 在 `C:/Users/21671/AppData/AndroidCLI/android`，每次调用会因 metrics 上报超时额外耗时约 90 秒。
- 历史文档提到 ADB 设备 `d8a4be7c`（真机）。
- 2027 考研初试：2026-12-19 至 20 日（教育部已公布）。2026 年 12 月四六级：预估 2026-12-12，App 内可由 AI 校准。

## 3. 总体规划

1. [x] 环境探测
2. [x] 建立日志
3. [ ] 项目骨架（复制 Paperly 的 gradle wrapper；配置与 Paperly 一致）。没用 `android create`：CLI 每次调用约 90 秒，且离线环境下模板依赖版本可能没有缓存。
4. [ ] 核心代码：Model / Store(JSON 文件) / Templates / Scheduler / AiClient / AppViewModel
5. [ ] UI：Onboarding / 今日 / 计划 / 进度 / 设置
6. [ ] `gradlew :app:assembleDebug`，修复错误
7. [ ] adb 安装到真机、截图验证
8. [ ] 输出 APK 路径

## 4. 代码结构

```
planapp/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradle.properties, local.properties
  app/build.gradle.kts            (namespace com.planapp.study, minSdk 26, targetSdk 36)
  app/src/main/java/com/planapp/study/
    Model.kt        数据类 + org.json 序列化
    Store.kt        filesDir/planapp_state.json 原子保存
    Templates.kt    考研(数学/英语/政治/专业课)、CET4/6 内置单元模板 + 考试日期
    Scheduler.kt    本地排程：紧迫度=剩余分钟/剩余天数×权重；习惯任务；避开饭点；考前一天回顾
    AiClient.kt     OpenAI 兼容 /chat/completions；refreshExamDates / generateUnits
    AppViewModel.kt 状态、今日计划冻结与跨天重排、打卡、进度编辑、AI 调用节流(aiAutoDays)
    MainActivity.kt
    ui/ App.kt Theme.kt Common.kt Onboarding.kt TodayScreen.kt PlanScreen.kt ProgressScreen.kt SettingsScreen.kt
```

## 5. 操作记录

### 步骤 1：环境探测（完成）
- 结果见第 2 节。

### 步骤 2：写项目骨架和全部源码（完成）
- 在 Arena 沙盒 `/home/user/planapp` 写好源码，再用 MCP apply_patch 同步到 Windows `planapp/`（同步脚本在沙盒 `/home/user/mcp/push.py`）。
- 从 `../app` 复制了 gradlew、gradlew.bat、gradle/wrapper（Gradle 9.1.0）。

### 步骤 3：首次编译（进行中）
- 命令：`export JAVA_HOME=.../jdk17.0.20_12; ./gradlew :app:assembleDebug --offline --console=plain > build_log.txt`
- 输出在 `planapp/build_log.txt`，末尾有 `BUILD_EXIT=<code>`。
- 下一步：读 build_log.txt，修复 Kotlin 编译错误并重新编译。离线失败时去掉 --offline 重试。

### 步骤 3 结果：首次编译成功
- 只有一个弃用警告（Icons.Filled.List），已改成 `Icons.AutoMirrored.Filled.List` 并加上 import。
- 坑：MCP 终端 `terminal_1` 出现过半截引号导致 shell 卡住，命令一直返回 failed。解决：调用 run_command 时传 `cwd:"planapp"`，会新开 terminal_2。

### 步骤 4：排程单元测试 + 算法修正（完成）
- 新增 `app/src/test/.../SchedulerTest.kt`：考研 + 六级，模拟 85 天，断言以下几点：每日不超量、时间段不重叠、不占用午饭/晚饭、单元不重复排。预览输出到 `app/build/scheduler_preview.txt`。
- 发现问题 1：数学每天占 2 个单元，政治和六级一直排不上。修正：分两轮挑选（第一轮每科最多 1 个，第二轮有空余才允许第 2 个），加上"连续 N 天未排到"的加权（×(1+0.6N)）。现在各科会轮转。
- 发现问题 2：长任务会跨进晚饭时段。修正：toSlots 改为"优先挑能在下一个饭点前做完的任务，放不下就跳到饭后"。
- `:app:testDebugUnitTest :app:assembleDebug` 全部通过（BUILD SUCCESSFUL）。

### 步骤 5：设备验证（受阻）
- `adb devices` 为空，SDK 中没有系统镜像，不能启动模拟器，所以还没做真机截图验证。
- 下一步：用户用 USB 连接手机（打开 USB 调试）后执行
  `adb install -r planapp/PlanApp-debug.apk` → `adb shell am start -n com.planapp.study/.MainActivity` → `adb exec-out screencap -p > shot.png` 检查 UI。

### 交付物
- APK：`C:\Users\21671\Desktop\workbody\planapp\PlanApp-debug.apk`（复制自 app/build/outputs/apk/debug/app-debug.apk）
- 在沙盒侧重新构建：执行 `/home/user/mcp/build.sh ":app:assembleDebug"`（需要 JAVA_HOME = PaperlyToolchain JDK17，并加 --offline）

## 6. 待办 / 后续可选
- 真机 UI 验证（Edge-to-edge、深色模式、文本截断）
- 可选：每日提醒通知、桌面小组件、数据导出/备份

### 步骤 6：用户第一轮反馈（2026-09-28）
- 用户回答：USB 连接真机 d8a4be7c（Redmi K60 / 23013RK75C）；考数学一；只考六级；新增每日提醒。
- 已完成：Reminder.kt（AlarmManager 非精确闹钟：早上推送计划、晚上提醒打卡、开机后重设）、POST_NOTIFICATIONS 权限、设置页加提醒开关和测试按钮；模板改成数学一。编译通过。
- 用户装机后反馈：
  1. 首次使用应先做"进度调查表"（比如高数基础已经过完），再由 AI 生成后续计划；
  2. 目标要通用（考公、雅思……都能用），可自定义，由 AI 智能安排，AI 也能自由调整计划；
  3. 每天弹出今日任务表。
- 重构方案（v1.1）：
  - GoalWizard：选/填目标 → 考试日期（可让 AI 查）→ 填当前进度（自由文本）和补充说明 → AI 一次性生成科目+单元，根据进度预先勾选已完成项 → "进度调查表"复核页（勾选、"到此都已完成"、删改）→ 确认。
  - 没配 AI 时用内置模板兜底（考研/四六级/雅思/考公），或者手动建。
  - Onboarding：欢迎 → AI 配置（可跳过）→ 每日时长 → 进入 GoalWizard。
  - 计划页新增"AI 调整计划"：用户用自然语言提要求，AI 返回操作 JSON（权重、增删单元、每日时长），本地执行。
  - 每天第一次打开 App 弹出"今日任务表"对话框，同时保留早上通知。

### 步骤 7：v1.1 重构实现（完成，versionCode 2 / 1.1.0）
- 新增文件 `ui/GoalWizard.kt`：GoalWizard（目标预设 考研/四级/六级/考公/雅思/托福/教资/自定义 + 任意名称；日期可"AI 查询"；进度自述 + 补充说明；按钮：AI 生成 / 用内置模板 / 手动创建）和 ProgressSurvey（进度调查表：勾选、"到此都完成"、删除、添加）。
- `Onboarding.kt` 重写：每日时长、开始时间、AI 配置 → 进入 GoalWizard（没有目标时不能进主界面）。
- `AiClient.kt` 新增：suggestDate / buildGoal（一次调用生成 2-6 科、带 done 标记，max_tokens 3500）/ adjust（自然语言 → 操作 JSON：weights/add/remove/done/daily_minutes）。
- `AppViewModel.kt` 新增：saveBasics、addGoalFull、aiSuggestDate、aiBuildGoal、aiAdjust、showDailyPopup/dismissPopup（AppState.popupDate）、wizardOpen、resetAll。
- `Templates.kt` 新增雅思、考公模板，以及 presets/byName。
- `App.kt`：打开向导时整页覆盖；每天首次打开弹出 DailyPopup 今日任务表。
- `PlanScreen.kt`：新增 AiAdjustCard。`ProgressScreen.kt`：底部改为"＋ 新建学习目标（AI 按进度生成）"。
- `SettingsScreen.kt`：新增"重新开始（清空目标和打卡记录）"，带二次确认。
- 编译、单元测试通过；`adb install -r` 成功（保留了用户原有数据）。
- 真机截图：手机锁屏（mCurrentFocus=NotificationShade），截图是黑屏，没法验证 UI。
- 坑：run_command 里用 `sleep` 会被 bridge 判为 failed 并中断后续命令，需要等待时拆成多次调用。
- 下一步：用户解锁手机 →（经用户同意后）清空数据，或者到设置里"重新开始"→ 走一遍新向导 → 截图验证。截图可以用 `base64 -w0 shot.png > shot.b64` 再 read_files 传回沙盒查看。

### 步骤 8：本轮结束
- 用户明确回复"结束"。
- 最终 APK：`C:\Users\21671\Desktop\workbody\planapp\PlanApp-debug.apk`（v1.1.0，versionCode 2）。
- 遗留：新向导的真机截图验证；可选功能（桌面小组件、统计图表、数据备份）用户没有选。

