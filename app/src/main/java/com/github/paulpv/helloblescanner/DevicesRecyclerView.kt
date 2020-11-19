package com.github.paulpv.helloblescanner

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class DevicesRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    RecyclerView(context, attrs, defStyleAttr) {
    //companion object {
    //    private val TAG = TAG(DevicesRecyclerView::class)
    //}

    override fun onAttachedToWindow() {
        //Log.e(TAG, "onAttachedToWindow()")
        super.onAttachedToWindow()
        adapter?.onAttachedToRecyclerView(this)
    }

    override fun onDetachedFromWindow() {
        //Log.e(TAG, "onDetachedFromWindow()")
        super.onDetachedFromWindow()
        adapter?.onDetachedFromRecyclerView(this)
    }
}