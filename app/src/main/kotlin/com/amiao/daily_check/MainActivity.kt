package com.amiao.daily_check

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import java.util.Calendar

class MainActivity : Activity() {
    private val ink = Color.rgb(32, 63, 53)
    private val accent = Color.rgb(51, 117, 91)
    private val paper = Color.rgb(246, 247, 242)
    private val muted = Color.rgb(106, 119, 111)
    private val colors = intArrayOf(accent, 0xffb57840.toInt(), 0xff627eab.toInt(), 0xff9b6b86.toInt(), 0xff728447.toInt())
    private lateinit var repository: HabitRepository
    private var habits = mutableListOf<Habit>()
    private var loadFailed = false
    private var selected = Days.today()
    private var lastToday = Days.today()
    private var tab = 0
    private var currentScroll: ScrollView? = null
    private var restoredScroll = 0
    private var editorState: (() -> Bundle)? = null
    private var openDialog: AlertDialog? = null
    private val active get() = habits.filterNot { it.archived }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        repository = HabitRepository(this)
        selected = state?.getString("date") ?: selected
        tab = state?.getInt("tab") ?: 0
        restoredScroll = state?.getInt("scroll") ?: 0
        lastToday = state?.getString("lastToday") ?: lastToday
        readData()
        state?.getBundle("editor")?.let { draft ->
            if (!loadFailed) {
                val h = habits.find { it.id == draft.getString("id") }
                if (draft.getString("type") == "habit") editHabit(h, draft)
                else if (h != null) editEntry(h, draft.getString("date") ?: selected, draft)
            }
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("date", selected); out.putInt("tab", tab); out.putString("lastToday", lastToday)
        editorState?.invoke()?.let { out.putBundle("editor", it) }
        out.putInt("scroll", currentScroll?.scrollY ?: 0)
        super.onSaveInstanceState(out)
    }
    override fun onResume() {
        super.onResume()
        val today = Days.today()
        if (today != lastToday && selected == lastToday) { selected = today; render() }
        lastToday = today
    }
    private fun readData() {
        try { habits = repository.load(); loadFailed = false }
        catch (_: Exception) { loadFailed = true }
        render()
    }
    private fun change(action: (MutableList<Habit>) -> Unit): Boolean {
        return try {
            val next = HabitCodec.decode(HabitCodec.encode(habits))
            action(next); repository.save(next); habits = next; render(true); true
        } catch (_: Exception) { message("保存失败，请重试。本次更改尚未保存。"); false }
    }
    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun column(padding: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(value: String, filled: Boolean = false, color: Int = accent, click: () -> Unit) = Button(this).apply {
        text = value; textSize = 14f; isAllCaps = false; stateListAnimator = null; elevation = 0f; setTextColor(if (filled) Color.WHITE else color)
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = RippleDrawable(ColorStateList.valueOf(0x2233755b), shape(if (filled) color else 0xffeaf0e6.toInt(), 14), null)
        setOnClickListener { click() }
    }
    private fun LinearLayout.line(view: View, top: Int = 0) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun gap(parent: LinearLayout, size: Int = 12) = parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size)))
    private fun card(parent: LinearLayout, block: (LinearLayout) -> Unit) {
        val card = column(18).apply { background = shape(Color.WHITE) }
        block(card); parent.line(card, 12)
    }
    private fun heading(parent: LinearLayout, title: String, subtitle: String) {
        parent.line(text(title, 28f, bold = true), 8); parent.line(text(subtitle, color = muted), 8); gap(parent, 12)
    }
    private fun render(preserveScroll: Boolean = false) {
        val position = if (preserveScroll) currentScroll?.scrollY ?: 0 else restoredScroll
        restoredScroll = 0
        val root = column().apply { setBackgroundColor(paper) }
        // Consume system bars and keyboard insets so Android 15/16 cannot cover controls.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
            }

        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        val header = row().apply { setPadding(dp(20), dp(10), dp(12), dp(10)) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.app_icon); contentDescription = "日积" }
        header.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)))
        val brand = column().apply { setPadding(dp(10), 0, 0, 0); line(text("日积", 22f, bold = true)); line(text("把每一天，过成积累", 11f, muted)) }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("⋮") { dataMenu() }.apply { contentDescription = "数据备份与恢复" }, LinearLayout.LayoutParams(dp(48), -2))
        root.line(header)
        val body = column(20)
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        currentScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (loadFailed) {
            heading(body, "本地数据暂时无法读取", "请重试，或通过右上角菜单恢复备份。原始数据不会被覆盖。")
            body.line(button("重试读取", true) { readData() })
        } else {
            when (tab) { 0 -> todayView(body); 1 -> goalsView(body); else -> historyView(body) }
            val footer = column(10)
            footer.line(button("＋ 新建任务", true, ink) { editHabit() }.apply { contentDescription = "新建任务" })
            root.line(footer)
        }
        val nav = row().apply { setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.WHITE) }
        listOf("每日", "目标", "记录").forEachIndexed { i, label ->
            nav.addView(button(label, i == tab, if (i == tab) ink else muted) { tab = i; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        root.line(nav); setContentView(root); root.requestApplyInsets()
        scroll.post { scroll.scrollTo(0, position) }
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
    }
    private fun dateBar(parent: LinearLayout) {
        val bar = row()
        bar.addView(button("‹") { selected = Days.shift(selected, -1); render() }.apply { contentDescription = "前一天"; isEnabled = selected > "2000-01-01" }, LinearLayout.LayoutParams(dp(44), -2))
        bar.addView(button(Days.label(selected)) { chooseDate() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        bar.addView(button("›") { selected = Days.shift(selected, 1); render() }.apply { contentDescription = "后一天"; isEnabled = selected < Days.today() }, LinearLayout.LayoutParams(dp(44), -2))
        parent.line(bar, 12)
    }
    private fun chooseDate() {
        val c = Calendar.getInstance().apply { time = Days.parse(selected) }
        DatePickerDialog(this, { _, y, m, d -> selected = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d); render() }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = Days.parse("2000-01-01").time; datePicker.maxDate = System.currentTimeMillis(); show()
        }
    }
    private fun progress(parent: LinearLayout, amount: Double, goal: Double, color: Int) {
        parent.line(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = ((amount / goal).coerceIn(0.0, 1.0) * 1000).toInt()
            progressTintList = ColorStateList.valueOf(color); progressBackgroundTintList = ColorStateList.valueOf(0xffeaf0e6.toInt())
        }, 10)
    }
    private fun todayView(parent: LinearLayout) {
        heading(parent, "小小行动，慢慢生长。", "专注眼前的一步，让改变有迹可循。")
        val summary = column(24).apply { background = shape(ink, 26) }
        val count = active.count { it.done(selected) }
        summary.line(text("当日完成", 14f, 0xffcaddc8.toInt()))
        summary.line(text("$count / ${active.size}", 38f, Color.WHITE, true), 8)
        summary.line(text(if (active.isEmpty()) "从一个想坚持的小习惯开始" else if (count == active.size) "今日目标已完成，给自己一点掌声。" else "每一次行动，都在靠近目标。", 13f, 0xffcaddc8.toInt()), 8)
        parent.line(summary); dateBar(parent); parent.line(text("每日任务 · ${active.size} 个进行中", 20f, bold = true), 20)
        if (active.isEmpty()) empty(parent, "种下你的第一个小目标", "每天阅读 20 页，或记录一天的心情。")
        active.forEach { habit -> taskCard(parent, habit) }
    }
    private fun taskHeader(card: LinearLayout, habit: Habit) {
        val head = row()
        head.addView(text(habit.title + if (habit.archived) " · 已归档" else "", 18f, colors[habit.color], true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(button("⋮") { taskMenu(habit) }.apply { contentDescription = "${habit.title}的任务选项" }, LinearLayout.LayoutParams(dp(44), -2))
        card.line(head)
    }
    private fun taskCard(parent: LinearLayout, habit: Habit) = card(parent) { card ->
        val entry = habit.entries[selected]; val done = habit.done(selected)
        taskHeader(card, habit)
        card.line(text("每日 ${number(habit.dailyTarget)} ${habit.unit}", 13f, muted), 4)
        progress(card, entry?.amount ?: 0.0, habit.dailyTarget, colors[habit.color])
        card.line(text("${number(entry?.amount ?: 0.0)} / ${number(habit.dailyTarget)} ${habit.unit}", 17f, colors[habit.color], true), 8)
        val actions = row()
        actions.addView(button(if (entry == null) "写记录" else "编辑记录") { editEntry(habit) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(if (done) "✓ 已完成" else "打卡", !done, colors[habit.color]) {
            if (done) editEntry(habit) else {
                val date = selected
                if (change { next -> next.first { it.id == habit.id }.entries[date] = DailyEntry(habit.dailyTarget, entry?.note ?: "") }) message("已完成「${habit.title}」")
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        card.line(actions, 12)
        if (!entry?.note.isNullOrBlank()) card.line(text(entry!!.note, 15f, muted), 12)
    }
    private fun goalsView(parent: LinearLayout) {
        heading(parent, "把期待，变成进度。", "累计每一天的努力，走向自己设定的目标。")
        if (habits.isEmpty()) empty(parent, "还没有目标", "创建任务时，可以设定自己的累计目标。")
        habits.forEach { h -> card(parent) { card ->
            taskHeader(card, h); card.line(text("${number(h.total)} / ${number(h.goal)} ${h.unit}", 26f, bold = true), 10)
            progress(card, h.total, h.goal, colors[h.color])
            card.line(text(if (h.total >= h.goal) "目标达成！你已经走了很远。" else "已达成 ${(h.total / h.goal * 100).toInt()}% · 当前连续达标 ${h.streak(Days.today())} 天", 13f, muted), 10)
            if (h.archived) card.line(text("归档后保留历史，可从任务菜单恢复。", 12f, muted), 6)
        } }
    }
    private fun historyView(parent: LinearLayout) {
        val days = habits.flatMap { it.entries.filter { e -> e.value.amount > 0 || e.value.note.isNotEmpty() }.keys }.toSet()
        heading(parent, "走过的路，都算数。", "共留下 ${days.size} 天记录。点击日期，回看自己的积累。")
        dateBar(parent)
        card(parent) { calendar ->
            val c = Calendar.getInstance().apply { time = Days.parse(selected); set(Calendar.DAY_OF_MONTH, 1) }
            val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val count = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            val prefix = selected.substring(0, 7)
            val grid = GridLayout(this).apply { columnCount = 7 }
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day -> grid.addView(text(day, 13f, muted).apply { gravity = Gravity.CENTER }, GridLayout.LayoutParams().apply { width = 0; height = dp(36); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) }) }
            repeat(offset + count) { index ->
                val day = index - offset + 1
                val view = if (day < 1) View(this) else {
                    val date = "$prefix-${day.toString().padStart(2, '0')}"
                    button("$day${if (date in days) "•" else ""}", date == selected, ink) { selected = date; render() }.apply {
                        textSize = 13f; setPadding(0, 0, 0, 0); contentDescription = "$date${if (date in days) " 有记录" else ""}"; isEnabled = date <= Days.today()
                        if (date != selected && date !in days) background = shape(Color.TRANSPARENT, 12)
                        if (!isEnabled) alpha = 0.35f
                    }
                }
                grid.addView(view, GridLayout.LayoutParams().apply { width = 0; height = dp(44); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(1), dp(2), dp(1), dp(2)) })
            }
            calendar.line(grid)
        }
        parent.line(text("${Days.label(selected)}的记录", 19f, bold = true), 20)
        val recorded = habits.filter { it.entries.containsKey(selected) }
        if (recorded.isEmpty()) empty(parent, "这一天，还没有记录", "到「每日」页打卡，或为这一天写点什么。")
        recorded.forEach { h -> card(parent) { card ->
            taskHeader(card, h)
            val e = h.entries.getValue(selected)
            card.line(text("${number(e.amount)} ${h.unit}${if (h.done(selected)) " · 已达标" else ""}", 16f, colors[h.color]), 6)
            if (e.note.isNotEmpty()) card.line(text(e.note, color = muted), 8)
            card.line(button("编辑记录") { editEntry(h) }, 12)
        } }
    }
    private fun empty(parent: LinearLayout, title: String, detail: String) {
        val box = column(24)
        box.line(text(title, 18f, bold = true).apply { gravity = Gravity.CENTER })
        box.line(text(detail, 14f, muted).apply { gravity = Gravity.CENTER }, 12); parent.line(box, 20)
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消", null).setPositiveButton("确认") { _, _ -> action() }.show()
    }
    private fun taskMenu(h: Habit) {
        val labels = mutableListOf("编辑任务", if (h.archived) "恢复任务" else "归档任务")
        if (h.entries.containsKey(selected)) labels.add("清除当天记录")
        labels.add("删除任务")
        AlertDialog.Builder(this).setTitle(h.title).setItems(labels.toTypedArray()) { _, index ->
            when (labels[index]) {
                "编辑任务" -> editHabit(h)
                "归档任务", "恢复任务" -> change { list -> val i = list.indexOfFirst { it.id == h.id }; list[i] = list[i].copy(archived = !h.archived) }
                "清除当天记录" -> { val date = selected; confirm("清除当天记录？", "$date 的数量和文字将被删除。") { change { list -> list.first { it.id == h.id }.entries.remove(date) } } }
                "删除任务" -> confirm("删除任务？", "「${h.title}」和全部历史记录将被删除，无法撤销。") { change { it.removeAll { habit -> habit.id == h.id } } }
            }
        }.show()
    }
    private fun field(parent: LinearLayout, label: String, value: String, numeric: Boolean = false, max: Int = 0, lines: Int = 1): EditText {
        parent.line(text(label, 13f, muted), 12)
        val edit = EditText(this).apply {
            id = View.generateViewId(); hint = label; contentDescription = label; setText(value); textSize = 16f; setTextColor(ink)
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = shape(0xfff0f3ed.toInt(), 12)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or if (lines > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (max > 0) filters = arrayOf(InputFilter.LengthFilter(max))
            minLines = lines; if (lines == 1) setSingleLine(true) else gravity = Gravity.TOP
        }
        parent.line(edit, 6); return edit
    }
    private fun validNumber(edit: EditText, zero: Boolean = false): Double? {
        val n = edit.text.toString().toDoubleOrNull()
        if (n == null || !n.isFinite() || n > 1e9 || if (zero) n < 0 else n <= 0) {
            edit.error = if (zero) "请输入 0 到 10 亿之间的数值" else "请输入大于 0、最多 10 亿的数值"; return null
        }
        return n
    }
    private fun editor(title: String, form: LinearLayout, save: () -> Unit) {
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        openDialog = dialog
        dialog.setOnDismissListener { if (openDialog === dialog) { openDialog = null; editorState = null } }
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
        }
        dialog.show()
    }
    private fun editHabit(h: Habit? = null, draft: Bundle? = null) {
        val form = column(20)
        if (h != null) form.line(text("修改单位不会换算历史数值；修改每日目标会重新判断历史达标状态。", 12f, muted))
        val title = field(form, "任务名称", draft?.getString("title") ?: h?.title ?: "", max = 40)
        val daily = field(form, "每日目标", draft?.getString("daily") ?: number(h?.dailyTarget ?: 1.0), true)
        val unit = field(form, "单位", draft?.getString("unit") ?: h?.unit ?: "次", max = 8)
        val goal = field(form, "累计目标", draft?.getString("goal") ?: number(h?.goal ?: 30.0), true)
        var color = draft?.getInt("color") ?: h?.color ?: 0
        form.line(text("任务颜色", 13f, muted), 12)
        val colorButtons = row(); val buttons = mutableListOf<Button>()
        colors.forEachIndexed { i, value ->
            val b = button(if (i == color) "✓" else "●", i == color, value) { color = i; buttons.forEachIndexed { index, button -> button.text = if (index == color) "✓" else "●" } }
            b.contentDescription = "样式 ${i + 1}"; buttons.add(b)
            colorButtons.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(3) })
        }
        form.line(colorButtons, 8)
        editor(if (h == null) "种下一个小目标" else "编辑任务", form) {
            if (title.text.isBlank()) { title.error = "请输入任务名称"; return@editor }
            val dailyValue = validNumber(daily) ?: return@editor
            if (unit.text.isBlank()) { unit.error = "请输入单位"; return@editor }
            val goalValue = validNumber(goal) ?: return@editor
            val updated = Habit(h?.id ?: java.util.UUID.randomUUID().toString(), title.text.toString().trim(), unit.text.toString().trim(), dailyValue, goalValue, color, h?.archived ?: false, h?.entries ?: linkedMapOf())
            if (change { list -> val i = list.indexOfFirst { it.id == updated.id }; if (i < 0) list.add(updated) else list[i] = updated }) openDialog?.dismiss()
        }
        editorState = { Bundle().apply { putString("type", "habit"); putString("id", h?.id); putString("title", title.text.toString()); putString("daily", daily.text.toString()); putString("unit", unit.text.toString()); putString("goal", goal.text.toString()); putInt("color", color) } }
    }
    private fun editEntry(h: Habit, date: String = selected, draft: Bundle? = null) {
        val form = column(20); val entry = h.entries[date]
        form.line(text("$date · 每日目标 ${number(h.dailyTarget)} ${h.unit}", 13f, muted))
        val amount = field(form, "当天完成总量（${h.unit}）", draft?.getString("amount") ?: number(entry?.amount ?: 0.0), true)
        form.line(text("填写当天总量；只写日记时可填 0。", 12f, muted), 6)
        val note = field(form, "今天的记录", draft?.getString("note") ?: entry?.note ?: "", max = 2000, lines = 5)
        editor(h.title, form) {
            val value = validNumber(amount, true) ?: return@editor
            val content = note.text.toString().trim()
            if (value == 0.0 && content.isEmpty()) { note.error = "请填写完成数量或文字记录"; return@editor }
            if (change { list -> list.first { it.id == h.id }.entries[date] = DailyEntry(value, content) }) openDialog?.dismiss()
        }
        editorState = { Bundle().apply { putString("type", "entry"); putString("id", h.id); putString("date", date); putString("amount", amount.text.toString()); putString("note", note.text.toString()) } }
    }
    private fun dataMenu() {
        val items = if (loadFailed) arrayOf("粘贴备份恢复") else arrayOf("复制数据备份", "粘贴备份恢复")
        AlertDialog.Builder(this).setTitle("数据备份").setItems(items) { _, index ->
            if (items[index] == "复制数据备份") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("日积数据备份", HabitCodec.encode(habits)))
                message("备份已复制，请粘贴到可信的文件或笔记保存。")
            } else restore()
        }.show()
    }
    private fun restore() {
        val form = column(20)
        val input = field(form, "粘贴完整的 JSON 备份", "", lines = 6)
        editor("恢复备份", form) {
            val next = try { HabitCodec.decode(input.text.toString().trim()) } catch (_: Exception) { input.error = "备份格式无效，当前数据未更改"; return@editor }
            confirm("恢复备份？", "当前数据将被替换为备份中的 ${next.size} 个任务。建议先复制当前备份。") {
                try { repository.save(next); habits = next; loadFailed = false; openDialog?.dismiss(); render(); message("备份已恢复") }
                catch (_: Exception) { message("恢复失败，请重试。") }
            }
        }
    }
}
