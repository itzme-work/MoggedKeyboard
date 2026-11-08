package com.mogged.keyboard

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.io.File
import kotlin.math.roundToInt

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
        prefs = Prefs(this)
        applyAppTheme()
        setContentView(R.layout.activity_main)

        bindStatus()
        bindOnboarding()
        bindSettings()
        bindUpdates()
        refreshOnboarding()

        changelogText = findViewById(R.id.changelogText)
        updateStatus.text = getString(R.string.update_current, BuildConfig.VERSION_NAME)
        updateButton.setOnClickListener { onUpdateClicked() }

        findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        showChangelog()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshOnboarding()
        if (prefs.checkUpdates) checkUpdate(quiet = true)
    }

    /** Тема приложения следует выбору темы клавиатуры. */
    private fun applyAppTheme() {
        val mode = when (prefs.theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    // ── статус и первый запуск ───────────────────────────────
    private fun bindStatus() {
        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener { showPicker() }
    }

    private fun showPicker() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.showInputMethodPicker()
    }

    private fun refreshStatus() {
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

    private fun bindOnboarding() {
        findViewById<Button>(R.id.btnObEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnObPick).setOnClickListener { showPicker() }
        findViewById<Button>(R.id.btnObInstall).setOnClickListener {
            UpdateManager.openInstallPermission(this)
        }
        findViewById<Button>(R.id.btnObDone).setOnClickListener {
            prefs.onboarded = true
            refreshOnboarding()
        }
    }

    private fun refreshOnboarding() {
        val card = findViewById<View>(R.id.cardOnboarding)
        card.visibility = if (prefs.onboarded) View.GONE else View.VISIBLE

        val installState = findViewById<TextView>(R.id.obInstallState)
        installState.text = getString(
            if (UpdateManager.canInstall(this)) R.string.onboard_install_done
            else R.string.onboard_install_off
        )
    }

    // ── настройки ────────────────────────────────────────────
    private fun bindSettings() {
        val switchVibrate = findViewById<MaterialSwitch>(R.id.swVibrate)
        switchVibrate.isChecked = prefs.vibrate
        switchVibrate.setOnCheckedChangeListener { _, value -> prefs.vibrate = value }

        val switchSound = findViewById<MaterialSwitch>(R.id.swSound)
        switchSound.isChecked = prefs.sound
        switchSound.setOnCheckedChangeListener { _, value -> prefs.sound = value }

        val switchAuto = findViewById<MaterialSwitch>(R.id.swAutoShift)
        switchAuto.isChecked = prefs.autoShift
        switchAuto.setOnCheckedChangeListener { _, value -> prefs.autoShift = value }

        val switchNumber = findViewById<MaterialSwitch>(R.id.swNumberRow)
        switchNumber.isChecked = prefs.numberRow
        switchNumber.setOnCheckedChangeListener { _, value -> prefs.numberRow = value }

        val switchHints = findViewById<MaterialSwitch>(R.id.swHints)
        switchHints.isChecked = prefs.hints
        switchHints.setOnCheckedChangeListener { _, value -> prefs.hints = value }

        val switchPreview = findViewById<MaterialSwitch>(R.id.swPreview)
        switchPreview.isChecked = prefs.keyPreview
        switchPreview.setOnCheckedChangeListener { _, value -> prefs.keyPreview = value }

        val switchDouble = findViewById<MaterialSwitch>(R.id.swDoubleSpace)
        switchDouble.isChecked = prefs.doubleSpace
        switchDouble.setOnCheckedChangeListener { _, value -> prefs.doubleSpace = value }

        setupSlider(
            R.id.sldHeight, R.id.heightValue, 220f, 340f, 10f,
            prefs.height, R.string.height_value
        ) { prefs.height = it }

        setupSlider(
            R.id.sldRadius, R.id.radiusValue, 0f, 20f, 1f,
            prefs.keyRadius, R.string.radius_value
        ) { prefs.keyRadius = it }

        setupSlider(
            R.id.sldVibration, R.id.vibrationValue, 1f, 100f, 1f,
            prefs.vibrateStrength, R.string.vibration_value
        ) { prefs.vibrateStrength = it }

        setupSlider(
            R.id.sldVolume, R.id.volumeValue, 0f, 100f, 1f,
            prefs.volume, R.string.volume_value
        ) { prefs.volume = it }

        val group = findViewById<RadioGroup>(R.id.themeGroup)
        group.check(
            when (prefs.theme) {
                "light" -> R.id.themeLight
                "dark" -> R.id.themeDark
                "amoled" -> R.id.themeAmoled
                else -> R.id.themeSystem
            }
        )
        group.setOnCheckedChangeListener { _, id ->
            prefs.theme = when (id) {
                R.id.themeLight -> "light"
                R.id.themeDark -> "dark"
                R.id.themeAmoled -> "amoled"
                else -> "system"
            }
            applyAppTheme()
        }
    }

    private fun setupSlider(
        sliderId: Int,
        labelId: Int,
        from: Float,
        to: Float,
        step: Float,
        value: Int,
        labelRes: Int,
        save: (Int) -> Unit
    ) {
        val label = findViewById<TextView>(labelId)
        val slider = findViewById<Slider>(sliderId)

        val initial = snap(value, from, to, step)
        label.text = getString(labelRes, initial.toInt())

        // Порядок важен: сначала границы и шаг, потом значение.
        slider.valueTo = to
        slider.valueFrom = from
        slider.stepSize = step
        slider.value = initial
        slider.addOnChangeListener { _, current, _ ->
            val int = current.toInt()
            label.text = getString(labelRes, int)
            save(int)
        }
    }

    private fun snap(value: Int, from: Float, to: Float, step: Float): Float {
        val clamped = value.toFloat().coerceIn(from, to)
        val steps = ((clamped - from) / step).roundToInt()
        return (from + steps * step).coerceIn(from, to)
    }

    // ── обновления ───────────────────────────────────────────
    private fun bindUpdates() {
        val switchUpdates = findViewById<MaterialSwitch>(R.id.swUpdates)
        switchUpdates.isChecked = prefs.checkUpdates
        switchUpdates.setOnCheckedChangeListener { _, value ->
            prefs.checkUpdates = value
            if (value) checkUpdate(quiet = true)
        }

        updateStatus = findViewById(R.id.updateStatus)
        updateButton = findViewById(R.id.btnUpdate)
        updateProgress = findViewById(R.id.updateProgress)
        updateNotes = findViewById(R.id.updateNotes)
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
                    val percent = (done.toLong() * 100L / total).toInt()
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
                if (!UpdateManager.canInstall(this)) refreshOnboarding()

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
