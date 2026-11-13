package com.mrafshun20.lasernesting.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mrafshun20.lasernesting.nesting.NestResult
import java.io.File

/**
 * Cut-list (CSV) and nest-layout (SVG) export, shared through the
 * Android share sheet. Files are written to the app cache directory and
 * shared with a FileProvider URI — no storage permission is needed.
 * The CSV/SVG strings themselves come from the shared core (ExportCore),
 * so the desktop app produces identical files.
 */
object Export {

    /** CSV cut list: one row per placed part, across all sheets. */
    fun cutListCsv(result: NestResult, sheetWidthMm: Double, sheetLengthMm: Double): String =
        ExportCore.cutListCsv(result, sheetWidthMm, sheetLengthMm)

    /** SVG layout of every used sheet, stacked vertically with a label. */
    fun nestSvg(result: NestResult, sheetWidthMm: Double, sheetLengthMm: Double): String =
        ExportCore.nestSvg(result, sheetWidthMm, sheetLengthMm)

    /** Shares several text files at once (e.g. one DXF per sheet). */
    fun shareFiles(
        context: Context,
        files: List<Pair<String, String>>,
        mimeType: String,
        chooserTitle: String
    ) {
        if (files.isEmpty()) return
        val uris = ArrayList<android.net.Uri>()
        for ((name, content) in files) {
            val file = File(context.cacheDir, name)
            file.writeText(content)
            uris.add(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            )
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** Writes [content] to the cache dir and opens the Android share sheet. */
    fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        chooserTitle: String
    ) {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
