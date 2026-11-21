package com.duoc.prestaaula.nativeaccess

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Exportación de reportes. Se escribe un CSV (separado por punto y coma, UTF-8 con BOM) que
 * Excel abre directamente, y se comparte mediante el selector de aplicaciones de Android.
 */
object ExportHelper {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun writeCsv(context: Context, baseName: String, content: String): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "${baseName}_${LocalDateTime.now().format(stamp)}.csv")
        // El BOM hace que Excel reconozca las tildes y la ñ.
        file.writeText("﻿$content", Charsets.UTF_8)
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Exportar reporte").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
