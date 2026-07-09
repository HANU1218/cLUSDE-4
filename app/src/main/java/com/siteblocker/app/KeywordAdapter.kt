package com.siteblocker.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KeywordAdapter(
    private var keywords: MutableList<String>,
    private val onDeleteClick: (String) -> Unit,
    private val onEditClick: (String) -> Unit
) : RecyclerView.Adapter<KeywordAdapter.KeywordViewHolder>() {

    class KeywordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keywordText: TextView = view.findViewById(R.id.keywordText)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        val editButton: ImageButton = view.findViewById(R.id.editButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keyword, parent, false)
        return KeywordViewHolder(view)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        val keyword = keywords[position]
        holder.keywordText.text = keyword
        holder.deleteButton.setOnClickListener { onDeleteClick(keyword) }
        holder.editButton.setOnClickListener { onEditClick(keyword) }
    }

    override fun getItemCount(): Int = keywords.size

    /** Replaces the full dataset and refreshes the list. */
    fun updateKeywords(newKeywords: List<String>) {
        keywords = newKeywords.toMutableList()
        notifyDataSetChanged()
    }
}
