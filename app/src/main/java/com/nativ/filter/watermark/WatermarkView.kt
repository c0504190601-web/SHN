package com.nativ.filter.watermark

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/**
 * הסמל הקבוע: מגן עם סימן וי בצורת דרך, "כשר" ו"נתיב נקי".
 * מצויר בקוד (ולא כתמונה) כדי שיהיה חד בכל גודל מסך. מערכת צירים פנימית: 132×178.
 */
class WatermarkView(ctx: Context) : View(ctx) {

    private val teal = 0xFF0F8F8C.toInt()

    private val shield = Path().apply {
        moveTo(66f, 0f); lineTo(132f, 24f); lineTo(132f, 86f)
        cubicTo(132f, 130f, 102f, 160f, 66f, 178f)
        cubicTo(30f, 160f, 0f, 130f, 0f, 86f)
        lineTo(0f, 24f); close()
    }
    private val check = Path().apply { moveTo(44f, 74f); lineTo(60f, 90f); lineTo(88f, 58f) }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = teal; alpha = 40 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = teal; strokeWidth = 4f; strokeJoin = Paint.Join.ROUND
    }
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = teal; strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND; pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = teal; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = teal; textSize = 12f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 4f
        val s = minOf((width - pad * 2) / 132f, (height - pad * 2) / 178f)
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 132f * s) / 2f, (height - 178f * s) / 2f)
        canvas.scale(s, s)
        canvas.drawPath(shield, fill)
        canvas.drawPath(shield, ring)
        canvas.drawPath(check, road)
        canvas.drawPath(check, dash)
        canvas.drawText("כשר", 66f, 128f, big)
        canvas.drawText("נתיב נקי", 66f, 150f, small)
        canvas.restore()
    }
}
