package com.amiao.daily_check

import android.app.Application
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.io.File

/** Runs entirely in isolated test files and preferences, never in the user's data store. */
class NativeSmokeInstrumentation : Instrumentation() {
    private fun descendants(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun dialog(activity: Activity): Dialog = MainActivity::class.java.getDeclaredField("openDialog").run { isAccessible=true; get(activity) as Dialog }
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
            val old = org.json.JSONObject(HabitCodec.encode(listOf(h)))
            old.put("version",1); old.getJSONArray("habits").getJSONObject(0).remove("goalDescription")
            val legacy = old.toString()
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
            val textGoal=h.copy(goal=null,goalDescription="听懂英文播客，学完这门课程！")
            repo.save(listOf(textGoal))
            check(repo.load().single()==textGoal)
            check(repo.load().single().goal==null)
            file.writeText("corrupt")
            check(runCatching { repo.load() }.isFailure)
            check(file.readText() == "corrupt")
            val activity = startActivitySync(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync {
                val views=descendants(activity.window.decorView)
                listOf("今天","目标","记录").forEach { label -> check(views.any { it.contentDescription=="导航：$label" }) }
                views.first { it.contentDescription=="导航：目标" }.performClick()
                descendants(activity.window.decorView).first { it.contentDescription=="新建任务" }.performClick()
                val editor=dialog(activity)
                val fields=descendants(editor.window!!.decorView).filterIsInstance<EditText>()
                check(fields.size==5)
                fields.first { it.contentDescription=="目标（选填）" }.setText("学完英语听力课程！")
                fields.first { it.contentDescription=="习惯名称" }.setText("屏幕重建后保留的草稿")
            }
            waitForIdleSync()
            val restored=AtomicReference<Activity>()
            val resumed=CountDownLatch(1)
            val app=context.applicationContext as Application
            val callback=object: Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a: Activity) { if(a is MainActivity && a!==activity) { restored.set(a); resumed.countDown() } }
                override fun onActivityCreated(a: Activity,b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity,b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            }
            app.registerActivityLifecycleCallbacks(callback)
            try {
                runOnMainSync { activity.recreate() }
                check(resumed.await(8,TimeUnit.SECONDS)) { "Activity recreation timed out" }
                waitForIdleSync()
                runOnMainSync {
                    val editor=dialog(restored.get())
                    val fields=descendants(editor.window!!.decorView).filterIsInstance<EditText>()
                    check(fields.first { it.contentDescription=="习惯名称" }.text.toString()=="屏幕重建后保留的草稿")
                    check(fields.first { it.contentDescription=="目标（选填）" }.text.toString()=="学完英语听力课程！")
                    val isolatedUiRepo=HabitRepository(context,"ui-text-goal-test.json","ui-text-goal-legacy")
                    MainActivity::class.java.getDeclaredField("repository").apply { isAccessible=true }.set(restored.get(),isolatedUiRepo)
                    descendants(editor.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString()=="创建习惯" }.performClick()
                    check(!editor.isShowing) { "A text-only goal must be creatable without a numeric target" }
                    val created=isolatedUiRepo.load().first { it.title=="屏幕重建后保留的草稿" }
                    check(created.goalDescription=="学完英语听力课程！")
                    check(created.goal==null)
                    check(HabitRepository(context,"ui-text-goal-test.json","ui-text-goal-legacy").load().contains(created))
                }
            } finally { app.unregisterActivityLifecycleCallbacks(callback) }
            result.putString("stream", "PASS: text-only habit creation through real editor, text-only goal persistence, text-goal draft restoration, redesigned navigation, bottom-sheet editor, draft restoration, native activity launch, legacy migration, decimals, Chinese notes, archive state, durable reload, legacy preserved, corrupt-data protection\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            file.delete(); File(file.path + ".bak").delete(); File(file.path + ".new").delete(); prefs.edit().clear().commit()
            listOf("ui-text-goal-test.json","ui-text-goal-test.json.bak","ui-text-goal-test.json.new").forEach { File(context.filesDir,it).delete() }
        }
    }
}
