package com.chiller3.bcr.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import com.google.android.material.chip.ChipGroup
import java.lang.Integer.max
import java.lang.Integer.min

/** Hacky wrapper around [ChipGroup] to make every row individually centered. */
class ChipGroupCentered : ChipGroup {
    private val _rowCountField = javaClass.superclass.superclass.getDeclaredField("rowCount")
    private var rowCountField
        get() = _rowCountField.getInt(this)
        set(value) = _rowCountField.setInt(this, value)

    init {
        _rowCountField.isAccessible = true
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    @SuppressLint("RestrictedApi")
    override fun onLayout(sizeChanged: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isSingleLine) {
            return super.onLayout(sizeChanged, left, top, right, bottom)
        }

        val maxWidth = right - left - paddingRight - paddingLeft
        var offsetTop = paddingTop
        var rowStartIndex = 0

        while (rowStartIndex < childCount) {
            val (rowEndIndex, rowWidth, rowHeight) = getFittingRow(rowStartIndex, maxWidth)

            layoutRow(
                rowStartIndex..rowEndIndex,
                paddingLeft + (maxWidth - rowWidth) / 2,
                offsetTop,
                rowCountField,
            )

            offsetTop += rowHeight + lineSpacing
            rowStartIndex = rowEndIndex + 1
            rowCountField += 1
        }
    }

    /**
     * Find the last index starting from [indexStart] that will fit in the row.
     *
     * @return (Index of last fitting element, width of row, height of row)
     */
    @SuppressLint("RestrictedApi")
    private fun getFittingRow(indexStart: Int, maxWidth: Int): Triple<Int, Int, Int> {
        var indexEnd = indexStart
        var childStart = 0
        var rowHeight = 0

        while (true) {
            val child = getChildAt(indexEnd)
            if (child.visibility == GONE) {
                continue
            }

            val (marginStart, marginEnd) = getMargins(child)
            val childWidth = marginStart + child.measuredWidth + marginEnd
            val separator = if (indexEnd > indexStart) { itemSpacing } else { 0 }

            // If even one child can't fit, force it to do so anyway
            if (indexEnd != indexStart && childStart + separator + childWidth > maxWidth) {
                --indexEnd
                break
            }

            childStart += separator + childWidth
            rowHeight = max(rowHeight, child.measuredHeight)

            if (indexEnd == childCount - 1) {
                break
            } else {
                ++indexEnd
            }
        }

        return Triple(indexEnd, min(childStart, maxWidth), rowHeight)
    }

    /**
     * Lay out [childIndices] children in a row positioned at [offsetLeft] and [offsetTop].
     */
    @SuppressLint("RestrictedApi")
    private fun layoutRow(childIndices: IntRange, offsetLeft: Int, offsetTop: Int, rowIndex: Int) {
        val range = if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
            childIndices.reversed()
        } else {
            childIndices
        }
        var childStart = offsetLeft

        for (i in range) {
            val child = getChildAt(i)
            if (child.visibility == GONE) {
                child.setTag(com.google.android.material.R.id.row_index_key, -1)
                continue
            } else {
                child.setTag(com.google.android.material.R.id.row_index_key, rowIndex)
            }

            val (marginStart, marginEnd) = getMargins(child)

            child.layout(
                childStart + marginStart,
                offsetTop,
                childStart + marginStart + child.measuredWidth,
                offsetTop + child.measuredHeight,
            )

            childStart += marginStart + child.measuredWidth + marginEnd + itemSpacing
        }
    }

    companion object {
        private fun getMargins(view: View): Pair<Int, Int> {
            val lp = view.layoutParams

            return if (lp is MarginLayoutParams) {
                Pair(lp.marginStart, lp.marginEnd)
            } else {
                Pair(0, 0)
            }
        }
    }
}
