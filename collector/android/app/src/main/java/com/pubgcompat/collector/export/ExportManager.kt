/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes fingerprints to app-private storage and exposes them through the standard Android sharing
 * mechanism. No personal data is collected or exported.
 */
object ExportManager {

  const val FILE_NAME = "fingerprint.json"

  fun externalFile(context: Context): File =
      File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

  fun internalFile(context: Context): File = File(context.filesDir, FILE_NAME)

  /** Writes the fingerprint to both app-private internal and external storage. */
  fun export(context: Context, content: String): File {
    val external = externalFile(context)
    external.parentFile?.mkdirs()
    external.writeText(content)
    internalFile(context).writeText(content)
    return external
  }

  /** Builds an ACTION_SEND intent exposing the fingerprint via FileProvider. */
  fun shareIntent(context: Context, file: File): Intent {
    val uri: Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    return Intent(Intent.ACTION_SEND).apply {
      type = "application/json"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }
}
