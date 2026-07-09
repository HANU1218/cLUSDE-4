package com.siteblocker.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Shows every browser detected on-device, refreshed on demand or after install/uninstall events. */
class BrowsersFragment : Fragment() {

    private lateinit var browserDetector: BrowserDetector
    private lateinit var adapter: BrowserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_browsers, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        browserDetector = BrowserDetector(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.browsersRecyclerView)
        val emptyText = view.findViewById<TextView>(R.id.browsersEmptyText)
        adapter = BrowserAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        browserDetector.browsers.observe(viewLifecycleOwner) { list ->
            adapter.updateBrowsers(list)
            emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        view.findViewById<View>(R.id.refreshBrowsersButton).setOnClickListener {
            try {
                browserDetector.refresh()
                Toast.makeText(requireContext(), "Browser list refreshed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Logger.e("BrowsersFragment", "Refresh failed", e)
            }
        }
    }
}
