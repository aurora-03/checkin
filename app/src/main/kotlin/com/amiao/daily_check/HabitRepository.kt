package com.amiao.daily_check

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Reads Flutter v1 and native v2 goals; the legacy preference file is never modified. */
class HabitRepository(
    private val context: Context,
    fileName: String = "habits-v1.json",
    private val legacyPreferences: String = "FlutterSharedPreferences"
) {
    private val file = AtomicFile(File(context.filesDir, fileName))
    fun load(): MutableList<Habit> {
        // AtomicFile also recovers an interrupted previous write on openRead().
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            return HabitCodec.decode(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        }
        val legacy = context.getSharedPreferences(legacyPreferences, Context.MODE_PRIVATE)
            .getString("flutter.daily_check_v1", null) ?: return mutableListOf()
        val habits = HabitCodec.decode(legacy) // Validate before saving; never silently reset corrupt data.
        save(habits)
        return habits
    }
    fun save(habits: List<Habit>) {
        val raw = HabitCodec.encode(habits)
        HabitCodec.decode(raw)
        val stream = file.startWrite()
        try {
            stream.write(raw.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
