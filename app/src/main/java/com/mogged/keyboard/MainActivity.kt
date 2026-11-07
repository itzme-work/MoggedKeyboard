package com.mogged.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

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
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

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
}
