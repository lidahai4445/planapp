package com.planapp.study

import android.content.Context
import java.io.File

/** 本地持久化：单个 JSON 文件，原子写入。 */
class Store(context: Context) {
    private val file = File(context.filesDir, "planapp_state.json")
    fun load(): AppState? = runCatching { if (file.exists()) Json.state(file.readText()) else null }.getOrNull()
    fun save(s: AppState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.state(s))
        tmp.renameTo(file) || run { file.writeText(tmp.readText()); tmp.delete() }
    }
}

