package com.siteblocker.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.siteblocker.app.databinding.ActivityMainBinding

/**
 * Single-activity host. Hosts the Dashboard, Debug Log, Keywords, Statistics,
 * and Settings screens as fragments behind a bottom navigation bar.
 * Browser Detection and Accessibility Inspector are reached from the
 * Dashboard's quick-action cards to keep the bottom nav to five items.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeMode()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        Logger.i(TAG, "MainActivity created")

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_debug_log -> DebugLogFragment()
                R.id.nav_keywords -> KeywordsFragment()
                R.id.nav_statistics -> StatisticsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            showFragment(fragment)
            binding.toolbar.title = item.title
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_dashboard
        }
    }

    /** Called by DashboardFragment's quick-action cards. */
    fun openScreen(fragment: Fragment, title: String) {
        showFragment(fragment)
        binding.bottomNav.menu.setGroupCheckable(0, false, false)
        for (i in 0 until binding.bottomNav.menu.size()) {
            binding.bottomNav.menu.getItem(i).isChecked = false
        }
        binding.bottomNav.menu.setGroupCheckable(0, true, true)
        binding.toolbar.title = title
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun applyThemeMode() {
        try {
            val settings = SettingsManager(applicationContext)
            val mode = when (settings.getThemeMode()) {
                ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to apply theme mode", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
