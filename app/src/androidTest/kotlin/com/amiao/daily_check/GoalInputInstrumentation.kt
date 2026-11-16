package com.amiao.daily_check

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText

/** Real pointer and IME regression: never persists or alters any habits. */
@androidx.annotation.RequiresApi(30)
class GoalInputInstrumentation : Instrumentation() {
    private fun descendants(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun screenRect(view: View): Rect {
        val location=IntArray(2); view.getLocationOnScreen(location)
        return Rect(location[0],location[1],location[0]+view.width,location[1]+view.height)
    }
    private fun awaitStableLayout(views: List<View>) {
        val deadline=SystemClock.uptimeMillis()+5000
        var previous=""; var stableSince=0L
        while(SystemClock.uptimeMillis()<deadline) {
            var current=""
            runOnMainSync {
                val frame=Rect(); views.first().rootView.getWindowVisibleDisplayFrame(frame)
                current=views.joinToString { screenRect(it).toString() }+frame.toString()
            }
            if(current!=previous) { previous=current; stableSince=SystemClock.uptimeMillis() }
            else if(SystemClock.uptimeMillis()-stableSince>=300) return
            SystemClock.sleep(50)
        }
        error("Editor layout did not settle")
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle()
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lateinit var editor: Dialog
            lateinit var goal: EditText
            lateinit var unit: EditText
            runOnMainSync {
                descendants(activity.window.decorView).first { it.contentDescription=="新建任务" }.performClick()
                editor=MainActivity::class.java.getDeclaredField("openDialog").run { isAccessible=true; get(activity) as Dialog }
                val fields=descendants(editor.window!!.decorView).filterIsInstance<EditText>()
                goal=fields.first { it.contentDescription=="累计目标" }
                unit=fields.first { it.contentDescription=="单位" }
                goal.setText("")
            }
            waitForIdleSync()
            uiAutomation.waitForIdle(250,3000)
            awaitStableLayout(listOf(unit,goal))
            val unitBounds=Rect()
            runOnMainSync { unitBounds.set(screenRect(unit)) }
            val unitTime=SystemClock.uptimeMillis()
            sendPointerSync(MotionEvent.obtain(unitTime,unitTime,MotionEvent.ACTION_DOWN,unitBounds.exactCenterX(),unitBounds.exactCenterY(),0))
            sendPointerSync(MotionEvent.obtain(unitTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,unitBounds.exactCenterX(),unitBounds.exactCenterY(),0))
            val end=SystemClock.uptimeMillis()+5000
            var ime=false
            while(SystemClock.uptimeMillis()<end && !ime) {
                waitForIdleSync()
                runOnMainSync { ime=editor.window!!.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true }
                if(!ime) SystemClock.sleep(50)
            }
            check(ime) { "Numeric-field test requires a visible soft keyboard" }
            uiAutomation.waitForIdle(250,3000)
            awaitStableLayout(listOf(unit,goal))
            val wrapperBounds=Rect(); val inputBounds=Rect(); var inputHeight=0
            runOnMainSync {
                wrapperBounds.set(screenRect(goal.parent.parent as View))
                inputBounds.set(screenRect(goal)); inputHeight=goal.height
            }
            val frame=Rect(); val viewport=Rect()
            runOnMainSync {
                editor.window!!.decorView.getWindowVisibleDisplayFrame(frame)
                var ancestor=goal.parent as View
                while(ancestor !is androidx.core.widget.NestedScrollView) ancestor=ancestor.parent as View
                viewport.set(screenRect(ancestor))
            }
            check(wrapperBounds.intersect(viewport) && wrapperBounds.intersect(frame)) { "Goal field is inaccessible while the keyboard is open: input=$inputBounds scroll=$viewport window=$frame" }
            val now=SystemClock.uptimeMillis()
            sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,wrapperBounds.exactCenterX(),wrapperBounds.exactCenterY(),0))
            sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,wrapperBounds.exactCenterX(),wrapperBounds.exactCenterY(),0))
            waitForIdleSync()
            var focused=false
            runOnMainSync { focused=goal.hasFocus() }
            check(focused) { "Visible goal field did not accept touch focus: wrapper=$wrapperBounds input=$inputBounds inputHeight=$inputHeight unitFocused=${unit.hasFocus()}" }
            sendStringSync("600.5")
            waitForIdleSync()
            runOnMainSync {
                check(goal.text.toString()=="600.5") { "Goal did not receive numeric input: ${goal.text}" }
                unit.requestFocus()
                unit.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT)
                check(goal.hasFocus()) { "IME Next must move from unit to cumulative goal" }
                check(goal.text.toString()=="600.5")
                editor.dismiss()
            }
            result.putString("stream","PASS: cumulative goal accepts a real touch and decimal input while IME is visible\n")
            finish(Activity.RESULT_OK,result)
        } catch(error: Throwable) {
            result.putString("stream","FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED,result)
        }
    }
}
