package com.amiao.daily_check

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File

/** Runs entirely in isolated test files and preferences, never in the user's data store. */
class NativeSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        val context = targetContext
        val name = "migration-smoke-test.json"
        val file = File(context.filesDir, name)
        val prefs = context.getSharedPreferences("migration-smoke-legacy", 0)
        try {
            file.delete(); File(file.path + ".bak").delete()
            val h = Habit(id = "legacy", title = "中文迁移测试", unit = "页", dailyTarget = 20.0, goal = 600.0, archived = true)
            h.entries["2026-11-16"] = DailyEntry(25.25, "原生版本保留旧版文字")
            val legacy = HabitCodec.encode(listOf(h))
            check(prefs.edit().putString("flutter.daily_check_v1", legacy).commit())
            val repo = HabitRepository(context, name, "migration-smoke-legacy")
            check(repo.load().single() == h)
            check(file.exists())
            check(prefs.getString("flutter.daily_check_v1", null) == legacy)
            val migrated = repo.load()
            migrated.single().entries["2026-11-15"] = DailyEntry(0.0, "只写记录")
            repo.save(migrated)
            check(HabitRepository(context, name, "migration-smoke-legacy").load().single().entries.size == 2)
            // Native data is authoritative; legacy preferences must never overwrite new entries.
            check(repo.load().single().total == 25.25)
            file.writeText("corrupt")
            check(runCatching { repo.load() }.isFailure)
            check(file.readText() == "corrupt")
            val activity = startActivitySync(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync { check(activity.findViewById<android.view.ViewGroup>(android.R.id.content).childCount > 0) }
            result.putString("stream", "PASS: native activity launch, legacy migration, decimals, Chinese notes, archive state, durable reload, legacy preserved, corrupt-data protection\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            file.delete(); File(file.path + ".bak").delete(); File(file.path + ".new").delete(); prefs.edit().clear().commit()
        }
    }
}
