/*
 * Copyright (c) 2026 pubg-compat-research contributors
 *
 * Licensed under the MIT License.
 */
package com.pubgcompat.collector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pubgcompat.collector.databinding.ActivityMainBinding
import com.pubgcompat.collector.export.ExportManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Intents: extra_env_label (String), extra_auto_export (Boolean). */
class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private val model: CollectorViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    model.output.observe(this) { binding.output.text = it }
    model.hasFingerprint.observe(this) { binding.btnExport.isEnabled = it }
    model.hasFingerprint.observe(this) { binding.btnShare.isEnabled = it }
    model.hasFingerprint.observe(this) { binding.btnCopy.isEnabled = it }

    val envLabel = intent.getStringExtra(EXTRA_ENV_LABEL) ?: "android"
    binding.btnCollect.setOnClickListener { model.collect(this, envLabel) }
    binding.btnExport.setOnClickListener {
      model.fingerprint()?.let { fp ->
        val file = ExportManager.export(this, fp)
        toast("Exported: ${file.absolutePath}")
      }
    }
    binding.btnShare.setOnClickListener {
      model.fingerprint()?.let { fp ->
        val file = ExportManager.export(this, fp)
        startActivity(ExportManager.shareIntent(this, file))
      }
    }
    binding.btnCopy.setOnClickListener {
      model.fingerprint()?.let { fp ->
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("fingerprint.json", fp))
        toast("Copied ${fp.length} chars")
      }
    }

    val autoExport = intent.getBooleanExtra(EXTRA_AUTO_EXPORT, false)
    if (autoExport) {
      model.collect(this, envLabel) { fp ->
        val file = ExportManager.export(this, fp)
        println("FINGERPRINT_EXPORTED:$file")
        toast("Exported: ${file.absolutePath}")
      }
    }
  }

  private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

  companion object {
    const val EXTRA_ENV_LABEL = "extra_env_label"
    const val EXTRA_AUTO_EXPORT = "extra_auto_export"
  }
}

class CollectorViewModel : ViewModel() {

  private val mutableOutput = androidx.lifecycle.MutableLiveData<String>("Ready")
  val output: androidx.lifecycle.LiveData<String> = mutableOutput

  private val mutableHasFingerprint = androidx.lifecycle.MutableLiveData(false)
  val hasFingerprint: androidx.lifecycle.LiveData<Boolean> = mutableHasFingerprint

  private var fingerprintText: String? = null
  private var fingerprintHash: String? = null

  fun fingerprint(): String? = fingerprintText

  fun collect(context: Context, environmentLabel: String, onDone: ((String) -> Unit)? = null) {
    viewModelScope.launch {
      mutableOutput.postValue("Collecting…")
      val engine = CollectorEngine(context, environmentLabel, CollectorRegistry.all())
      val (document, hash) = engine.run()
      fingerprintHash = hash
      fingerprintText = serialize(document)
      mutableHasFingerprint.postValue(true)
      val summary = buildString {
        append("fingerprint_sha256: $hash\n")
        append("collectors: ${CollectorRegistry.all().size}\n\n")
        append(prettySummary(document))
      }
      mutableOutput.postValue(summary)
      onDone?.invoke(fingerprintText ?: "")
    }
  }

  private fun serialize(document: JsonObject): String =
      Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), document)

  private fun prettySummary(document: JsonObject): String {
    val summary = document["collector_status_summary"] as? JsonObject ?: return "no status summary"
    return buildString {
      for ((name, status) in summary) {
        val s = (status as? JsonPrimitive)?.content ?: "unknown"
        append(name.padEnd(20)).append(s).append('\n')
      }
    }
  }
}
