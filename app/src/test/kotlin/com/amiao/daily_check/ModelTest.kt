package com.amiao.daily_check

import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun legacyBackupKeepsQuantitiesNotesAndArchiveState() {
        val raw = """{"version":1,"habits":[{"id":"old","title":"阅读","unit":"页","dailyTarget":20.0,"goal":600.0,"color":1,"archived":true,"entries":{"2026-11-16":{"amount":25.25,"note":"读完第一章"},"2026-11-15":{"amount":0,"note":"纯文字记录"}}}]}"""
        val h = HabitCodec.decode(raw).single()
        assertEquals(25.25, h.total, 0.0)
        assertTrue(h.archived)
        assertEquals("读完第一章", h.entries["2026-11-16"]?.note)
        assertEquals(h, HabitCodec.decode(HabitCodec.encode(listOf(h))).single())
        assertEquals("25.25", number(h.total))
    }
    @Test fun streakCrossesMonthAndHandlesIncompleteToday() {
        val h = Habit(title = "运动")
        h.entries["2026-10-31"] = DailyEntry(1.0, "")
        h.entries["2026-11-01"] = DailyEntry(1.0, "")
        h.entries["2026-11-02"] = DailyEntry(.5, "")
        assertEquals(2, h.streak("2026-11-02"))
        assertEquals(0, h.streak("2026-11-03"))
        assertEquals("2026-11-01", Days.shift("2026-10-31", 1))
    }
    @Test fun rejectsBadBackupsBeforeReplacingData() {
        val valid = HabitCodec.encode(listOf(Habit(id = "one", title = "阅读")))
        listOf(valid.replace("\"version\":2", "\"version\":99"), valid.replace("\"dailyTarget\":1", "\"dailyTarget\":-1"), "{}", "not json").forEach { raw ->
            assertThrows(Exception::class.java) { HabitCodec.decode(raw) }
        }
        assertThrows(Exception::class.java) { HabitCodec.decode(HabitCodec.encode(listOf(Habit(id = "same", title = "a"), Habit(id = "same", title = "b")))) }
        assertThrows(Exception::class.java) { Days.parse("2026-02-30") }
        assertThrows(Exception::class.java) { Days.parse("2026-1-1") }
    }
    @Test fun noteOnlyRecordDoesNotCountAsCompleted() {
        val h = Habit(title = "日记")
        h.entries["2026-11-16"] = DailyEntry(0.0, "今天心情很好")
        assertFalse(h.done("2026-11-16"))
        assertEquals(0.0, h.total, 0.0)
    }
    @Test fun textOnlyGoalSurvivesBackupWithoutAnInventedQuantity() {
        val h = Habit(title = "英语听力学习", unit = "集", dailyTarget = 2.0,
            goalDescription = "听懂英文播客，学完这门课程！")
        val restored = HabitCodec.decode(HabitCodec.encode(listOf(h))).single()
        assertNull(restored.goal)
        assertEquals(h.goalDescription, restored.goalDescription)
        assertEquals(h, restored)
    }
    @Test fun textAndQuantityTargetsCanBeUsedTogether() {
        val h = Habit(title = "阅读", unit = "页", goal = 600.5, goalDescription = "读完一本书")
        assertEquals(h, HabitCodec.decode(HabitCodec.encode(listOf(h))).single())
    }
    @Test fun optionalQuantityStillRejectsInvalidValues() {
        val h = Habit(title = "阅读", goal = -1.0, goalDescription = "读完一本书")
        assertThrows(Exception::class.java) { HabitCodec.decode(HabitCodec.encode(listOf(h))) }
    }

}
