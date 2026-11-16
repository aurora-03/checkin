package com.amiao.daily_check

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min

/** Small consistent line icons, drawn as vectors without image or font dependencies. */
class Glyph(private val kind: String, private val tint: Int) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 1.7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun draw(canvas: Canvas) {
        val state = canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(vararg values: Float) { val p = Path(); p.moveTo(values[0], values[1]); for (i in 2 until values.size step 2) p.lineTo(values[i], values[i + 1]); canvas.drawPath(p, pen) }
        fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = 3f) = canvas.drawRoundRect(l,t,r,b,radius,radius,pen)
        when (kind) {
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "check" -> line(5.5f,12f,10f,16.5f,18.5f,7.5f)
            "today" -> { box(4f,4f,20f,20f,5f); line(8f,12f,11f,15f,16f,9f) }
            "target" -> { canvas.drawCircle(12f,12f,8f,pen); canvas.drawCircle(12f,12f,4f,pen); line(12f,12f,20f,4f) }
            "journal" -> { box(5f,3f,20f,21f,2f); line(9f,3f,9f,21f); line(12f,8f,16f,8f); line(12f,12f,16f,12f) }
            "calendar" -> { box(3.5f,5f,20.5f,21f,3f); line(3.5f,10f,20.5f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f); line(8f,14f,10f,14f); line(14f,14f,16f,14f) }
            "book" -> { line(12f,5f,12f,21f); line(12f,5f,4f,3f,4f,18f,12f,21f,20f,18f,20f,3f,12f,5f) }
            "activity" -> { line(3f,13f,7f,13f,10f,5f,14f,19f,17f,11f,21f,11f) }
            "note" -> { line(5f,5f,15f,5f); line(5f,10f,12f,10f); line(5f,15f,9f,15f); line(12f,17f,19f,10f,22f,13f,15f,20f,11f,21f,12f,17f) }
            "leaf" -> { val p=Path(); p.moveTo(6f,18f); p.cubicTo(0f,6f,11f,2f,21f,3f); p.cubicTo(22f,15f,14f,23f,6f,18f); canvas.drawPath(p,pen); line(4f,22f,15f,10f) }
            "drop" -> { val p=Path(); p.moveTo(12f,2f); p.cubicTo(8f,8f,4f,11f,4f,15f); p.cubicTo(4f,24f,20f,24f,20f,15f); p.cubicTo(20f,11f,16f,8f,12f,2f); canvas.drawPath(p,pen) }
            "sun" -> { canvas.drawCircle(12f,12f,4f,pen); for(i in 0..7) { val a=i*Math.PI/4; line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) } }
            "left" -> line(15f,5f,8f,12f,15f,19f)
            "right" -> line(9f,5f,16f,12f,9f,19f)
            "down" -> line(7f,9f,12f,14f,17f,9f)
            "more" -> { pen.style=Paint.Style.FILL; for(y in listOf(5f,12f,19f)) canvas.drawCircle(12f,y,1.4f,pen); pen.style=Paint.Style.STROKE }
            "settings" -> { canvas.drawCircle(12f,12f,4f,pen); canvas.drawCircle(12f,12f,8f,pen); for(i in 0..7) { val a=i*Math.PI/4; line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) } }
            "archive" -> { box(4f,9f,20f,21f,2f); box(3f,3f,21f,8f,1f); line(9f,13f,15f,13f) }
            "delete" -> { line(4f,6f,20f,6f); line(9f,3f,15f,3f); line(6f,6f,7f,21f,17f,21f,18f,6f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
            "export" -> { box(4f,10f,20f,21f,2f); line(12f,15f,12f,3f); line(8f,7f,12f,3f,16f,7f) }
            "import" -> { box(4f,10f,20f,21f,2f); line(12f,3f,12f,15f); line(8f,11f,12f,15f,16f,11f) }
        }
        canvas.restoreToCount(state)
    }
    override fun setAlpha(alpha: Int) { pen.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter=filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

@SuppressLint("ViewConstructor")
class RingView(context: Context, private val fraction: Float, private val tint: Int, private val completed: Boolean = false) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val check = Glyph("check",tint)
    override fun onDraw(c: Canvas) {
        val s=min(width,height).toFloat(); val stroke=s*.065f
        rect.set((width-s)/2+stroke,(height-s)/2+stroke,(width+s)/2-stroke,(height+s)/2-stroke)
        pen.style=Paint.Style.STROKE; pen.strokeWidth=stroke; pen.color=0xffe5ebe5.toInt(); c.drawOval(rect,pen)
        pen.strokeCap=Paint.Cap.ROUND; pen.color=tint; c.drawArc(rect,-90f,360f*fraction.coerceIn(0f,1f),false,pen)
        if(completed) { check.setBounds((width*.26).toInt(),(height*.26).toInt(),(width*.74).toInt(),(height*.74).toInt()); check.draw(c) }
    }
}

/** Rolling 28-day grid, based on real entry amounts rather than sample data. */
@SuppressLint("ViewConstructor")
class HabitGrid(context: Context, private val habit: Habit, private val tint: Int) : View(context) {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    private val levels=List(28) { i -> ((habit.entries[Days.shift(Days.today(),i-27)]?.amount ?: 0.0)/habit.dailyTarget).coerceIn(0.0,1.0).toFloat() }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val w=MeasureSpec.getSize(widthMeasureSpec)
        val gap=4*resources.displayMetrics.density
        setMeasuredDimension(w,resolveSize((((w-gap*13)/14f)*2+gap).toInt(),heightMeasureSpec))
    }
    override fun onDraw(c: Canvas) {
        val gap=4*resources.displayMetrics.density; val cell=(width-gap*13)/14f
        repeat(28) { i ->
            val f=levels[i]
            pen.color=if(f==0f) 0xffeff2ed.toInt() else Color.argb((80+175*f).toInt(),Color.red(tint),Color.green(tint),Color.blue(tint))
            val x=(i%14)*(cell+gap); val y=(i/14)*(height/2f+gap/2)
            c.drawRoundRect(x,y,x+cell,y+(height-gap)/2,3f,3f,pen)
        }
    }
}
