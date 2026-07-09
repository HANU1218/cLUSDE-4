package com.siteblocker.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.MutableLiveData

/** Metadata about a single detected browser, shown on the Browser Detection screen. */
data class BrowserInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isDefault: Boolean
)

class BrowserDetector(private val context: Context) {

    @Volatile
    private var cachedBrowserPackages: Set<String> = emptySet()

    @Volatile
    private var cachedDefaultPackage: String? = null

    /** Observable list of full browser metadata, used by the Browser Detection screen. */
    val browsers = MutableLiveData<List<BrowserInfo>>(emptyList())

    init {
        refresh()
    }

    fun refresh(): Set<String> {
        val packageManager = context.packageManager
        val discovered = mutableSetOf<String>()

        val httpIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))

        try {
            val httpResolves = packageManager.queryIntentActivities(httpIntent, 0)
            val httpsResolves = packageManager.queryIntentActivities(httpsIntent, 0)

            for (resolveInfo in httpResolves) {
                resolveInfo.activityInfo?.packageName?.let { discovered.add(it) }
            }
            for (resolveInfo in httpsResolves) {
                resolveInfo.activityInfo?.packageName?.let { discovered.add(it) }
            }

            cachedDefaultPackage = try {
                packageManager.resolveActivity(httpsIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to query browser packages", e)
        }

        cachedBrowserPackages = discovered
        Logger.i(TAG, "Browser list refreshed, ${discovered.size} browsers detected")

        browsers.postValue(buildBrowserInfoList(discovered, packageManager))
        return cachedBrowserPackages
    }

    private fun buildBrowserInfoList(
        packages: Set<String>,
        packageManager: PackageManager
    ): List<BrowserInfo> {
        return packages.mapNotNull { pkg ->
            try {
                val info = packageManager.getPackageInfo(pkg, 0)
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                BrowserInfo(
                    packageName = pkg,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    versionName = info.versionName ?: "Unknown",
                    isDefault = pkg == cachedDefaultPackage
                )
            } catch (e: Exception) {
                // Package might have been uninstalled between query and lookup.
                null
            }
        }.sortedByDescending { it.isDefault }
    }

    fun isBrowser(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (cachedBrowserPackages.isEmpty()) {
            refresh()
        }
        return cachedBrowserPackages.contains(packageName)
    }

    fun getBrowserPackages(): Set<String> = cachedBrowserPackages

    fun getDefaultBrowserPackage(): String? = cachedDefaultPackage

    companion object {
        private const val TAG = "BrowserDetector"
    }
}
