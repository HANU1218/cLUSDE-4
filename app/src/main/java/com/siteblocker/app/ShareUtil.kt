package com.siteblocker.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Small helper used by every "Export" button so the behavior is consistent. */
object ShareUtil {

    /**
     * Writes [content] to a file named [fileName] in the app's external files
     * dir and launches a share sheet for it. Returns the resulting file, or
     * null if the write failed (caller should show an error toast).
     */
    fun exportAndShare(context: Context, fileName: String, content: String): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            file
        } catch (e: Exception) {
            Logger.e("ShareUtil", "Export failed for $fileName", e)
            null
        }
    }
}
