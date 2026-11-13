package com.skypin.soundmate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** Batang equalizer tegak, nilai -10 sampai +10 dB. */
class VerticalSlider(context: Context) : View(context) {

    var value: Int = 0
        set(v) {
            field = v.coerceIn(-10, 10)
            invalidate()
        }

    var onChange: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val knobR = 9f * density
    private val trackW = 4f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val colorTrack = context.getColor(R.color.line)
    private val colorOn = context.getColor(R.color.accent)
    private val colorOff = context.getColor(R.color.off)

    private fun yFor(v: Int): Float {
        val usable = height - 2f * knobR
        return knobR + (10 - v) / 20f * usable
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val main = if (isEnabled) colorOn else colorOff

        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = trackW
        paint.color = colorTrack
        canvas.drawLine(cx, knobR, cx, height - knobR, paint)

        val yMid = yFor(0)
        val yKnob = yFor(value)
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = main
        canvas.drawLine(cx, yMid, cx, yKnob, paint)

        canvas.drawCircle(cx, yKnob, knobR, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val usable = height - 2f * knobR
                if (usable > 0f) {
                    val v = (10f - (event.y - knobR) / usable * 20f).roundToInt().coerceIn(-10, 10)
                    if (v != value) {
                        value = v
                        onChange?.invoke(v)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
