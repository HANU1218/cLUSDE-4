package com.siteblocker.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BrowserAdapter(private var browsers: List<BrowserInfo> = emptyList()) :
    RecyclerView.Adapter<BrowserAdapter.BrowserViewHolder>() {

    class BrowserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.browserLabel)
        val pkg: TextView = view.findViewById(R.id.browserPackage)
        val version: TextView = view.findViewById(R.id.browserVersion)
        val defaultBadge: TextView = view.findViewById(R.id.browserDefaultBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_browser, parent, false)
        return BrowserViewHolder(view)
    }

    override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) {
        val browser = browsers[position]
        holder.label.text = browser.label
        holder.pkg.text = browser.packageName
        holder.version.text = "Version ${browser.versionName}  •  Installed  •  Supported"
        holder.defaultBadge.visibility = if (browser.isDefault) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = browsers.size

    fun updateBrowsers(newBrowsers: List<BrowserInfo>) {
        browsers = newBrowsers
        notifyDataSetChanged()
    }
}
