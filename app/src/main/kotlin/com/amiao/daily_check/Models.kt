package com.amiao.daily_check

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object Days {
    private fun format() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    fun today(): String = format().format(Date())
    fun parse(value: String): Date {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "日期格式无效" }
        val date = requireNotNull(format().parse(value))
        require(format().format(date) == value) { "日期无效" }
        return date
    }
    fun shift(value: String, offset: Int): String = Calendar.getInstance().run {
        time = parse(value); add(Calendar.DAY_OF_MONTH, offset); format().format(time)
    }
    fun label(value: String): String = Calendar.getInstance().run {
        time = parse(value)
        "${get(Calendar.MONTH) + 1}月${get(Calendar.DAY_OF_MONTH)}日 · ${arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[get(Calendar.DAY_OF_WEEK) - 1]}" + if (value == today()) " · 今天" else ""
    }
}
fun number(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
data class DailyEntry(val amount: Double, val note: String)
data class Habit(
    val id: String = UUID.randomUUID().toString(), val title: String, val unit: String = "次",
    val dailyTarget: Double = 1.0, val goal: Double = 30.0, val color: Int = 0,
    val archived: Boolean = false, val entries: MutableMap<String, DailyEntry> = linkedMapOf()
) {
    val total get() = entries.values.sumOf { it.amount }
    fun done(day: String) = (entries[day]?.amount ?: 0.0) >= dailyTarget
    fun streak(today: String): Int {
        var day = if (done(today)) today else Days.shift(today, -1)
        var count = 0
        while (done(day)) { count++; day = Days.shift(day, -1) }
        return count
    }
}
object HabitCodec {
    fun encode(habits: List<Habit>): String = JSONObject().put("version", 1).put("habits", JSONArray().apply {
        habits.forEach { h -> put(JSONObject().put("id", h.id).put("title", h.title).put("unit", h.unit)
            .put("dailyTarget", h.dailyTarget).put("goal", h.goal).put("color", h.color)
            .put("archived", h.archived).put("entries", JSONObject().apply {
                h.entries.forEach { (date, entry) -> put(date, JSONObject().put("amount", entry.amount).put("note", entry.note)) }
            })) }
    }).toString()
    fun decode(raw: String): MutableList<Habit> {
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "不支持的备份版本" }
        val array = json.getJSONArray("habits")
        val habits = (0 until array.length()).map { index ->
            val h = array.getJSONObject(index)
            val daily = h.getDouble("dailyTarget"); val goal = h.getDouble("goal")
            require(daily.isFinite() && daily > 0 && goal.isFinite() && goal > 0)
            val title = h.getString("title"); val unit = h.getString("unit"); val id = h.getString("id")
            require(title.isNotBlank() && unit.isNotBlank() && id.isNotBlank())
            val entries = linkedMapOf<String, DailyEntry>()
            val records = h.getJSONObject("entries")
            records.keys().forEach { key ->
                Days.parse(key)
                val record = records.getJSONObject(key); val amount = record.getDouble("amount")
                require(amount.isFinite() && amount >= 0)
                entries[key] = DailyEntry(amount, record.getString("note"))
            }
            Habit(id, title, unit, daily, goal, h.getInt("color").coerceIn(0, 4), h.getBoolean("archived"), entries)
        }
        require(habits.map { it.id }.toSet().size == habits.size) { "重复的任务" }
        return habits.toMutableList()
    }
}
