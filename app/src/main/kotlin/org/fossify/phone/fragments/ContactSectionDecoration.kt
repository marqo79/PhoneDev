package org.fossify.phone.fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getProperBackgroundColor

internal class ContactSectionDecoration(parent: RecyclerView) : RecyclerView.ItemDecoration() {
    private val metrics = parent.resources.displayMetrics
    private val height = (40 * metrics.density).toInt()
    private val inset = 18 * metrics.density
    private val indexWidth = 32 * metrics.density
    private val cardInset = (8 * metrics.density).toInt()
    private val cardRadius = 10 * metrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, metrics)
        typeface = Typeface.DEFAULT_BOLD
        strokeWidth = metrics.density
    }

    var sectionTitles: Map<Int, String> = emptyMap()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val rtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        outRect.left = cardInset + if (rtl) indexWidth.toInt() else 0
        outRect.right = cardInset + if (rtl) 0 else indexWidth.toInt()
        if (parent.getChildAdapterPosition(view) in sectionTitles) {
            outRect.top = height
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val rtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val left = parent.paddingLeft + inset + if (rtl) indexWidth else 0f
        val right = parent.width - parent.paddingRight - inset - if (rtl) 0f else indexWidth
        val textColor = parent.context.getProperTextColor()
        val backgroundColor = parent.context.getProperBackgroundColor()
        val cardColor = ColorUtils.blendARGB(
            backgroundColor,
            Color.WHITE,
            if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) 0.72f else 0.08f
        )

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            val top = child.top + child.translationY
            paint.color = cardColor
            canvas.drawRoundRect(
                child.left + child.translationX,
                top,
                child.right + child.translationX,
                child.bottom + child.translationY,
                cardRadius,
                cardRadius,
                paint
            )

            val title = sectionTitles[position] ?: continue
            if (position > 0) {
                paint.color = ColorUtils.setAlphaComponent(textColor, 42)
                canvas.drawLine(left, top - height, right, top - height, paint)
            }
            paint.color = textColor
            paint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val baseline = top - (height + paint.ascent() + paint.descent()) / 2
            canvas.drawText(title, if (rtl) right else left, baseline, paint)
        }
    }
}
