package com.mogged.keyboard

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.widget.NestedScrollView
import androidx.transition.TransitionManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.slider.Slider
import com.google.android.material.transition.MaterialSharedAxis
import java.io.File
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private companion object {
        const val SECTION_HOME = 0
        const val SECTION_LOOK = 1
        const val SECTION_INPUT = 2
        const val SECTION_FEEL = 3
        const val SECTION_MORE = 4

        const val KEY_SECTION = "section"
        const val KEY_SCROLL = "scroll"

        const val SHARED_AXIS_MS = 220L

        /** Рейка разделов появляется на широких экранах, от 840dp. */
        const val RAIL_MIN_WIDTH_DP = 840

        val SECTION_TITLES = intArrayOf(
            R.string.sec_home,
            R.string.sec_look,
            R.string.sec_input,
            R.string.sec_feel,
            R.string.sec_more
        )

        val SECTION_IDS = intArrayOf(
            R.id.nav_home,
            R.id.nav_look,
            R.id.nav_input,
            R.id.nav_feel,
            R.id.nav_more
        )

        val SECTION_VIEWS = intArrayOf(
            R.id.secHome,
            R.id.secLook,
            R.id.secInput,
            R.id.secFeel,
            R.id.secMore
        )
    }

    private lateinit var prefs: Prefs

    private lateinit var collapsingToolbar: CollapsingToolbarLayout
    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var navRail: NavigationRailView
    private lateinit var sectionHost: FrameLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var miniPreview: MiniKeyboardPreview

    private lateinit var updateStatus: TextView
    private lateinit var updateButton: Button
    private lateinit var updateProgress: ProgressBar
    private lateinit var updateNotes: TextView
    private lateinit var updateHomeCard: View
    private lateinit var updateHomeText: TextView
    private lateinit var changelogText: TextView
    private lateinit var homeWhatsNew: TextView

    private var section = SECTION_HOME
    private var sectionsReady = false

    private var pendingUpdate: UpdateManager.Update? = null
    private var busy = false

    /** Экспорт: системный диалог «создать документ». */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) saveBackup(uri)
    }

    /** Импорт: системный диалог «открыть документ». */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API: системный экран запуска до первого кадра.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        MoggedApp.applyNightMode(prefs.theme)
        // Акцент перекрашивает и приложение: опции строятся заново для этого
        // экрана, поэтому новый акцент виден сразу после recreate().
        DynamicColors.applyToActivityIfAvailable(this, MoggedApp.dynamicOptions(this))
        setContentView(R.layout.activity_main)

        collapsingToolbar = findViewById(R.id.collapsingToolbar)
        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottomNav)
        navRail = findViewById(R.id.navRail)
        sectionHost = findViewById(R.id.sectionHost)
        scroll = findViewById(R.id.scroll)
        miniPreview = findViewById(R.id.miniPreview)

        bindNavigation()
        bindBack()
        bindStatus()
        bindOnboarding()
        bindSettings()
        bindBackup()
        bindSandbox()
        bindUpdates()

        changelogText = findViewById(R.id.changelogText)
        homeWhatsNew = findViewById(R.id.homeWhatsNew)
        updateStatus.text = getString(R.string.update_current, BuildConfig.VERSION_NAME)
        updateButton.setOnClickListener { onUpdateClicked() }

        findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        showChangelog()
        refreshOnboarding()

        // Поворот экрана возвращает тот же раздел и ту же прокрутку.
        val restoredSection = savedInstanceState?.getInt(KEY_SECTION, SECTION_HOME) ?: SECTION_HOME
        val restoredScroll = savedInstanceState?.getInt(KEY_SCROLL, 0) ?: 0
        showSection(restoredSection, animate = false)
        if (restoredScroll > 0) {
            scroll.post { scroll.scrollTo(0, restoredScroll) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshOnboarding()
        if (prefs.checkUpdates) checkUpdate(quiet = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SECTION, section)
        outState.putInt(KEY_SCROLL, scroll.scrollY)
    }

    // ── разделы и навигация ─────────────────────────────────
    private fun bindNavigation() {
        // На широких экранах (от 840dp) нижнюю панель заменяет рейка.
        val wide = resources.configuration.screenWidthDp >= RAIL_MIN_WIDTH_DP
        navRail.visibility = if (wide) View.VISIBLE else View.GONE
        bottomNav.visibility = if (wide) View.GONE else View.VISIBLE

        val listener = NavigationBarView.OnItemSelectedListener { item ->
            val index = SECTION_IDS.indexOf(item.itemId)
            if (index >= 0 && index != section) showSection(index)
            true
        }
        bottomNav.setOnItemSelectedListener(listener)
        navRail.setOnItemSelectedListener(listener)
    }

    /**
     * Предиктивный жест «назад»: с любого раздела возвращаемся на главную,
     * а не выходим из приложения сразу. Система сама показывает анимацию.
     */
    private fun bindBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (section != SECTION_HOME) {
                    showSection(SECTION_HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun showSection(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, SECTION_VIEWS.size - 1)

        if (animate && sectionsReady && target != section) {
            // Shared axis: разделы сменяют друг друга вдоль оси X.
            val forward = target > section
            val transition = MaterialSharedAxis(MaterialSharedAxis.X, forward)
                .setDuration(SHARED_AXIS_MS)
            TransitionManager.beginDelayedTransition(sectionHost, transition)
        }

        SECTION_VIEWS.forEachIndexed { i, id ->
            sectionHost.findViewById<View>(id).visibility =
                if (i == target) View.VISIBLE else View.GONE
        }

        section = target
        collapsingToolbar.title = getString(SECTION_TITLES[target])
        toolbar.title = ""
        // Новый раздел открывается сверху: заголовок снова большой.
        appBar.setExpanded(true, animate)

        // Отмечаем пункт без вызова слушателей — иначе будет рекурсия.
        bottomNav.menu.findItem(SECTION_IDS[target])?.isChecked = true
        navRail.menu.findItem(SECTION_IDS[target])?.isChecked = true

        if (animate && sectionsReady) {
            scroll.scrollTo(0, 0)
        }
        sectionsReady = true
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
        bindSwitch(R.id.swVibrate, prefs.vibrate) { prefs.vibrate = it }
        bindSwitch(R.id.swSound, prefs.sound) { prefs.sound = it }
        bindSwitch(R.id.swAutoShift, prefs.autoShift) { prefs.autoShift = it }
        bindSwitch(R.id.swNumberRow, prefs.numberRow) { prefs.numberRow = it }
        bindSwitch(R.id.swHints, prefs.hints) { prefs.hints = it }
        bindSwitch(R.id.swPreview, prefs.keyPreview) { prefs.keyPreview = it }
        bindSwitch(R.id.swDoubleSpace, prefs.doubleSpace) { prefs.doubleSpace = it }
        bindSwitch(R.id.swHapticStrong, prefs.hapticStrong) { prefs.hapticStrong = it }
        bindSwitch(R.id.swLangKey, prefs.langKey) { prefs.langKey = it }
        bindSwitch(R.id.swOutline, prefs.keyOutline) {
            prefs.keyOutline = it
            miniPreview.refresh()
        }
        // Палитра выбирается при создании экрана, поэтому переключатель
        // сразу пересоздаёт его — клавиатура подхватит цвет при следующем показе.
        bindSwitch(R.id.swDynamic, prefs.dynamicColor) {
            prefs.dynamicColor = it
            recreate()
        }
        if (!DynamicColors.isDynamicColorAvailable()) {
            findViewById<TextView>(R.id.dynamicHint).setText(R.string.dynamic_unavailable)
        }
        bindSwitch(R.id.swBackspaceWord, prefs.backspaceWord) { prefs.backspaceWord = it }
        bindSwitch(R.id.swReleaseHaptic, prefs.releaseHaptic) { prefs.releaseHaptic = it }
        bindSwitch(R.id.swOneHandPanel, prefs.oneHandPanel) { prefs.oneHandPanel = it }

        setupSlider(
            R.id.sldHeight, R.id.heightValue, 220f, 340f, 10f,
            prefs.height, R.string.height_value_short
        ) {
            prefs.height = it
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldRadius, R.id.radiusValue, 0f, 20f, 1f,
            prefs.keyRadius, R.string.radius_value_short
        ) {
            prefs.keyRadius = it
            prefs.preset = Look.PRESET_CUSTOM
            refreshPresetSelection()
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldWidth, R.id.widthValue, 50f, 100f, 5f,
            prefs.width, R.string.width_value_short
        ) {
            prefs.width = it
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldVibration, R.id.vibrationValue, 1f, 100f, 1f,
            prefs.vibrateStrength, R.string.vibration_value_short
        ) { prefs.vibrateStrength = it }

        setupSlider(
            R.id.sldHapticDuration, R.id.hapticDurationValue, 5f, 60f, 1f,
            prefs.customHapticDuration, R.string.haptic_duration_value_short
        ) { prefs.customHapticDuration = it }

        setupSlider(
            R.id.sldFontScale, R.id.fontScaleValue, 80f, 120f, 5f,
            prefs.keyFontScale, R.string.font_scale_value_short
        ) {
            prefs.keyFontScale = it
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldVolume, R.id.volumeValue, 0f, 100f, 1f,
            prefs.volume, R.string.volume_value_short
        ) { prefs.volume = it }

        bindTheme()
        bindOneHand()
        bindHaptics()
        bindSpaceSwipe()
        bindPresets()
        bindAccent()
        bindContrast()
        bindShape()

        setupSlider(
            R.id.sldFuncRadius, R.id.funcRadiusValue, 0f, 28f, 1f,
            prefs.funcRadius, R.string.radius_value_short
        ) {
            prefs.funcRadius = it
            prefs.preset = Look.PRESET_CUSTOM
            refreshPresetSelection()
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldElevation, R.id.elevationValue, 0f, 4f, 1f,
            prefs.elevation, R.string.radius_value_short
        ) {
            prefs.elevation = it
            prefs.preset = Look.PRESET_CUSTOM
            refreshPresetSelection()
            miniPreview.refresh()
        }

        setupSlider(
            R.id.sldKeyAlpha, R.id.keyAlphaValue, 0f, 100f, 5f,
            prefs.keyAlpha, R.string.key_alpha_value_short
        ) {
            prefs.keyAlpha = it
            miniPreview.refresh()
        }

        bindSwitch(R.id.swGradient, prefs.background == "gradient") {
            prefs.background = if (it) "gradient" else "solid"
            miniPreview.refresh()
        }
        bindSwitch(R.id.swAnimations, prefs.animations) { prefs.animations = it }

        findViewById<Button>(R.id.btnResetLook).setOnClickListener {
            confirmReset { resetLook() }
        }
        findViewById<Button>(R.id.btnResetInput).setOnClickListener {
            confirmReset { resetInput() }
        }
        findViewById<Button>(R.id.btnResetFeel).setOnClickListener {
            confirmReset { resetFeel() }
        }
        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            confirmReset { Backup.applyTo(prefs, Backup.DEFAULT) }
        }
    }

    private fun bindTheme() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.themeGroup)
        group.check(
            when (prefs.theme) {
                "light" -> R.id.themeLight
                "dark" -> R.id.themeDark
                "amoled" -> R.id.themeAmoled
                else -> R.id.themeSystem
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.theme = when (checkedId) {
                R.id.themeLight -> "light"
                R.id.themeDark -> "dark"
                R.id.themeAmoled -> "amoled"
                else -> "system"
            }
            MoggedApp.applyNightMode(prefs.theme)
            // Тёмная и AMOLED дают один и тот же ночной режим, поэтому
            // экран не пересоздаётся — предпросмотр перекрашиваем руками.
            miniPreview.refresh()
        }
    }

    private fun bindOneHand() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.oneHandGroup)
        group.check(
            when (prefs.oneHand) {
                "left" -> R.id.oneHandLeft
                "right" -> R.id.oneHandRight
                else -> R.id.oneHandOff
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.oneHand = when (checkedId) {
                R.id.oneHandLeft -> "left"
                R.id.oneHandRight -> "right"
                else -> "off"
            }
            miniPreview.refresh()
        }
    }

    private fun bindHaptics() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.hapticGroup)
        group.check(
            when (Haptics.parse(prefs.hapticStyle)) {
                HapticStyle.TICK -> R.id.hapticTick
                HapticStyle.STRONG -> R.id.hapticStrong
                HapticStyle.CUSTOM -> R.id.hapticCustom
                HapticStyle.CLICK -> R.id.hapticClick
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.hapticStyle = when (checkedId) {
                R.id.hapticTick -> Haptics.key(HapticStyle.TICK)
                R.id.hapticStrong -> Haptics.key(HapticStyle.STRONG)
                R.id.hapticCustom -> Haptics.key(HapticStyle.CUSTOM)
                else -> Haptics.key(HapticStyle.CLICK)
            }
            // Сила и длительность нужны только «Своему» отклику.
            updateVibrationUi()
            // Сразу показываем, как звучит выбранный характер.
            sampleHaptic()
        }
        updateVibrationUi()
    }

    private fun bindSpaceSwipe() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.spaceSwipeGroup)
        group.check(
            when (Gestures.parseSpaceSwipe(prefs.spaceSwipe)) {
                SpaceSwipe.CURSOR -> R.id.swipeCursor
                SpaceSwipe.OFF -> R.id.swipeOff
                SpaceSwipe.LANG -> R.id.swipeLang
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.spaceSwipe = when (checkedId) {
                R.id.swipeCursor -> Gestures.spaceSwipeKey(SpaceSwipe.CURSOR)
                R.id.swipeOff -> Gestures.spaceSwipeKey(SpaceSwipe.OFF)
                else -> Gestures.spaceSwipeKey(SpaceSwipe.LANG)
            }
        }
    }

    // ── оформление: пресеты, акцент, контраст, форма ─────────
    /** Карточки пресетов: живые мини-предпросмотры. */
    private fun bindPresets() {
        val row = findViewById<LinearLayout>(R.id.presetRow)
        row.removeAllViews()

        val onSurface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, 0
        )
        Look.presets.forEach { preset ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) }
                radius = dp(20).toFloat()
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                contentDescription = preset.name
                tag = preset.id
                setOnClickListener { applyPreset(preset) }
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            column.addView(
                MiniKeyboardPreview(this).apply {
                    presetOverride = preset
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
                    )
                }
            )
            column.addView(
                TextView(this).apply {
                    text = preset.name
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(onSurface)
                    setPadding(0, dp(4), 0, dp(2))
                }
            )
            card.addView(column)
            row.addView(card)
        }
        refreshPresetSelection()
    }

    /** Выделяет карточку выбранного пресета; ручные изменения снимают выбор. */
    private fun refreshPresetSelection() {
        val row = findViewById<LinearLayout>(R.id.presetRow)
        val selected = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, 0
        )
        val idle = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutlineVariant, 0
        )
        for (i in 0 until row.childCount) {
            val card = row.getChildAt(i) as? MaterialCardView ?: continue
            val active = card.tag == prefs.preset
            card.strokeWidth = dp(if (active) 2 else 1)
            card.strokeColor = if (active) selected else idle
        }
    }

    /** Пресет задаёт акцент, радиус, форму и тень, и сразу всё применяет. */
    private fun applyPreset(preset: Preset) {
        prefs.preset = preset.id
        // Пресет берёт один из восьми акцентов — подсветим его кружок.
        prefs.accent = Look.accents.firstOrNull { it.color == preset.accent }?.id
            ?: Look.seedKey(preset.accent)
        prefs.keyRadius = preset.radius
        prefs.funcRadius = (preset.radius + 6).coerceAtMost(28)
        prefs.shape = KeyShape.key(preset.shape)
        prefs.elevation = preset.elevation
        // Акцент перекрашивает и приложение: пересоздаём экран.
        recreate()
    }

    private fun bindAccent() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        row.removeAllViews()

        val strokeIdle = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutlineVariant, 0
        )
        // «Из обоев»: кружок с переливом всех акцентов.
        row.addView(
            chip(Look.ACCENT_AUTO, getString(R.string.accent_auto)).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    colors = Look.accents.map { it.color }.toIntArray()
                    setStroke(dp(2), strokeIdle)
                }
            }
        )
        Look.accents.forEach { accent ->
            row.addView(chip(accent.id, accent.name).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent.color)
                    setStroke(dp(2), strokeIdle)
                }
            })
        }
        refreshAccentSelection()

        findViewById<Button>(R.id.btnCustomAccent).setOnClickListener { showAccentDialog() }

        if (!DynamicColors.isDynamicColorAvailable()) {
            findViewById<TextView>(R.id.accentHint).setText(R.string.accent_hint)
        }
    }

    private fun chip(accentId: String, label: String): View {
        val size = dp(44)
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) }
            isClickable = true
            isFocusable = true
            contentDescription = label
            tag = accentId
            setOnClickListener { selectAccent(accentId) }
        }
    }

    private fun refreshAccentSelection() {
        val row = findViewById<LinearLayout>(R.id.accentRow)
        val selected = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, 0
        )
        val idle = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutlineVariant, 0
        )
        val width = dp(3)
        for (i in 0 until row.childCount) {
            val view = row.getChildAt(i)
            val drawable = view.background as? GradientDrawable ?: continue
            val active = view.tag == prefs.accent
            drawable.setStroke(if (active) width else dp(2), if (active) selected else idle)
            view.background = drawable
        }
    }

    private fun selectAccent(id: String) {
        prefs.accent = id
        // Акцент выбран вручную — пресет больше не описывает оформление.
        prefs.preset = Look.PRESET_CUSTOM
        // «Из обоев» снова включает системную палитру.
        if (id == Look.ACCENT_AUTO) prefs.dynamicColor = true
        recreate()
    }

    /** Свой акцент: три ползунка RGB и живой образец цвета. */
    private fun showAccentDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_accent, null)
        val swatch = view.findViewById<View>(R.id.accentSwatch)
        val hex = view.findViewById<TextView>(R.id.accentHex)
        val start = Look.seed(prefs.accent) ?: Look.accents.first().color
        val channels = intArrayOf(
            Palette.red(start), Palette.green(start), Palette.blue(start)
        )

        fun current(): Int = (0xFF shl 24) or
            (channels[0] shl 16) or (channels[1] shl 8) or channels[2]

        fun preview() {
            val color = current()
            swatch.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(color)
            }
            hex.text = Look.seedKey(color)
        }

        val ids = intArrayOf(R.id.sldRed, R.id.sldGreen, R.id.sldBlue)
        ids.forEachIndexed { index, id ->
            val slider = view.findViewById<Slider>(id)
            slider.valueFrom = 0f
            slider.valueTo = 255f
            slider.stepSize = 1f
            slider.value = channels[index].toFloat()
            slider.addOnChangeListener { _, value, _ ->
                channels[index] = value.toInt()
                preview()
            }
        }
        preview()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accent_dialog_title)
            .setView(view)
            .setNegativeButton(R.string.reset_cancel, null)
            .setPositiveButton(R.string.accent_apply) { _, _ ->
                prefs.accent = Look.seedKey(current())
                prefs.preset = Look.PRESET_CUSTOM
                recreate()
            }
            .show()
    }

    private fun bindContrast() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.contrastGroup)
        group.check(
            when (Contrast.parse(prefs.contrast)) {
                Contrast.MEDIUM -> R.id.contrastMedium
                Contrast.HIGH -> R.id.contrastHigh
                Contrast.STANDARD -> R.id.contrastStandard
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.contrast = when (checkedId) {
                R.id.contrastMedium -> "medium"
                R.id.contrastHigh -> "high"
                else -> "standard"
            }
            miniPreview.refresh()
        }
    }

    private fun bindShape() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.shapeGroup)
        group.check(
            when (KeyShape.parse(prefs.shape)) {
                KeyShape.SQUARE -> R.id.shapeSquare
                KeyShape.PILL -> R.id.shapePill
                KeyShape.FLOWER -> R.id.shapeFlower
                KeyShape.ROUNDED -> R.id.shapeRounded
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            prefs.shape = KeyShape.key(
                when (checkedId) {
                    R.id.shapeSquare -> KeyShape.SQUARE
                    R.id.shapePill -> KeyShape.PILL
                    R.id.shapeFlower -> KeyShape.FLOWER
                    else -> KeyShape.ROUNDED
                }
            )
            prefs.preset = Look.PRESET_CUSTOM
            refreshPresetSelection()
            miniPreview.refresh()
        }
    }

    /** Размер в dp целыми пикселями. */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    /** Пример отклика при смене характера вибрации. */
    @Suppress("DEPRECATION")
    private fun sampleHaptic() {
        if (!prefs.vibrate) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        val style = Haptics.parse(prefs.hapticStyle)
        try {
            if (style == HapticStyle.CUSTOM || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val amplitude = if (vibrator.hasAmplitudeControl()) {
                    Haptics.amplitude(prefs.vibrateStrength)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val duration = if (style == HapticStyle.CUSTOM) {
                    Haptics.customDuration(prefs.customHapticDuration)
                } else {
                    Haptics.durationMs(style)
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                val effect = when (style) {
                    HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                    HapticStyle.STRONG -> VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effect))
            }
        } catch (t: Throwable) {
            // вибрация может быть отключена системой
        }
    }

    /**
     * Сила вибрации нужна на Android 8–9 и для «Своего» отклика.
     * На Android 10+ с системными эффектами ползунок силы не действует.
     */
    private fun updateVibrationUi() {
        val custom = Haptics.parse(prefs.hapticStyle) == HapticStyle.CUSTOM
        val showStrength = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || custom

        listOf(R.id.vibrationRow, R.id.sldVibration).forEach { id ->
            findViewById<View>(id).visibility =
                if (showStrength) View.VISIBLE else View.GONE
        }
        listOf(R.id.hapticDurationRow, R.id.sldHapticDuration).forEach { id ->
            findViewById<View>(id).visibility =
                if (custom) View.VISIBLE else View.GONE
        }

        val hint = findViewById<TextView>(R.id.vibrationHint)
        hint.visibility = if (showStrength) View.VISIBLE else View.GONE
        hint.setText(
            if (custom) R.string.vibration_custom_hint
            else R.string.vibration_legacy_hint
        )
    }

    private fun bindSwitch(id: Int, value: Boolean, save: (Boolean) -> Unit) {
        val view = findViewById<MaterialSwitch>(id)
        view.isChecked = value
        view.setOnCheckedChangeListener { _, checked -> save(checked) }
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

    // ── сброс настроек ───────────────────────────────────────
    private fun confirmReset(action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_text)
            .setNegativeButton(R.string.reset_cancel, null)
            .setPositiveButton(R.string.reset_ok) { _, _ ->
                action()
                MoggedApp.applyNightMode(prefs.theme)
                toast(R.string.reset_done)
                // Пересоздаём экран: все ползунки и переключатели снова
                // показывают значения по умолчанию.
                recreate()
            }
            .show()
    }

    private fun resetLook() = with(Backup.DEFAULT) {
        prefs.width = width
        prefs.oneHand = oneHand
        prefs.height = height
        prefs.keyRadius = keyRadius
        prefs.theme = theme
        prefs.keyOutline = keyOutline
        prefs.keyFontScale = keyFontScale
        prefs.dynamicColor = dynamicColor
        prefs.accent = accent
        prefs.contrast = contrast
        prefs.preset = preset
        prefs.shape = shape
        prefs.funcRadius = funcRadius
        prefs.elevation = elevation
        prefs.background = background
        prefs.keyAlpha = keyAlpha
        prefs.animations = animations
    }

    private fun resetInput() = with(Backup.DEFAULT) {
        prefs.hints = hints
        prefs.keyPreview = keyPreview
        prefs.numberRow = numberRow
        prefs.doubleSpace = doubleSpace
        prefs.autoShift = autoShift
        prefs.spaceSwipe = spaceSwipe
        prefs.backspaceWord = backspaceWord
        prefs.langKey = langKey
        prefs.oneHandPanel = oneHandPanel
    }

    private fun resetFeel() = with(Backup.DEFAULT) {
        prefs.vibrate = vibrate
        prefs.hapticStyle = hapticStyle
        prefs.hapticStrong = hapticStrong
        prefs.sound = sound
        prefs.volume = volume
        prefs.vibrateStrength = vibrateStrength
        prefs.releaseHaptic = releaseHaptic
        prefs.customHapticDuration = customHapticDuration
    }

    // ── резервная копия ──────────────────────────────────────
    private fun bindSandbox() {
        findViewById<Button>(R.id.btnSandbox).setOnClickListener {
            startActivity(Intent(this, SandboxActivity::class.java))
        }
    }

    private fun bindBackup() {
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportLauncher.launch("mogged-settings.json")
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }

    private fun saveBackup(uri: Uri) {
        val text = Backup.toJson(Backup.fromPrefs(prefs))
        val ok = try {
            val out = contentResolver.openOutputStream(uri)
            if (out == null) {
                false
            } else {
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (t: Throwable) {
            false
        }
        toast(if (ok) R.string.backup_saved else R.string.backup_save_failed)
    }

    private fun loadBackup(uri: Uri) {
        // Читаем не больше лимита: огромный файл — точно не наша копия.
        var tooBig = false
        val text = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(Backup.MAX_BYTES + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                if (total > Backup.MAX_BYTES) {
                    tooBig = true
                    null
                } else {
                    String(buffer, 0, total, Charsets.UTF_8)
                }
            }
        } catch (t: Throwable) {
            null
        }

        if (tooBig) {
            // Слишком большой файл — тоже «не наша копия», настройки не трогаем.
            toast(R.string.backup_bad)
            return
        }
        if (text == null) {
            toast(R.string.backup_read_failed)
            return
        }

        val settings = Backup.parse(text)
        if (settings == null) {
            // Чужой или битый файл: настройки не трогаем.
            toast(R.string.backup_bad)
            return
        }

        Backup.applyTo(prefs, settings)
        MoggedApp.applyNightMode(prefs.theme)
        toast(R.string.backup_loaded)
        recreate()
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
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
        updateHomeCard = findViewById(R.id.updateHomeCard)
        updateHomeText = findViewById(R.id.updateHomeText)
        findViewById<Button>(R.id.btnHomeUpdates).setOnClickListener {
            showSection(SECTION_MORE)
        }
    }

    /** Бейдж на «Ещё»: виден, пока есть новая версия. */
    private fun setUpdateBadge(visible: Boolean) {
        if (visible) {
            bottomNav.getOrCreateBadge(R.id.nav_more).isVisible = true
            navRail.getOrCreateBadge(R.id.nav_more).isVisible = true
        } else {
            bottomNav.removeBadge(R.id.nav_more)
            navRail.removeBadge(R.id.nav_more)
        }
    }

    private fun setUpdateCard(update: UpdateManager.Update?) {
        if (update == null) {
            updateHomeCard.visibility = View.GONE
            setUpdateBadge(false)
            return
        }
        updateHomeText.text = getString(R.string.update_available, update.versionName)
        updateHomeCard.visibility = View.VISIBLE
        setUpdateBadge(true)
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

        // На «Главной» — короткая выжимка последней версии.
        val latest = Changelog.releases.firstOrNull()
        homeWhatsNew.text = if (latest == null) {
            ""
        } else {
            buildString {
                append(latest.version).append(" — «").append(latest.name).append('»')
                latest.items.take(3).forEach { append("\n• ").append(it) }
            }
        }
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
                        setUpdateCard(null)
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
        setUpdateCard(update)
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
