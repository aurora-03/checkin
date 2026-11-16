package com.amiao.daily_check

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private val ink = 0xff202824.toInt()
    private val accent = 0xff2c705b.toInt()
    private val paper = 0xfff7f8f5.toInt()
    private val muted = 0xff6c776f.toInt()
    private val border = 0xffe8ece5.toInt()
    private val colors = intArrayOf(accent, 0xffb27f43.toInt(), 0xff587c9e.toInt(), 0xff9a728f.toInt(), 0xff7b874f.toInt())
    private lateinit var repository: HabitRepository
    private var habits = mutableListOf<Habit>()
    private var loadFailed = false
    private var selected = Days.today()
    private var lastToday = Days.today()
    private var tab = 0
    private var showArchived = false
    private var currentScroll: ScrollView? = null
    private var restoredScroll = 0
    private var editorState: (() -> Bundle)? = null
    private var openDialog: Dialog? = null
    private val active get() = habits.filterNot { it.archived }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        repository = HabitRepository(this)
        selected = state?.getString("date") ?: selected
        tab = state?.getInt("tab") ?: 0
        showArchived = state?.getBoolean("archived") ?: false
        restoredScroll = state?.getInt("scroll") ?: 0
        lastToday = state?.getString("lastToday") ?: lastToday
        readData()
        state?.getBundle("editor")?.let { draft ->
            if (!loadFailed) {
                val h = habits.find { it.id == draft.getString("id") }
                when (draft.getString("type")) {
                    "habit" -> editHabit(h, draft)
                    "entry" -> if (h != null) editEntry(h, draft.getString("date") ?: selected, draft)
                    "restore" -> restore(draft)
                }
            }
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("date", selected); out.putInt("tab", tab); out.putBoolean("archived", showArchived)
        out.putString("lastToday", lastToday); out.putInt("scroll", currentScroll?.scrollY ?: 0)
        editorState?.invoke()?.let { out.putBundle("editor", it) }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() {
        openDialog?.dismiss()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        val today = Days.today()
        if (today != lastToday && selected == lastToday) { selected = today; render() }
        lastToday = today
    }
    private fun readData() {
        try { habits = repository.load(); loadFailed = false } catch (_: Exception) { loadFailed = true }
        render()
    }
    private fun change(action: (MutableList<Habit>) -> Unit): Boolean = try {
        val next = HabitCodec.decode(HabitCodec.encode(habits))
        action(next); repository.save(next); habits = next; render(true); true
    } catch (_: Exception) { message("保存失败，请重试。本次更改尚未保存。"); false }
    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    private fun shape(color: Int, radius: Int = 20, outline: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (outline != Color.TRANSPARENT) setStroke(dp(1), outline)
    }
    private fun column(padding: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1.06f)
    }
    private fun tap(view: View, color: Int = Color.TRANSPARENT, radius: Int = 14, outline: Int = Color.TRANSPARENT, action: () -> Unit) {
        view.background = RippleDrawable(ColorStateList.valueOf(0x142c705b), shape(color, radius, outline), null)
        view.isFocusable = true; view.setOnClickListener { action() }
    }
    private fun icon(kind: String, tint: Int = ink, size: Int = 22) = ImageView(this).apply {
        setImageDrawable(Glyph(kind, tint)); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun iconButton(kind: String, label: String, filled: Boolean = false, size: Int = 44, action: () -> Unit) = ImageButton(this).apply {
        contentDescription = label; setImageDrawable(Glyph(kind, if (filled) Color.WHITE else ink)); scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(11),dp(11),dp(11),dp(11)); minimumWidth=dp(44); minimumHeight=dp(44)
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)); tap(this, if(filled) accent else Color.TRANSPARENT, 15, action=action)
    }
    private fun primary(label: String, action: () -> Unit) = MaterialButton(this).apply {
        text=label; textSize=15f; isAllCaps=false; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        cornerRadius=dp(16); insetTop=0; insetBottom=0; minHeight=dp(52)
        backgroundTintList=ColorStateList.valueOf(accent); setTextColor(Color.WHITE); elevation=0f
        setOnClickListener { action() }
    }
    private fun chip(label: String, chosen: Boolean = false, action: () -> Unit) = text(label,13f,if(chosen) accent else muted,chosen).apply {
        gravity=Gravity.CENTER; setPadding(dp(14),dp(10),dp(14),dp(10)); minHeight=dp(44)
        tap(this,if(chosen) 0xffe8f0e7.toInt() else Color.TRANSPARENT,14,action=action)
    }
    private fun LinearLayout.line(view: View, top: Int = 0) {
        addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) })
    }
    private fun gap(parent: LinearLayout, size: Int = 12) = parent.addView(View(this),LinearLayout.LayoutParams(1,dp(size)))
    private fun card(parent: LinearLayout, padding: Int = 16, block: (LinearLayout) -> Unit): LinearLayout {
        val view=column(padding).apply { background=shape(Color.WHITE,20,border) }
        block(view); parent.line(view,12); return view
    }
    private fun section(parent: LinearLayout, label: String, detail: String = "", top: Int = 22) {
        val r=row(); r.addView(text(label,15f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        r.addView(text(detail,12f,muted)); parent.line(r,top)
    }
    private fun habitIcon(h: Habit): String {
        val name="${h.title} ${h.unit}".lowercase(Locale.ROOT)
        return when {
            listOf("阅读","读书","read","book","页","page").any { it in name } -> "book"
            listOf("运动","跑","walk","run","分钟").any { it in name } -> "activity"
            listOf("喝水","饮水","water","毫升","ml").any { it in name } -> "drop"
            listOf("记录","日记","写","note","journal").any { it in name } -> "note"
            else -> arrayOf("leaf","book","activity","note","sun")[h.color]
        }
    }
    private fun habitTile(h: Habit, size: Int = 40): FrameLayout = FrameLayout(this).apply {
        background=shape(Color.argb(20,Color.red(colors[h.color]),Color.green(colors[h.color]),Color.blue(colors[h.color])),13)
        addView(icon(habitIcon(h),colors[h.color]),FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER))
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
    }

    private fun render(preserveScroll: Boolean = false) {
        val position=if(preserveScroll) currentScroll?.scrollY ?: 0 else restoredScroll; restoredScroll=0
        val root=column().apply { setBackgroundColor(paper) }
        if(Build.VERSION.SDK_INT>=30) {
            @Suppress("DEPRECATION") window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v,insets ->
                val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom); insets
            }
        } else {
            @Suppress("DEPRECATION") window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        val header=row().apply { setPadding(dp(24),dp(16),dp(16),dp(12)) }
        val heading=column(); heading.line(text(if(tab==0) "日积  /  ${selected.substring(5,7).toInt()}月" else "日积",12f,muted))
        heading.line(text(arrayOf(if(selected==Days.today()) "今天" else "每日","目标","记录")[tab],28f,bold=true),6)
        header.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
        if(!loadFailed) header.addView(iconButton("plus","新建任务",true) { editHabit() })
        header.addView(iconButton("settings","设置与数据备份") { dataMenu() },LinearLayout.LayoutParams(dp(44),dp(44)).apply { marginStart=dp(8) })
        root.line(header)
        val body=column().apply { setPadding(dp(24),0,dp(24),dp(24)) }
        val scroll=ScrollView(this).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; addView(body) }
        currentScroll=scroll; root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(loadFailed) {
            empty(body,"本地数据暂时无法读取","请重试，或从设置中恢复备份。原始数据会保留。")
            body.line(primary("重试读取") { readData() },20)
        } else when(tab) { 0->todayView(body); 1->goalsView(body); else->historyView(body) }
        val divider=View(this).apply { setBackgroundColor(border) }; root.addView(divider,LinearLayout.LayoutParams(-1,dp(1)))
        val nav=row().apply { setPadding(dp(24),dp(8),dp(24),dp(6)); setBackgroundColor(Color.WHITE) }
        listOf("今天" to "today","目标" to "target","记录" to "journal").forEachIndexed { i,(label,glyph) ->
            val item=column().apply {
                gravity=Gravity.CENTER; setPadding(0,dp(7),0,dp(5)); contentDescription="导航：$label"
                val icon=icon(glyph,if(tab==i) accent else muted,22); addView(icon)
                line(text(label,11f,if(tab==i) accent else muted,tab==i).apply { gravity=Gravity.CENTER },5)
                tap(this,if(tab==i) 0xffedf3e9.toInt() else Color.TRANSPARENT,16) { tab=i; render() }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(5); marginEnd=dp(5) })
        }
        root.line(nav); setContentView(root); root.requestApplyInsets(); scroll.post { scroll.scrollTo(0,position) }
        if(Build.VERSION.SDK_INT>=30) window.insetsController?.setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
    }

    private fun weekStart(day: String): String {
        val c=Calendar.getInstance().apply { time=Days.parse(day) }
        return Days.shift(day,-((c.get(Calendar.DAY_OF_WEEK)+5)%7))
    }
    private fun weekStrip(parent: LinearLayout) {
        val start=weekStart(selected)
        val bar=row()
        val label=row().apply {
            addView(text("${selected.substring(0,4)}年${selected.substring(5,7).toInt()}月",13f,muted,true))
            addView(icon("down",muted,14),LinearLayout.LayoutParams(dp(14),dp(14)).apply { marginStart=dp(5) })
            contentDescription="选择日期"; setPadding(0,dp(12),dp(12),dp(12)); minimumHeight=dp(44); tap(this,action={chooseDate()})
        }
        bar.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(iconButton("left","上一周") { selected=maxOf("2000-01-01",Days.shift(selected,-7)); render() }.apply { isEnabled=start>"2000-01-01"; if(!isEnabled) alpha=.25f })
        bar.addView(iconButton("right","下一周") { selected=minOf(Days.today(),Days.shift(selected,7)); render() }.apply { isEnabled=start<weekStart(Days.today()); if(!isEnabled) alpha=.25f })
        parent.line(bar)
        val strip=row()
        repeat(7) { index ->
            val day=Days.shift(start,index); val chosen=day==selected; val future=day>Days.today()
            val cell=column().apply {
                gravity=Gravity.CENTER; setPadding(0,dp(9),0,dp(8)); contentDescription="选择 $day"; minimumHeight=dp(66)
                addView(text(arrayOf("一","二","三","四","五","六","日")[index],10f,if(chosen) 0xffd7e6da.toInt() else muted).apply { gravity=Gravity.CENTER })
                addView(text(day.takeLast(2).toInt().toString(),18f,if(chosen) Color.WHITE else ink,chosen),LinearLayout.LayoutParams(-2,-2).apply { topMargin=dp(7) })
                val recorded=habits.any { it.entries.containsKey(day) }
                addView(View(this@MainActivity).apply { background=shape(if(recorded) if(chosen) Color.WHITE else accent else Color.TRANSPARENT,2) },LinearLayout.LayoutParams(dp(3),dp(3)).apply { topMargin=dp(6) })
                if(!future && day>="2000-01-01") tap(this,if(chosen) accent else Color.TRANSPARENT,16) { selected=day; render() } else { alpha=.3f; isEnabled=false }
            }
            strip.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(2); marginEnd=dp(2) })
        }
        parent.line(strip,2)
    }
    private fun chooseDate() {
        val c=Calendar.getInstance().apply { time=Days.parse(selected) }
        DatePickerDialog(this,{_,y,m,d -> selected=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d); render()},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate=Days.parse("2000-01-01").time; datePicker.maxDate=System.currentTimeMillis(); show()
        }
    }
    private fun todayView(parent: LinearLayout) {
        weekStrip(parent)
        if(active.isEmpty()) {
            empty(parent,"从一个小习惯开始","阅读几页书，散一会儿步。\n给想坚持的事，留一个位置。")
            parent.line(primary("创建第一个习惯") { editHabit() },24); return
        }
        val complete=active.count { it.done(selected) }
        val summary=row().apply { setPadding(dp(18),dp(16),dp(18),dp(16)); background=shape(0xffedf3e9.toInt(),20) }
        val detail=column()
        detail.line(text("当日进度",11f,accent))
        val numbers=row().apply { gravity=Gravity.BOTTOM }
        numbers.addView(text("$complete",27f,bold=true))
        numbers.addView(text(" / ${active.size}",15f,muted),LinearLayout.LayoutParams(-2,-2).apply { bottomMargin=dp(2) })
        detail.line(numbers,5)
        summary.addView(detail,LinearLayout.LayoutParams(0,-2,1f))
        summary.addView(text(if(complete==active.size) "全部完成" else "还有 ${active.size-complete} 个",11f,accent),LinearLayout.LayoutParams(-2,-2).apply { marginEnd=dp(14) })
        summary.addView(RingView(this@MainActivity,complete.toFloat()/active.size,accent,complete==active.size),LinearLayout.LayoutParams(dp(43),dp(43)))
        parent.line(summary,18)
        val pending=active.filterNot { it.done(selected) }; val done=active.filter { it.done(selected) }
        if(pending.isNotEmpty()) { section(parent,"待完成", "${pending.size} 个"); pending.forEach { taskCard(parent,it) } }
        if(done.isNotEmpty()) { section(parent,"已完成", "${done.size} 个"); done.forEach { taskCard(parent,it) } }
    }
    private fun quickCheck(h: Habit) {
        if(h.done(selected)) { editEntry(h); return }
        val date=selected
        if(change { list -> val habit=list.first { it.id==h.id }; habit.entries[date]=DailyEntry(h.dailyTarget,habit.entries[date]?.note ?: "") }) {
            window.decorView.performHapticFeedback(if(Build.VERSION.SDK_INT>=30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    private fun taskCard(parent: LinearLayout,h: Habit) {
        val e=h.entries[selected]; val done=h.done(selected)
        val box=card(parent) { card ->
            val line=row(); line.addView(habitTile(h))
            val words=column().apply { setPadding(dp(12),0,dp(8),0) }
            words.line(text(h.title,16f,if(done) 0xff5e6e62.toInt() else ink,true).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END })
            val amount=if(e==null) "每天 ${number(h.dailyTarget)} ${h.unit}" else "${number(e.amount)} / ${number(h.dailyTarget)} ${h.unit}"
            words.line(text(amount,12f,muted),6)
            line.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            val control=FrameLayout(this).apply {
                contentDescription=if(done) "${h.title}已完成，编辑记录" else "打卡：${h.title}"
                if(done) { background=shape(colors[h.color],14); addView(icon("check",Color.WHITE),FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER)) }
                else { addView(RingView(this@MainActivity,((e?.amount ?: 0.0)/h.dailyTarget).toFloat(),colors[h.color]),FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER)) }
                tap(this,if(done) colors[h.color] else Color.TRANSPARENT,14) { quickCheck(h) }
            }
            line.addView(control,LinearLayout.LayoutParams(dp(44),dp(44))); card.line(line)
            if(!e?.note.isNullOrBlank()) {
                val note=row(); note.addView(icon("note",muted,13)); note.addView(text(e!!.note,12f,muted).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(7) })
                card.line(note,12)
            }
        }
        tap(box,Color.WHITE,20,border) { editEntry(h) }; box.contentDescription="任务：${h.title}"
        box.setOnLongClickListener { taskMenu(h); true }
    }
    private fun progress(parent: LinearLayout,fraction: Double,color: Int) {
        val track=FrameLayout(this).apply { background=shape(0xffedf0eb.toInt(),4) }
        val fill=View(this).apply { background=shape(color,4) }
        track.addView(fill,FrameLayout.LayoutParams(0,-1)); track.addOnLayoutChangeListener { _,l,_,r,_,_,_,_,_ -> fill.layoutParams=FrameLayout.LayoutParams(((r-l)*fraction.coerceIn(0.0,1.0)).toInt(),-1) }
        parent.addView(track,LinearLayout.LayoutParams(-1,dp(4)).apply { topMargin=dp(14) })
    }
    private fun goalsView(parent: LinearLayout) {
        val filters=row()
        filters.addView(chip("进行中 ${active.size}",!showArchived) { showArchived=false; render() })
        filters.addView(chip("已归档 ${habits.count { it.archived }}",showArchived) { showArchived=true; render() },LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(6) })
        parent.line(filters,6)
        val visible=habits.filter { it.archived==showArchived }
        if(visible.isEmpty()) { empty(parent,if(showArchived) "这里还没有归档的习惯" else "给坚持设一个方向",if(showArchived) "归档会保留所有历史记录。" else "创建习惯时，也可以设定累计目标。"); return }
        visible.forEach { h -> card(parent,18) { box ->
            val head=row(); head.addView(habitTile(h,36)); head.addView(text(h.title,16f,bold=true).apply { maxLines=2; ellipsize=TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(10) })
            head.addView(iconButton("more","${h.title}的任务选项") { taskMenu(h) }); box.line(head)
            if(h.goalDescription.isNotBlank()) box.line(text(h.goalDescription,14f,ink).apply { setLineSpacing(dp(4).toFloat(),1.1f) },12)
            val target=h.goal
            val total=row().apply { gravity=Gravity.BOTTOM }; total.addView(text(number(h.total),27f,bold=true)); total.addView(text(if(target==null) " ${h.unit} · 累计" else " / ${number(target)} ${h.unit}",13f,muted),LinearLayout.LayoutParams(0,-2,1f).apply { bottomMargin=dp(3) }); box.line(total,16)
            if(target!=null) progress(box,h.total/target,colors[h.color])
            val metrics=row(); metrics.addView(text("连续 ${h.streak(Days.today())} 天",12f,muted),LinearLayout.LayoutParams(0,-2,1f))
            if(target!=null) metrics.addView(text(if(h.total>=target) "数量达标" else "${(h.total/target*100).toInt()}%",12f,colors[h.color],true))
            box.line(metrics,12)
            box.addView(HabitGrid(this,h,colors[h.color]).apply { contentDescription="${h.title}最近28天的完成情况" },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(20) })
            box.line(text("最近 28 天",10f,muted),7)
        } }
    }
    private fun shiftMonth(offset: Int) {
        val c=Calendar.getInstance().apply { time=Days.parse(selected); set(Calendar.DAY_OF_MONTH,1); add(Calendar.MONTH,offset) }
        val day=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(c.time)
        selected=minOf(day,Days.today()); render()
    }
    private fun historyView(parent: LinearLayout) {
        val days=habits.flatMap { it.entries.filter { e -> e.value.amount>0 || e.value.note.isNotBlank() }.keys }.toSet()
        val prefix=selected.substring(0,7)
        card(parent) { calendar ->
            val month=row(); month.addView(text("${selected.substring(0,4)}年${selected.substring(5,7).toInt()}月",17f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
            month.addView(iconButton("left","上一月") { shiftMonth(-1) }.apply { isEnabled=prefix>"2000-01"; if(!isEnabled) alpha=.25f })
            month.addView(iconButton("right","下一月") { shiftMonth(1) }.apply { isEnabled=prefix<Days.today().substring(0,7); if(!isEnabled) alpha=.25f }); calendar.line(month)
            calendar.line(text("本月记录 ${days.count { it.startsWith(prefix) }} 天",11f,muted),4)
            val c=Calendar.getInstance().apply { time=Days.parse(selected); set(Calendar.DAY_OF_MONTH,1) }
            val offset=(c.get(Calendar.DAY_OF_WEEK)+5)%7; val count=c.getActualMaximum(Calendar.DAY_OF_MONTH)
            val grid=GridLayout(this).apply { columnCount=7 }
            listOf("一","二","三","四","五","六","日").forEach { label -> grid.addView(text(label,11f,muted).apply { gravity=Gravity.CENTER },GridLayout.LayoutParams().apply { width=0; height=dp(32); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }) }
            repeat(offset+count) { i ->
                val day=i-offset+1
                val view=if(day<1) View(this) else {
                    val date="$prefix-${day.toString().padStart(2,'0')}"; val chosen=date==selected
                    column().apply {
                        gravity=Gravity.CENTER; contentDescription="日历：$date${if(date in days) "，有记录" else ""}"
                        addView(text("$day",14f,if(chosen) Color.WHITE else ink,chosen).apply { gravity=Gravity.CENTER })
                        addView(View(this@MainActivity).apply { background=shape(if(date in days) if(chosen) Color.WHITE else accent else Color.TRANSPARENT,2) },LinearLayout.LayoutParams(dp(3),dp(3)).apply { topMargin=dp(4) })
                        if(date<=Days.today()) tap(this,if(chosen) accent else Color.TRANSPARENT,13,if(date==Days.today() && !chosen) border else Color.TRANSPARENT) { selected=date; render(true) }
                        else { alpha=.25f; isEnabled=false }
                    }
                }
                grid.addView(view,GridLayout.LayoutParams().apply { width=0; height=dp(40); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) })
            }
            calendar.line(grid,12)
        }
        val recorded=habits.filter { it.entries.containsKey(selected) }
        section(parent,"${selected.substring(5,7).toInt()}月${selected.takeLast(2).toInt()}日", "${recorded.size} 条记录")
        if(recorded.isEmpty()) {
            empty(parent,"这一天，留白也没关系","可以回到每日页补记，或选择其他日期。")
            parent.line(chip("去补记") { tab=0; render() },4)
        }
        recorded.forEach { h ->
            val box=card(parent) { box ->
                val entry=h.entries.getValue(selected); val head=row(); head.addView(habitTile(h,32))
                head.addView(text(h.title,14f,bold=true),LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(10); marginEnd=dp(8) })
                head.addView(text("${number(entry.amount)} ${h.unit}",12f,colors[h.color],true)); box.line(head)
                if(entry.note.isNotBlank()) box.line(text(entry.note,14f,0xff5e6a62.toInt()).apply { setLineSpacing(dp(5).toFloat(),1.1f) },14)
                else box.line(text(if(h.done(selected)) "完成了当天的目标" else "留下了一次行动",12f,muted),12)
            }
            tap(box,Color.WHITE,20,border) { editEntry(h) }; box.contentDescription="编辑${h.title}的记录"
        }
    }
    private fun empty(parent: LinearLayout,title: String,detail: String) {
        val box=column().apply { gravity=Gravity.CENTER; setPadding(dp(12),dp(44),dp(12),dp(22)) }
        val symbol=FrameLayout(this).apply { background=shape(0xffedf3e9.toInt(),24); addView(icon("leaf",accent,27),FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER)) }
        box.addView(symbol,LinearLayout.LayoutParams(dp(64),dp(64))); box.line(text(title,17f,bold=true).apply { gravity=Gravity.CENTER },20)
        box.line(text(detail,13f,muted).apply { gravity=Gravity.CENTER; setLineSpacing(dp(5).toFloat(),1.1f) },10); parent.line(box,12)
    }

    private fun confirm(title: String,detail: String,action: () -> Unit) {
        MaterialAlertDialogBuilder(this).setBackground(shape(Color.WHITE,24)).setTitle(title).setMessage(detail).setNegativeButton("取消",null).setPositiveButton("确认") { _,_->action() }.show()
    }
    private fun showSheet(title: String,content: View,saveLabel: String? = null,save: (() -> Unit)? = null): BottomSheetDialog {
        val dialog=BottomSheetDialog(this)
        val root=column()
        val handle=View(this).apply { background=shape(0xffdbe1da.toInt(),3); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(handle,LinearLayout.LayoutParams(dp(32),dp(4)).apply { gravity=Gravity.CENTER_HORIZONTAL; topMargin=dp(10); bottomMargin=dp(8) })
        val header=row().apply { setPadding(dp(24),dp(4),dp(14),dp(4)) }
        header.addView(text(title,21f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(iconButton("close","关闭面板") { dialog.dismiss() }); root.line(header)
        val scroll=NestedScrollView(this).apply { isFillViewport=false; isVerticalScrollBarEnabled=false; addView(content) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,-2,1f))
        if(saveLabel!=null) {
            val footer=column().apply { setPadding(dp(24),dp(12),dp(24),dp(20)) }
            footer.line(primary(saveLabel) { save?.invoke() }); root.line(footer)
        } else gap(root,20)
        // Keep the focused input and a little following context inside the scroll viewport.
        // The IME can resize the sheet after focus was granted, so focus-time scrolling alone
        // leaves the final goal field below the fixed footer on short windows.
        var lastFocus: View?=null
        var lastVisibleHeight=-1
        var lastInputHeight=-1
        var lastViewportHeight=-1
        fun revealFocusedInput() {
            val focused=dialog.currentFocus as? EditText ?: return
            if(!focused.isShown) return
            val visible=Rect(); root.getWindowVisibleDisplayFrame(visible)
            if(focused===lastFocus && visible.height()==lastVisibleHeight && focused.height==lastInputHeight && scroll.height==lastViewportHeight) return
            lastFocus=focused; lastVisibleHeight=visible.height(); lastInputHeight=focused.height; lastViewportHeight=scroll.height
            focused.post {
                if(dialog.isShowing && focused.hasFocus()) {
                    val context=min(dp(100),maxOf(0,scroll.height-focused.height-dp(8)))
                    focused.requestRectangleOnScreen(Rect(0,0,focused.width,focused.height+context),true)
                }
            }
        }
        fun inputs(view: View): List<EditText> = listOfNotNull(view as? EditText) +
            if(view is ViewGroup) (0 until view.childCount).flatMap { inputs(view.getChildAt(it)) } else emptyList()
        val fields=inputs(content)
        fields.forEach { edit ->
            if(edit.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE==0) {
                edit.setOnEditorActionListener { _,action,_ ->
                    val visible=fields.filter { it.isShown }
                    val next=visible.getOrNull(visible.indexOf(edit)+1)
                    when(action) {
                        EditorInfo.IME_ACTION_NEXT -> { next?.requestFocus(); revealFocusedInput(); true }
                        EditorInfo.IME_ACTION_DONE -> { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(edit.windowToken,0); true }
                        else -> false
                    }
                }
            }
        }
        fun updateInputActions() {
            val visible=fields.filter { it.isShown }
            visible.forEachIndexed { index,edit ->
                if(edit.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE==0) {
                    val next=visible.getOrNull(index+1)
                    val options=(if(next==null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    if(edit.imeOptions!=options) {
                        edit.imeOptions=options
                        if(edit.hasFocus()) (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(edit)
                    }
                    edit.nextFocusForwardId=next?.id ?: View.NO_ID
                    edit.nextFocusDownId=next?.id ?: View.NO_ID
                }
            }
        }
        val layoutListener=ViewTreeObserver.OnGlobalLayoutListener { updateInputActions(); revealFocusedInput() }
        val focusListener=ViewTreeObserver.OnGlobalFocusChangeListener { _,_->revealFocusedInput() }
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.behavior.maxHeight=(resources.displayMetrics.heightPixels*.9f).toInt()
            dialog.behavior.skipCollapsed=true; dialog.behavior.state=BottomSheetBehavior.STATE_EXPANDED
            @Suppress("DEPRECATION") dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setOnDismissListener {
            if(root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                root.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
            }
            if(openDialog===dialog) { openDialog=null; editorState=null }
        }
        openDialog=dialog; dialog.show(); return dialog
    }
    private fun actionItem(parent: LinearLayout,label: String,detail: String,glyph: String,danger: Boolean = false,action: () -> Unit) {
        val item=row().apply { setPadding(dp(24),dp(14),dp(24),dp(14)); minimumHeight=dp(60) }
        item.addView(icon(glyph,if(danger) 0xffb45b54.toInt() else accent))
        val words=column(); words.line(text(label,15f,if(danger) 0xffb45b54.toInt() else ink,true)); if(detail.isNotEmpty()) words.line(text(detail,12f,muted),5)
        item.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(15) }); tap(item) { openDialog?.dismiss(); action() }; parent.line(item)
    }
    private fun taskMenu(h: Habit) {
        val form=column()
        actionItem(form,"编辑习惯","名称、数量、目标与颜色","note") { editHabit(h) }
        actionItem(form,if(h.archived) "恢复习惯" else "归档习惯","保留全部历史记录","archive") { change { list -> val i=list.indexOfFirst { it.id==h.id }; list[i]=list[i].copy(archived=!h.archived) } }
        if(h.entries.containsKey(selected)) { val date=selected; actionItem(form,"清除当天记录",date,"calendar") { confirm("清除当天记录？","$date 的数量和文字将被删除。") { change { list -> list.first { it.id==h.id }.entries.remove(date) } } } }
        actionItem(form,"删除习惯","全部记录将一并删除","delete",true) { confirm("删除「${h.title}」？","这项习惯与全部历史记录将被删除，无法撤销。") { change { it.removeAll { habit -> habit.id==h.id } } } }
        showSheet(h.title,form)
    }
    private fun field(parent: LinearLayout,label: String,value: String,numeric: Boolean = false,max: Int = 0,lines: Int = 1,suffix: String? = null): EditText {
        val wrapper=TextInputLayout(this).apply {
            hint=label; boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_FILLED
            setBoxCornerRadii(dp(13).toFloat(),dp(13).toFloat(),dp(13).toFloat(),dp(13).toFloat())
            boxBackgroundColor=0xfff3f5f0.toInt(); boxStrokeWidth=0; boxStrokeWidthFocused=dp(1); boxStrokeColor=accent
            defaultHintTextColor=ColorStateList.valueOf(muted); if(suffix!=null) suffixText=suffix
            setPadding(0,0,0,0)
        }
        val edit=TextInputEditText(wrapper.context).apply {
            id=View.generateViewId(); contentDescription=label; setText(value); textSize=16f; setTextColor(ink)
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or if(lines>1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if(max>0) filters=arrayOf(InputFilter.LengthFilter(max))
            minLines=lines; if(lines==1) setSingleLine(true) else gravity=Gravity.TOP
            setPadding(dp(16),dp(20),dp(16),dp(12))
        }
        wrapper.addView(edit,LinearLayout.LayoutParams(-1,-2)); parent.line(wrapper,14); return edit
    }
    private fun formError(edit: EditText,message: String) {
        val wrapper=edit.parent?.parent as? TextInputLayout
        if(wrapper!=null) wrapper.error=message else edit.error=message
        edit.requestFocus()
    }
    private fun validNumber(edit: EditText,zero: Boolean = false): Double? {
        val n=edit.text.toString().toDoubleOrNull()
        if(n==null || !n.isFinite() || n>1e9 || if(zero) n<0 else n<=0) {
            formError(edit,if(zero) "请输入 0 到 10 亿之间的数值" else "请输入大于 0、最多 10 亿的数值"); return null
        }
        return n
    }
    private fun editor(title: String,form: LinearLayout,label: String,save: () -> Unit) { showSheet(title,form,label,save) }
    private fun editHabit(h: Habit? = null,draft: Bundle? = null) {
        val form=column().apply { setPadding(dp(24),0,dp(24),dp(8)) }
        val title=field(form,"习惯名称",draft?.getString("title") ?: h?.title ?: "",max=40)
        var color=draft?.getInt("color") ?: h?.color ?: 0
        val picker=row(); val swatches=mutableListOf<FrameLayout>()
        fun refreshColors() { swatches.forEachIndexed { i,v -> v.background=shape(Color.TRANSPARENT,22,if(i==color) colors[i] else Color.TRANSPARENT); v.isSelected=i==color; (v.getChildAt(0) as FrameLayout).removeAllViews(); if(i==color) (v.getChildAt(0) as FrameLayout).addView(icon("check",Color.WHITE,17),FrameLayout.LayoutParams(dp(17),dp(17),Gravity.CENTER)) } }
        colors.forEachIndexed { i,c ->
            val outer=FrameLayout(this).apply { contentDescription="颜色 ${i+1}" }
            val circle=FrameLayout(this).apply { background=shape(c,18) }
            outer.addView(circle,FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER)); outer.setOnClickListener { color=i; refreshColors() }; swatches.add(outer)
            picker.addView(outer,LinearLayout.LayoutParams(dp(44),dp(44)).apply { marginEnd=dp(8) })
        }
        refreshColors(); form.line(picker,16)
        val description=field(form,"目标（选填）",draft?.getString("goalDescription") ?: h?.goalDescription ?: "",max=300,lines=2)
        form.line(text("例如：学完一门课程，或读完一本书。",11f,muted),8)
        val pair=row(); val left=column(); val right=column()
        val daily=field(left,"每日目标",draft?.getString("daily") ?: number(h?.dailyTarget ?: 1.0),true)
        val unit=field(right,"单位",draft?.getString("unit") ?: h?.unit ?: "次",max=8)
        pair.addView(left,LinearLayout.LayoutParams(0,-2,1f)); pair.addView(right,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) }); form.line(pair,4)
        var quantityEnabled=draft?.getBoolean("quantityEnabled",!draft.getString("goal").isNullOrBlank()) ?: (h?.goal!=null)
        val quantityBox=column()
        val goal=field(quantityBox,"累计数量（选填）",draft?.getString("goal") ?: h?.goal?.let { number(it) } ?: "",true)
        quantityBox.line(text("仅用于数量进度统计，例如累计阅读 600 页。可不填。",11f,muted),8)
        val toggle=chip(if(quantityEnabled) "取消累计数量" else "＋ 设置累计数量（可选）") {}
        tap(toggle) {
            quantityEnabled=!quantityEnabled
            quantityBox.visibility=if(quantityEnabled) View.VISIBLE else View.GONE
            toggle.text=if(quantityEnabled) "取消累计数量" else "＋ 设置累计数量（可选）"
            if(quantityEnabled) goal.requestFocus()
        }
        form.line(toggle,10); form.line(quantityBox)
        quantityBox.visibility=if(quantityEnabled) View.VISIBLE else View.GONE
        if(h!=null) form.line(text("修改单位不会换算历史数量；每日目标会用于判断历史达标情况。",11f,muted),12)
        editor(if(h==null) "新建习惯" else "编辑习惯",form,if(h==null) "创建习惯" else "保存修改") {
            if(title.text.isBlank()) { formError(title,"给这个习惯起个名字吧"); return@editor }
            val dailyValue=validNumber(daily) ?: return@editor
            if(unit.text.isBlank()) { formError(unit,"请输入单位"); return@editor }
            val goalValue=if(quantityEnabled && goal.text.isNotBlank()) validNumber(goal) ?: return@editor else null
            val updated=Habit(h?.id ?: java.util.UUID.randomUUID().toString(),title.text.toString().trim(),unit.text.toString().trim(),dailyValue,goalValue,color,h?.archived ?: false,h?.entries ?: linkedMapOf(),description.text.toString().trim())
            if(change { list -> val i=list.indexOfFirst { it.id==updated.id }; if(i<0) list.add(updated) else list[i]=updated }) openDialog?.dismiss()
        }
        editorState={ Bundle().apply { putString("type","habit"); putString("id",h?.id); putString("title",title.text.toString()); putString("daily",daily.text.toString()); putString("unit",unit.text.toString()); putString("goal",goal.text.toString()); putString("goalDescription",description.text.toString()); putBoolean("quantityEnabled",quantityEnabled); putInt("color",color) } }
    }
    private fun editEntry(h: Habit,date: String = selected,draft: Bundle? = null) {
        val form=column().apply { setPadding(dp(24),0,dp(24),dp(8)) }; val e=h.entries[date]
        val meta=row(); meta.addView(text(Days.label(date),12f,muted),LinearLayout.LayoutParams(0,-2,1f))
        meta.addView(iconButton("more","管理${h.title}") { openDialog?.dismiss(); taskMenu(h) }); form.line(meta)
        val amount=field(form,"当天完成总量",draft?.getString("amount") ?: number(e?.amount ?: 0.0),true,suffix=h.unit)
        val quick=row()
        listOf("清零" to { amount.setText("0") },"＋1" to { val n=amount.text.toString().toDoubleOrNull() ?: 0.0; amount.setText(number((n+1).coerceAtMost(1e9))) },"达标 ${number(h.dailyTarget)}" to { amount.setText(number(h.dailyTarget)) }).forEach { (label,action) -> quick.addView(chip(label,false) { action() },LinearLayout.LayoutParams(0,-2,1f)) }
        form.line(quick,6)
        val note=field(form,"写下今天的感受…",draft?.getString("note") ?: e?.note ?: "",max=2000,lines=4)
        form.line(text("只想写记录？完成数量可以保留为 0。",11f,muted),8)
        editor(h.title,form,"保存记录") {
            val value=validNumber(amount,true) ?: return@editor; val content=note.text.toString().trim()
            if(value==0.0 && content.isEmpty()) { formError(note,"写一点记录，或填写完成数量"); return@editor }
            if(change { list -> list.first { it.id==h.id }.entries[date]=DailyEntry(value,content) }) openDialog?.dismiss()
        }
        editorState={ Bundle().apply { putString("type","entry"); putString("id",h.id); putString("date",date); putString("amount",amount.text.toString()); putString("note",note.text.toString()) } }
    }
    private fun dataMenu() {
        val form=column()
        if(!loadFailed) actionItem(form,"导出备份","复制完整数据，粘贴到文件或笔记保存","export") {
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("日积数据备份",HabitCodec.encode(habits))); message("备份已复制，请妥善保存。")
        }
        actionItem(form,"恢复备份","兼容此前版本的 JSON 备份","import") { restore() }
        val about=column().apply { setPadding(dp(24),dp(24),dp(24),dp(4)); line(text("日积  2.2.0",12f,muted)); line(text("所有记录保存在这台设备上。",11f,muted),6) }; form.line(about)
        showSheet("设置与数据",form)
    }
    private fun restore(draft: Bundle? = null) {
        val form=column().apply { setPadding(dp(24),0,dp(24),dp(8)) }
        val input=field(form,"粘贴完整的 JSON 备份",draft?.getString("raw") ?: "",lines=6)
        editor("恢复备份",form,"检查备份") {
            val next=try { HabitCodec.decode(input.text.toString().trim()) } catch(_: Exception) { formError(input,"备份格式无效，当前数据未更改"); return@editor }
            confirm("恢复 ${next.size} 个习惯？","当前数据将被替换。建议先导出当前备份。") {
                try { repository.save(next); habits=next; loadFailed=false; openDialog?.dismiss(); render(); message("备份已恢复") } catch(_: Exception) { message("恢复失败，请重试。") }
            }
        }
        editorState={ Bundle().apply { putString("type","restore"); putString("raw",input.text.toString()) } }
    }
}
