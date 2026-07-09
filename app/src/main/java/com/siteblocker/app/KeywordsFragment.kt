package com.siteblocker.app

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

class KeywordsFragment : Fragment() {

    private lateinit var keywordManager: KeywordManager
    private lateinit var adapter: KeywordAdapter
    private var currentSort = KeywordSort.ALPHABETICAL
    private var currentQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_keywords, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keywordManager = KeywordManager(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.keywordRecyclerView)
        adapter = KeywordAdapter(
            mutableListOf(),
            onDeleteClick = { keyword ->
                keywordManager.removeKeyword(keyword)
                refreshKeywordList()
                Toast.makeText(requireContext(), "${getString(R.string.delete)}: $keyword", Toast.LENGTH_SHORT).show()
            },
            onEditClick = { keyword -> showEditKeywordDialog(keyword) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.addKeywordButton).setOnClickListener { showAddKeywordDialog() }
        view.findViewById<View>(R.id.importButton).setOnClickListener { showImportDialog() }
        view.findViewById<View>(R.id.exportButton).setOnClickListener { exportKeywords() }

        val sortButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.sortButton)
        sortButton.setOnClickListener {
            currentSort = if (currentSort == KeywordSort.ALPHABETICAL) KeywordSort.RECENTLY_ADDED else KeywordSort.ALPHABETICAL
            sortButton.setText(if (currentSort == KeywordSort.ALPHABETICAL) R.string.sort_alphabetical else R.string.sort_recent)
            refreshKeywordList()
        }

        view.findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                refreshKeywordList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refreshKeywordList()
    }

    private fun refreshKeywordList() {
        val view = view ?: return
        val keywords = if (currentQuery.isBlank()) {
            keywordManager.getKeywordsSorted(currentSort)
        } else {
            keywordManager.search(currentQuery)
        }
        adapter.updateKeywords(keywords)
        view.findViewById<View>(R.id.emptyStateText).visibility = if (keywords.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.keywordRecyclerView).visibility = if (keywords.isEmpty()) View.GONE else View.VISIBLE
        AppState.totalKeywords.postValue(keywordManager.getKeywords().size)
    }

    private fun showAddKeywordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_keyword, null)
        val input = dialogView.findViewById<EditText>(R.id.keywordInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_keyword_title)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { dialog, _ ->
                handleAddKeyword(input.text?.toString().orEmpty())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showEditKeywordDialog(existing: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_keyword, null)
        val input = dialogView.findViewById<EditText>(R.id.keywordInput)
        input.setText(existing)
        input.setSelection(existing.length)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_edit_keyword_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newValue = input.text?.toString().orEmpty()
                if (TextUtils.isEmpty(newValue.trim())) {
                    Toast.makeText(requireContext(), R.string.keyword_empty, Toast.LENGTH_SHORT).show()
                } else if (!keywordManager.editKeyword(existing, newValue)) {
                    Toast.makeText(requireContext(), R.string.keyword_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    refreshKeywordList()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun handleAddKeyword(raw: String) {
        if (TextUtils.isEmpty(raw.trim())) {
            Toast.makeText(requireContext(), R.string.keyword_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val normalized = keywordManager.normalize(raw)
        if (!keywordManager.isValid(normalized)) {
            Toast.makeText(requireContext(), R.string.keyword_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        if (keywordManager.getKeywords().contains(normalized)) {
            Toast.makeText(requireContext(), R.string.keyword_exists, Toast.LENGTH_SHORT).show()
            return
        }
        keywordManager.addKeyword(raw)
        refreshKeywordList()
    }

    private fun showImportDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_keyword, null)
        val input = dialogView.findViewById<EditText>(R.id.keywordInput)
        input.hint = "One keyword per line"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.isSingleLine = false
        input.minLines = 3

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.btn_import)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_import) { dialog, _ ->
                val count = keywordManager.importFromText(input.text?.toString().orEmpty())
                Toast.makeText(requireContext(), getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                refreshKeywordList()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun exportKeywords() {
        val fileName = "siteblocker_keywords_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.txt"
        val file = ShareUtil.exportAndShare(requireContext(), fileName, keywordManager.exportAsText())
        if (file == null) {
            Toast.makeText(requireContext(), R.string.log_export_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
