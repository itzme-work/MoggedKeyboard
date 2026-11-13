package com.mogged.keyboard

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.textfield.TextInputEditText

/**
 * Песочница: место, где клавиатуру можно проверить на практике.
 *
 * Живой предпросмотр показывает текущее оформление, а поле ввода сразу
 * открывает настоящую клавиатуру — с тем же акцентом, формой и прозрачностью.
 * Ничего не сохраняет: только читает настройки.
 */
class SandboxActivity : AppCompatActivity() {

    private lateinit var preview: MiniKeyboardPreview
    private lateinit var field: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MoggedApp.applyNightMode(Prefs(this).theme)
        DynamicColors.applyToActivityIfAvailable(this, MoggedApp.dynamicOptions(this))
        setContentView(R.layout.activity_sandbox)

        preview = findViewById(R.id.sandboxPreview)
        field = findViewById(R.id.sandboxField)

        findViewById<MaterialToolbar>(R.id.sandboxToolbar)
            .setNavigationOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        findViewById<Button>(R.id.btnSandboxShow).setOnClickListener {
            field.requestFocus()
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }

        findViewById<Button>(R.id.btnSandboxClear).setOnClickListener {
            field.setText("")
            preview.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        // Оформление могли поменять в разделе «Вид», пока экран был скрыт.
        preview.refresh()
    }
}
