package com.example.push_up_counter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.icu.number.IntegerWidth
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import org.w3c.dom.Text

// do-what : Constructor for XML installation. (Shows up when the app starts running). (it's not a programmatic instantiation)
// AttributeSet : contains all the properties assigned to that view in the XML file
// (like android: layout_width="match_parent" or your custom app:borderColor=#FF00000")
// Missing the constructor will lead the program run into InstantiationException.
class OverlayView (context: Context, attrs: AttributeSet?) : View(context, attrs){

    var counter = 0
    var stage: String? = null

    var printQuote: String? = null
    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 60f
        style = Paint.Style.FILL
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float) {
        val words = text.split(" ")
        var line = ""
        var y = startY

        for (word in words) {
            val testline = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(testline) > maxWidth) {
                canvas.drawText(line, x, y, textPaint)
                line = word
                y += textPaint.textSize + 10f
            } else {
                line = testline
            }
        }
        canvas.drawText(line, x, y, textPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawText("Counter: $counter",70f,80f,textPaint)
        canvas.drawText("Stage: $stage", 70f, 135f, textPaint)

        printQuote?.let { drawWrappedText(canvas, it, 70f, 250f, width.toFloat() - 140f)}
    }
}