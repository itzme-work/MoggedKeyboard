package com.mogged.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

        updateStatus.text = getString(R.string.update_current, BuildConfig.VERSION_NAME)
        updateButton.setOnClickListener { onUpdateClicked() }
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

    // ── обновления ───────────────────────────────────────────
    private fun checkUpdate(quiet: Boolean) {
        if (busy) return
        if (quiet) {
            Thread {
                val found = UpdateManager.check()
                if (found != null) runOnUiThread { showUpdate(found) }
            }.start()
        } else {
            updateStatus.text = getString(R.string.update_checking)
            Thread {
                val found = UpdateManager.check()
                runOnUiThread {
                    when {
                        found == null -> updateStatus.text = getString(R.string.update_none)
                        else -> showUpdate(found)
                    }
                }
            }.start()
        }
    }

    private fun showUpdate(found: UpdateManager.Update) {
        pendingUpdate = found
        if (found.versionCode > BuildConfig.VERSION_CODE) {
            updateStatus.text =
                getString(R.string.update_available, "1.${found.versionCode}")
            updateButton.text = getString(R.string.update_download)
            updateButton.visibility = View.VISIBLE
        } else {
            updateStatus.text =
                getString(R.string.update_fresh, BuildConfig.VERSION_NAME)
            updateButton.text = getString(R.string.update_check)
        }
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

    private fun startDownload(found: UpdateManager.Update) {
        busy = true
        updateProgress.visibility = View.VISIBLE
        updateProgress.progress = 0
        updateButton.isEnabled = false
        updateStatus.text = getString(R.string.update_downloading)

        val target = File(cacheDir, "updates/MoggedKeyboard.apk")

        Thread {
            val ok = UpdateManager.download(found.apkUrl, target) { done, total ->
                if (total > 0) {
                    val percent = (done * 100L / total).toInt()
                    runOnUiThread { updateProgress.progress = percent }
                }
            }

            runOnUiThread {
                busy = false
                updateButton.isEnabled = true
                updateProgress.visibility = View.GONE

                if (ok) {
                    updateStatus.text = getString(R.string.update_ready)
                    if (!UpdateManager.install(this, target)) {
                        updateStatus.text = getString(R.string.update_error)
                    }
                } else {
                    updateStatus.text = getString(R.string.update_error)
                }
            }
        }.start()
    }
}
