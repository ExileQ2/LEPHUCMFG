package com.example.lephucmfg.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lephucmfg.R
import com.example.lephucmfg.data.AbRow

class AbAdapter(private val data: List<AbRow>) :
    RecyclerView.Adapter<AbAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_ab, parent, false))

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = data[position]
        holder.tv.text = "${r.a}  ${r.b}"   // removed r.id
    }
}
