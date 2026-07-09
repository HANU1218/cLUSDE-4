package com.siteblocker.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.util.Locale

/** Aggregate blocking statistics: today, yesterday, 7-day, totals, top sites. */
class StatisticsFragment : Fragment() {

    private lateinit var statisticsManager: StatisticsManager
    private var statsContainer: LinearLayout? = null
    private var topSitesContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_statistics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statisticsManager = StatisticsManager(requireContext())
        statsContainer = view.findViewById(R.id.statisticsContainer)
        topSitesContainer = view.findViewById(R.id.topSitesContainer)

        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.statsSwipeRefresh)
        swipeRefresh.setOnRefreshListener {
            refresh()
            swipeRefresh.isRefreshing = false
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        statsContainer?.removeAllViews()
        topSitesContainer?.removeAllViews()

        addRow(statsContainer, getString(R.string.stats_today), statisticsManager.getTodayCount().toString())
        addRow(statsContainer, getString(R.string.stats_yesterday), statisticsManager.getYesterdayCount().toString())
        addRow(statsContainer, getString(R.string.stats_7days), statisticsManager.getLast7DaysCount().toString())
        addRow(statsContainer, getString(R.string.stats_total), statisticsManager.getTotalCount().toString())
        addRow(
            statsContainer, getString(R.string.stats_most_blocked_site),
            statisticsManager.getMostBlockedSite() ?: getString(R.string.dashboard_none)
        )
        addRow(
            statsContainer, getString(R.string.stats_most_active_browser),
            statisticsManager.getMostActiveBrowser() ?: getString(R.string.dashboard_none)
        )
        addRow(
            statsContainer, getString(R.string.stats_average_daily),
            String.format(Locale.US, "%.1f", statisticsManager.getAverageDailyBlocks())
        )

        val topSites = statisticsManager.getTopSites(10)
        if (topSites.isEmpty()) {
            addRow(topSitesContainer, getString(R.string.dashboard_none), "")
        } else {
            topSites.forEach { (site, count) -> addRow(topSitesContainer, site, count.toString()) }
        }
    }

    private fun addRow(container: LinearLayout?, label: String, value: String) {
        val c = container ?: return
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_row, c, false)
        row.findViewById<TextView>(R.id.statLabel).text = label
        row.findViewById<TextView>(R.id.statValue).text = value
        c.addView(row)
    }
}
