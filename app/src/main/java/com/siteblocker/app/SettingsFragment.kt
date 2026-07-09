package com.siteblocker.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var keywordManager: KeywordManager
    private lateinit var statisticsManager: StatisticsManager
    private var versionTapCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        keywordManager = KeywordManager(requireContext())
        statisticsManager = StatisticsManager(requireContext())

        setupThemeToggle(view)
        setupSwitches(view)
        setupActions(view)
        setupVersion(view)
    }

    private fun setupThemeToggle(view: View) {
        val group = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.themeToggleGroup)
        val lightBtn = view.findViewById<View>(R.id.themeLightButton).id
        val darkBtn = view.findViewById<View>(R.id.themeDarkButton).id
        val systemBtn = view.findViewById<View>(R.id.themeSystemButton).id

        group.check(
            when (settingsManager.getThemeMode()) {
                ThemeMode.LIGHT -> lightBtn
                ThemeMode.DARK -> darkBtn
                ThemeMode.SYSTEM -> systemBtn
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                lightBtn -> ThemeMode.LIGHT
                darkBtn -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            settingsManager.setThemeMode(mode)
            activity?.recreate()
        }
    }

    private fun setupSwitches(view: View) {
        val autoStart = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.autoStartSwitch)
        val notifications = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.notificationsSwitch)
        val overlayAnim = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.overlayAnimationSwitch)
        val debugMode = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.debugModeSwitch)

        autoStart.isChecked = settingsManager.isAutoStartCheckEnabled()
        notifications.isChecked = settingsManager.areNotificationsEnabled()
        overlayAnim.isChecked = settingsManager.isOverlayAnimationEnabled()
        debugMode.isChecked = settingsManager.isDebugModeEnabled()

        autoStart.setOnCheckedChangeListener { _, checked -> settingsManager.setAutoStartCheckEnabled(checked) }
        notifications.setOnCheckedChangeListener { _, checked -> settingsManager.setNotificationsEnabled(checked) }
        overlayAnim.setOnCheckedChangeListener { _, checked -> settingsManager.setOverlayAnimationEnabled(checked) }
        debugMode.setOnCheckedChangeListener { _, checked ->
            settingsManager.setDebugModeEnabled(checked)
            Logger.i("SettingsFragment", "Debug mode ${if (checked) "enabled" else "disabled"}")
        }
    }

    private fun setupActions(view: View) {
        view.findViewById<View>(R.id.exportLogsButton).setOnClickListener {
            val repo = EventRepository.getInstance(requireContext())
            val fileName = "siteblocker_log_${timestamp()}.txt"
            val file = ShareUtil.exportAndShare(requireContext(), fileName, repo.getAllAsText())
            if (file == null) Toast.makeText(requireContext(), R.string.log_export_failed, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.backupKeywordsButton).setOnClickListener {
            val fileName = "siteblocker_keywords_backup_${timestamp()}.json"
            val file = ShareUtil.exportAndShare(requireContext(), fileName, keywordManager.backup())
            if (file != null) {
                Toast.makeText(requireContext(), R.string.settings_backup_success, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.restoreKeywordsButton).setOnClickListener { showRestoreDialog() }

        view.findViewById<View>(R.id.resetStatsButton).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_reset_confirm_title)
                .setMessage(R.string.settings_reset_confirm_message)
                .setPositiveButton(R.string.settings_reset_statistics) { dialog, _ ->
                    statisticsManager.resetAll()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showRestoreDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_keyword, null)
        val input = dialogView.findViewById<EditText>(R.id.keywordInput)
        input.hint = "Paste backup JSON here"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.isSingleLine = false
        input.minLines = 4

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_restore_keywords)
            .setView(dialogView)
            .setPositiveButton(R.string.settings_restore_keywords) { dialog, _ ->
                val ok = keywordManager.restore(input.text?.toString().orEmpty())
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.settings_restore_success else R.string.settings_restore_failed,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupVersion(view: View) {
        val versionText = view.findViewById<TextView>(R.id.versionText)
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        versionText.text = getString(R.string.settings_version, versionName)
        versionText.setOnClickListener {
            versionTapCount++
            if (versionTapCount >= TAPS_TO_UNLOCK) {
                versionTapCount = 0
                Toast.makeText(requireContext(), R.string.dev_console_unlocked, Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), DeveloperConsoleActivity::class.java))
            }
        }
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())

    companion object {
        private const val TAPS_TO_UNLOCK = 7
    }
}
