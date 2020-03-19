package com.cornellappdev.coffee_chats_android

import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.cornellappdev.coffee_chats_android.models.ClubOrInterest


class TimeOptionAdapter(private val mContext: Context, list: Array<String>) :
    ArrayAdapter<String?>(mContext, 0, list), Filterable {
    var list = emptyArray<String>()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val listItem = LayoutInflater.from(mContext).inflate(R.layout.time_option_item, parent, false)
        val timeTextView = listItem.findViewById<TextView>(R.id.time_option_text)
        timeTextView.text = list[position]

        return listItem
    }

    init {
        this.list = list
    }
}