package com.github.paulpv.helloblescanner

import android.view.View
import android.view.View.OnClickListener
import androidx.recyclerview.widget.RecyclerView

abstract class BindableViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    open fun bindTo(item: T, clickListener: OnClickListener) {
        //int position = getLayoutPosition();
        //Log.v(TAG, "bindTo: position=" + position);
        itemView.setOnClickListener(clickListener)
        itemView.tag = this
    }
}
