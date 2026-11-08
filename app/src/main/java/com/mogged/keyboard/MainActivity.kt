package com.mogged.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var updateStatus: TextView
    private lateinit var updateButton: Button
    private lateinit var updateProgress: ProgressBar
    private lateinit var updateNotes: TextView
    private lateinit var changelogText: TextView

    private var pendingUpdate: UpdateManager.Update? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val switchVibrate = findViewById<MaterialSwitch>(R.id.swVibrate)
        switchVibrate.isChecked = prefs.vibrate
        switchVibrate.setOnCheckedChangeListener { _, value -> prefs.vibrate = value }

        val switchSound = findViewById<MaterialSwitch>(R.id.swSound)
        switchSound.isChecked = prefs.sound
        switchSound.setOnCheckedChangeListener { _, value -> prefs.sound = value }

        val switchAuto = findViewById<MaterialSwitch>(R.id.swAutoShift)
        switchAuto.isChecked = prefs.autoShift
        switchAuto.setOnCheckedChangeListener { _, value -> prefs.autoShift = value }

        val switchUpdates = findViewById<MaterialSwitch>(R.id.swUpdates)
        switchUpdates.isChecked = prefs.checkUpdates
        switchUpdates.setOnCheckedChangeListener { _, value ->
            prefs.checkUpdates = value
            if (value) checkUpdate(quiet = true)
        }

        val heightValue = findViewById<TextView>(R.id.heightValue)
        heightValue.text = getString(R.string.height_value, prefs.height)

        val slider = findViewById<Slider>(R.id.sldHeight)
        // Порядок важен: сначала верхняя граница, потом нижняя.
        slider.valueTo = 340f
        slider.valueFrom = 220f
        slider.stepSize = 10f
        slider.value = prefs.height.toFloat().coerceIn(220f, 340f)
        slider.addOnChangeListener { _, value, _ ->
            prefs.height = value.toInt()
            heightValue.text = getString(R.string.height_value, value.toInt())
        }

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showInputMethodPicker()
        }

        updateStatus = findViewById(R.id.updateStatus)
        updateButton = findViewById(R.id.btnUpdate)
        updateProgress = findViewById(R.id.updateProgress)
        updateNotes = findViewById(R.id.updateNotes)
        changelogText = findViewById(R.id.changelogText)

        updateStatus.text = getString(R.string.update_current, BuildConfig.VERSION_NAME)
        updateButton.setOnClickListener { onUpdateClicked() }

        showChangelog()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (prefs.checkUpdates) checkUpdate(quiet = true)
    }

    // ── статус клавиатуры ────────────────────────────────────
    private fun updateStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val selected = Settings.Secure
            .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true

        val text = when {
            selected -> getString(R.string.status_ready)
            enabled -> getString(R.string.status_enabled)
            else -> getString(R.string.status_off)
        }
        findViewById<TextView>(R.id.statusText).text = text
    }

    // ── список обновлений ────────────────────────────────────
    private fun showChangelog() {
        val builder = SpannableStringBuilder()
        Changelog.releases.forEachIndexed { index, release ->
            if (index > 0) builder.append("\n\n")

            val header = "${release.version} — «${release.name}»"
            val start = builder.length
            builder.append(header)
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            release.items.forEach { item ->
                builder.append("\n• ").append(item)
            }
        }
        changelogText.text = builder
    }

    private fun showUpdateNotes(update: UpdateManager.Update) {
        if (update.changelog.isEmpty()) {
            updateNotes.visibility = View.GONE
            return
        }

        val title = buildString {
            append(update.versionName)
            if (update.name.isNotEmpty()) append(" — «").append(update.name).append('»')
        }
        val builder = SpannableStringBuilder(title)
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        update.changelog.forEach { builder.append("\n• ").append(it) }

        updateNotes.text = builder
        updateNotes.visibility = View.VISIBLE
    }

    // ── обновления ───────────────────────────────────────────
    private fun checkUpdate(quiet: Boolean) {
        if (busy) return

        if (!quiet) updateStatus.text = getString(R.string.update_checking)

        Thread {
            val result = UpdateManager.check()
            runOnUiThread {
                when (result) {
                    is UpdateManager.Check.Found -> showUpdate(result.update)
                    is UpdateManager.Check.UpToDate -> {
                        pendingUpdate = null
                        updateNotes.visibility = View.GONE
                        updateStatus.text =
                            getString(R.string.update_fresh, BuildConfig.VERSION_NAME)
                        updateButton.text = getString(R.string.update_check)
                        updateButton.visibility = View.VISIBLE
                    }
                    is UpdateManager.Check.Failed -> {
                        if (!quiet) {
                            updateStatus.text = when (result.reason) {
                                UpdateManager.Reason.NETWORK -> getString(R.string.update_none)
                                UpdateManager.Reason.BAD_DATA -> getString(R.string.update_bad_data)
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun showUpdate(update: UpdateManager.Update) {
        pendingUpdate = update
        updateStatus.text = getString(
            R.string.update_available,
            update.versionName
        )
        updateButton.text = getString(R.string.update_download)
        updateButton.visibility = View.VISIBLE
        showUpdateNotes(update)
    }

    private fun onUpdateClicked() {
        if (busy) return

        val found = pendingUpdate
        if (found == null || found.versionCode <= BuildConfig.VERSION_CODE) {
            updateButton.text = getString(R.string.update_check)
            checkUpdate(quiet = false)
            return
        }

        if (!UpdateManager.canInstall(this)) {
            updateStatus.text = getString(R.string.update_allow)
            UpdateManager.openInstallPermission(this)
            return
        }

        startDownload(found)
    }

    private fun startDownload(update: UpdateManager.Update) {
        busy = true
        updateProgress.visibility = View.VISIBLE
        updateProgress.progress = 0
        updateButton.isEnabled = false
        updateStatus.text = getString(R.string.update_downloading)

        val target = File(cacheDir, "updates/" + UpdateManager.APK_NAME)

        Thread {
            var lastPercent = -1
            val result = UpdateManager.download(
                update.apkUrl,
                target,
                update.sha256
            ) { done, total ->
                if (total > 0) {
                    val percent = (done * 100L / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        runOnUiThread { updateProgress.progress = percent }
                    }
                }
            }

            runOnUiThread {
                busy = false
                updateButton.isEnabled = true
                updateProgress.visibility = View.GONE
                updateButton.text = getString(R.string.update_download)

                updateStatus.text = when (result) {
                    is UpdateManager.Download.Ok -> {
                        if (UpdateManager.install(this, target)) {
                            getString(R.string.update_ready)
                        } else {
                            getString(R.string.update_install_failed)
                        }
                    }
                    is UpdateManager.Download.Network -> getString(R.string.update_error)
                    is UpdateManager.Download.NoSpace -> getString(R.string.update_no_space)
                    is UpdateManager.Download.Corrupt -> getString(R.string.update_corrupt)
                }
            }
        }.start()
    }
}
