package com.example.airmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Air Mouse — Created by AXVin
 *
 * Coordinates the Collapsible Connection Bar, Permanent Bottom Navigation Footer
 * (Air Mouse, Keyboard, Media, Gamepad, System Power, Settings), Fullscreen Landscape
 * Keyboard & Gamepad modes, and Material You / Mihon Dynamic Theming.
 */
@SuppressLint("MissingPermission", "ClickableViewAccessibility", "SetTextI18n")
class MainActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val SENTINEL_CHAR = "\u200B"
        private const val PATREON_URL = "https://www.patreon.com/axvin"
        private const val KOFI_URL = "https://ko-fi.com/axvin"
    }

    private enum class GamepadStyle {
        XBOX_360, PS5_DUALSENSE, MOBILE_PRO
    }

    private lateinit var settings: AppSettings
    private lateinit var palette: ThemePalette
    private lateinit var btController: BluetoothHidController

    // Sensor state
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isAirMouseTriggerHeld = false
    private var isAirMouseLockedOn = false
    private var isGamepadGyroSteering = false
    private var gyroSensitivity = 22.0f
    private var residualX = 0.0f
    private var residualY = 0.0f
    private var accelBaseX = 0.0f
    private var accelBaseY = 0.0f
    private var accelCalibrated = false

    // Mouse & Multi-Finger Gesture state
    private var activeMouseButtons = 0
    private var maxPointersInGesture = 0
    private var gestureStartTime = 0L
    private var lastSingleX = 0f
    private var lastSingleY = 0f
    private var singleMovedDist = 0f
    private var lastTwoCenterX = 0f
    private var lastTwoCenterY = 0f
    private var lastPinchSpan = 0f
    private var twoFingerMovedDist = 0f
    private var threeStartX = 0f
    private var threeStartY = 0f
    private var threeGestureFired = false
    private var lastScrollY = 0f

    // Media & Volume state
    private var estimatedVolumePercent = 50
    private var isVolumeMuted = false
    private var seekWheelUseArrowKeys = true
    private var volumeDialViewRef: VelocityRotaryDialView? = null
    private var seekDialViewRef: VelocityRotaryDialView? = null
    private var muteButtonRef: Button? = null

    // Full Landscape Keyboard Sticky Modifier state
    private var stickyModifiersMask = 0
    private var isCapsLocked = false
    private val modifierButtonsMap = mutableMapOf<Int, Button>()
    private var capsButtonRef: Button? = null

    // Gamepad state
    private var currentGamepadStyle = GamepadStyle.XBOX_360
    private var gamepadButtonMask = 0
    private var gamepadHatState = 8
    private var leftStickX = 0
    private var leftStickY = 0
    private var rightStickX = 0
    private var rightStickY = 0

    // Live keyboard state
    private var isResettingLiveInput = false

    // UI & Fullscreen state
    private var isUserSelectingDevice = false
    private var isConnectionCardExpanded = true
    private var isFullscreenActive = false
    private var currentTabIndex = 0

    private lateinit var outerRootLayout: LinearLayout
    private lateinit var topConnectionWrapper: LinearLayout
    private lateinit var connectionHeaderToggle: LinearLayout
    private lateinit var connectionHeaderStatus: TextView
    private lateinit var connectionCollapseArrow: TextView
    private lateinit var connectionBodyLayout: LinearLayout
    private lateinit var deviceSpinner: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var quickSwitchRow: LinearLayout

    private lateinit var rootScrollView: LockableScrollView
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var touchpadView: TextView
    private lateinit var liveKeyboardInput: EditText
    private lateinit var airTriggerBtn: Button
    private lateinit var gamepadContentContainer: LinearLayout

    private val tabItemLayouts = mutableListOf<LinearLayout>()
    private val tabTitleViews = mutableListOf<TextView>()
    private val tabIndicatorPills = mutableListOf<View>()
    private val tabSections = mutableListOf<View>()

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                showDiagnosticErrorScreen(throwable)
            }
        }
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {
        }

        settings = AppSettings(this)
        palette = settings.theme.resolvePalette(this, settings.amoledBlack)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        btController = BluetoothHidController(
            context = this,
            iosCompatibilityMode = settings.iosCompatibilityMode,
            onStatusChanged = { statusText, isConnected, isConnecting ->
                if (::connectionHeaderStatus.isInitialized) {
                    connectionHeaderStatus.text = statusText
                    connectionHeaderStatus.setTextColor(
                        when {
                            isConnected -> palette.success
                            isConnecting -> palette.warning
                            else -> palette.primary
                        }
                    )
                    if (isConnected) {
                        hapticTick()
                        setConnectionCardExpanded(false)
                    } else if (!isConnecting) {
                        setConnectionCardExpanded(true)
                    }
                }
            },
            onDevicesUpdated = {
                if (::quickSwitchRow.isInitialized) {
                    rebuildQuickSwitchChips()
                }
            }
        )

        try {
            buildUserInterface()
            checkAndRequestPermissions()
        } catch (t: Throwable) {
            showDiagnosticErrorScreen(t)
        }
    }

    private fun showDiagnosticErrorScreen(t: Throwable) {
        try {
            val scroll = android.widget.ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0F172A"))
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(24), dp(16), dp(24))
            }
            val title = TextView(this).apply {
                text = "Air Mouse Diagnostic Report"
                setTextColor(Color.parseColor("#F87171"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val traceText = TextView(this).apply {
                text = android.util.Log.getStackTraceString(t)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextIsSelectable(true)
                setPadding(0, dp(12), 0, 0)
            }
            box.addView(title)
            box.addView(traceText)
            scroll.addView(box)
            setContentView(scroll)
        } catch (_: Throwable) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        if (hasBluetoothPermissions()) {
            refreshPairedDevicesUi()
            btController.initProfile()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        btController.shutdown()
    }

    // =========================================================================
    // MAIN SCAFFOLD (TOP COLLAPSIBLE CONNECTION + SCROLL CONTENT + BOTTOM FOOTER)
    // =========================================================================

    private fun buildUserInterface() {
        palette = settings.theme.resolvePalette(this, settings.amoledBlack)
        try {
            window.statusBarColor = palette.background
            window.navigationBarColor = palette.surface
        } catch (_: Throwable) {
        }

        outerRootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Top Collapsible Connection Panel
        topConnectionWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
        }

        connectionHeaderToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(12), palette.outline)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
            setOnClickListener {
                hapticTick()
                setConnectionCardExpanded(!isConnectionCardExpanded)
            }
        }

        connectionHeaderStatus = TextView(this).apply {
            text = "🔵 Connection: Ready"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        connectionHeaderToggle.addView(connectionHeaderStatus)

        connectionCollapseArrow = TextView(this).apply {
            text = if (isConnectionCardExpanded) "▲ Hide" else "▼ Devices"
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        connectionHeaderToggle.addView(connectionCollapseArrow)
        topConnectionWrapper.addView(connectionHeaderToggle)

        connectionBodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(12), palette.outline)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            visibility = if (isConnectionCardExpanded) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        deviceAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf("Select Paired Device...")
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (super.getView(position, convertView, parent) as TextView)
                tv.setTextColor(palette.onSurface)
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.typeface = Typeface.DEFAULT_BOLD
                tv.setPadding(dp(10), dp(6), dp(10), dp(6))
                tv.isSingleLine = true
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (super.getDropDownView(position, convertView, parent) as TextView)
                tv.setTextColor(palette.onSurface)
                tv.setBackgroundColor(palette.surfaceContainer)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                tv.setPadding(dp(14), dp(11), dp(14), dp(11))
                return tv
            }
        }

        deviceSpinner = Spinner(this).apply {
            adapter = deviceAdapter
            background = AppTheme.materialCard(palette.surfaceContainer, dp(10), palette.outline)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isUserSelectingDevice && position in btController.pairedDevices.indices) {
                        btController.switchToDevice(btController.pairedDevices[position])
                    }
                    isUserSelectingDevice = true
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        spinnerRow.addView(deviceSpinner)

        val connectBtn = createStyledButton("Connect", palette.primary, palette.onPrimary) {
            val idx = deviceSpinner.selectedItemPosition
            if (idx in btController.pairedDevices.indices) {
                btController.switchToDevice(btController.pairedDevices[idx])
            } else {
                Toast.makeText(this, "Pair your TV first using 'Pair New'", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginStart = dp(6)
            }
        }
        spinnerRow.addView(connectBtn)
        connectionBodyLayout.addView(spinnerRow)

        val btActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        btActionsRow.addView(createStyledButton("Reset Conn", palette.warning, Color.BLACK) {
            hapticTick()
            btController.resetConnection()
            refreshPairedDevicesUi()
            Toast.makeText(this, "Bluetooth HID connection reset", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = weightParams(dp(34)) })

        btActionsRow.addView(createStyledButton("Unpair", palette.danger, Color.WHITE) {
            hapticTick()
            confirmAndUnpairDevice()
        }.apply { layoutParams = weightParams(dp(34)) })

        btActionsRow.addView(createStyledButton("Pair New", palette.surfaceContainer, palette.onSurface) {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                })
            } catch (_: Throwable) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: Throwable) {
                }
            }
        }.apply { layoutParams = weightParams(dp(34)) })

        btActionsRow.addView(createStyledButton("Scan TV", palette.primary, palette.onPrimary) {
            hapticTick()
            showFireTvScannerDialog()
        }.apply { layoutParams = weightParams(dp(34)) })

        btActionsRow.addView(createStyledButton("Refresh", palette.surfaceContainer, palette.onSurface) {
            refreshPairedDevicesUi()
            Toast.makeText(this, "Device list refreshed", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = weightParams(dp(34)) })
        connectionBodyLayout.addView(btActionsRow)

        val quickSwitchScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        quickSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quickSwitchScroll.addView(quickSwitchRow)
        connectionBodyLayout.addView(quickSwitchScroll)

        topConnectionWrapper.addView(connectionBodyLayout)
        outerRootLayout.addView(topConnectionWrapper)

        // 2. Scrollable Middle Content Area (Holds all 6 Pages)
        rootScrollView = LockableScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val pagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(12))
        }

        tabSections.clear()
        tabSections.add(buildAirMouseTab())
        tabSections.add(buildFullLandscapeKeyboardTab())
        tabSections.add(buildMediaTab())
        tabSections.add(buildLandscapeGamepadTab())
        tabSections.add(buildSystemPowerTab())
        tabSections.add(buildSettingsTab())

        tabSections.forEach { pagesContainer.addView(it) }
        rootScrollView.addView(pagesContainer)
        outerRootLayout.addView(rootScrollView)

        // 3. Permanent Docked Material 3 Bottom Navigation Footer Bar
        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        }

        val navSpecs = listOf(
            "🖱️" to "Air Mouse",
            "⌨️" to "Keyboard",
            "📺" to "Media",
            "🎮" to "Gamepad",
            "⚡" to "System Power",
            "⚙️" to "Settings"
        )

        tabItemLayouts.clear()
        tabTitleViews.clear()
        tabIndicatorPills.clear()

        navSpecs.forEachIndexed { index, (icon, label) ->
            val navCell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    hapticTick()
                    selectTab(index, fromUserTap = true)
                }
            }

            val pillView = TextView(this).apply {
                text = icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(24))
            }
            navCell.addView(pillView)

            val labelTv = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                setPadding(0, dp(2), 0, 0)
            }
            navCell.addView(labelTv)

            tabItemLayouts.add(navCell)
            tabIndicatorPills.add(pillView)
            tabTitleViews.add(labelTv)
            bottomNavBar.addView(navCell)
        }

        outerRootLayout.addView(bottomNavBar)
        setContentView(outerRootLayout)

        if (hasBluetoothPermissions()) {
            refreshPairedDevicesUi()
        }
        selectTab(currentTabIndex, fromUserTap = false)
    }

    private fun applyThemeLive() {
        val savedTab = currentTabIndex
        val savedFullscreen = isFullscreenActive
        val savedExpanded = isConnectionCardExpanded
        isConnectionCardExpanded = savedExpanded
        buildUserInterface()
        selectTab(savedTab, fromUserTap = false)
        if (savedFullscreen) {
            applyFullscreenState(true)
        }
    }

    private fun setConnectionCardExpanded(expanded: Boolean) {
        isConnectionCardExpanded = expanded
        if (::connectionBodyLayout.isInitialized) {
            connectionBodyLayout.visibility = if (expanded) View.VISIBLE else View.GONE
        }
        if (::connectionCollapseArrow.isInitialized) {
            connectionCollapseArrow.text = if (expanded) "▲ Hide" else "▼ Devices"
        }
    }

    private fun selectTab(selectedIndex: Int, fromUserTap: Boolean) {
        currentTabIndex = selectedIndex

        if (selectedIndex != 1 && selectedIndex != 3 && isFullscreenActive) {
            applyFullscreenState(false)
        }

        if (fromUserTap) {
            val targetOrientation = if (selectedIndex == 1 || selectedIndex == 3) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (requestedOrientation != targetOrientation) {
                try {
                    requestedOrientation = targetOrientation
                } catch (_: Throwable) {
                }
            }
        }

        tabItemLayouts.forEachIndexed { idx, _ ->
            val isSelected = idx == selectedIndex
            if (isSelected) {
                tabIndicatorPills[idx].background = AppTheme.materialCard(palette.primary, dp(12))
                tabTitleViews[idx].setTextColor(palette.primary)
            } else {
                tabIndicatorPills[idx].background = AppTheme.materialCard(Color.TRANSPARENT, dp(12))
                tabTitleViews[idx].setTextColor(palette.onSurfaceVariant)
            }
        }

        tabSections.forEachIndexed { idx, section ->
            section.visibility = if (idx == selectedIndex) View.VISIBLE else View.GONE
        }
    }

    // =========================================================================
    // FULLSCREEN MODE FOR LANDSCAPE KEYBOARD & GAMEPAD
    // =========================================================================

    private fun applyFullscreenState(fullscreen: Boolean) {
        isFullscreenActive = fullscreen
        if (::topConnectionWrapper.isInitialized) {
            topConnectionWrapper.visibility = if (fullscreen) View.GONE else View.VISIBLE
        }
        if (::bottomNavBar.isInitialized) {
            bottomNavBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        }

        window?.decorView?.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    if (fullscreen) {
                        controller?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller?.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = if (fullscreen) {
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    } else {
                        View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    // =========================================================================
    // UNPAIR CONFIRMATION DIALOG
    // =========================================================================

    private fun confirmAndUnpairDevice() {
        val idx = deviceSpinner.selectedItemPosition
        val target = btController.connectedDevice ?: btController.pairedDevices.getOrNull(idx)
        if (target == null) {
            Toast.makeText(this, "No paired device selected to unpair", Toast.LENGTH_SHORT).show()
            return
        }

        val devName = btController.safeDeviceName(target)
        AlertDialog.Builder(this)
            .setTitle("Permanently Unpair Device?")
            .setMessage(
                "This will permanently unpair \"$devName\" from your phone's Bluetooth paired devices. " +
                    "You will need to pair it again to reconnect."
            )
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Unpair Permanently") { dialog, _ ->
                dialog.dismiss()
                val removed = btController.unpairDevice(target)
                if (removed) {
                    Toast.makeText(this, "Permanently unpaired $devName", Toast.LENGTH_SHORT).show()
                    window.decorView.postDelayed({ refreshPairedDevicesUi() }, 600)
                } else {
                    Toast.makeText(this, "Opening Bluetooth settings to finish unpairing $devName", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (_: Throwable) {
                    }
                }
            }
            .show()
    }

    /**
     * Scans for nearby Fire TV Stick / Smart TVs directly from the phone and initiates
     * pairing from the phone side (which bypasses Fire OS's Class-of-Device filter).
     */
    private fun showFireTvScannerDialog() {
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val instructions = TextView(this).apply {
            text = "1. On your Fire Stick, open:\n" +
                "   Settings > Controllers & Bluetooth Devices > Other Bluetooth Devices > Add Bluetooth Devices\n\n" +
                "2. Wait a few seconds below and tap your Fire TV when it appears (or tap 'System Bluetooth Settings'):"
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, 0, 0, dp(10))
        }
        listLayout.addView(instructions)

        val discoveredContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scanningLabel = TextView(this).apply {
            text = "🔍 Scanning for nearby devices..."
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(6))
        }
        discoveredContainer.addView(scanningLabel)
        listLayout.addView(discoveredContainer)

        var dialogRef: AlertDialog? = null

        btController.onDiscoveredDevicesUpdated = { devices ->
            discoveredContainer.removeAllViews()
            if (devices.isEmpty()) {
                discoveredContainer.addView(scanningLabel)
            } else {
                for (dev in devices) {
                    val name = btController.safeDeviceName(dev)
                    val btn = createStyledButton("Pair: $name (${dev.address})", palette.surfaceContainer, palette.onSurface) {
                        hapticTick()
                        dialogRef?.dismiss()
                        btController.pairAndConnectDevice(dev)
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(42)
                        ).apply { topMargin = dp(6) }
                    }
                    discoveredContainer.addView(btn)
                }
            }
        }

        btController.startTvDiscovery()

        dialogRef = AlertDialog.Builder(this)
            .setTitle("Pair with Fire Stick / TV")
            .setView(listLayout)
            .setNeutralButton("Make Phone Discoverable") { _, _ ->
                btController.stopTvDiscovery()
                try {
                    startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    })
                } catch (_: Throwable) {
                }
            }
            .setPositiveButton("System Bluetooth Settings") { _, _ ->
                btController.stopTvDiscovery()
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: Throwable) {
                }
            }
            .setNegativeButton("Close") { dialog, _ ->
                btController.stopTvDiscovery()
                dialog.dismiss()
            }
            .setOnDismissListener {
                btController.stopTvDiscovery()
            }
            .show()
    }

    // =========================================================================
    // TAB 1: AIR MOUSE (ENLARGED MULTI-GESTURE TRACKPAD + GYRO + INLINE KEYBOARD)
    // =========================================================================

    private fun buildAirMouseTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(315)
            )
        }

        touchpadView = TextView(this).apply {
            text = "MULTI-GESTURE TRACKPAD (SCROLL LOCKED)\n" +
                "• 1 Finger: Move & Tap (Left Click)\n" +
                "• 2 Fingers: Tap (Right Click), V/H Scroll & Pinch Zoom\n" +
                "• 3 Fingers: Swipe Left/Right (Back/Fwd), Up/Down (Apps/Home)"
            gravity = Gravity.CENTER
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { v, event -> handleMultiGestureTouchpad(v, event) }
        }
        padRow.addView(touchpadView)

        val scrollStrip = TextView(this).apply {
            text = "S\nC\nR\nO\nL\nL"
            gravity = Gravity.CENTER
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(6)
            }
            setOnTouchListener { v, event -> handleScrollStripEvent(v, event) }
        }
        padRow.addView(scrollStrip)
        tabRoot.addView(padRow)

        val mouseBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { topMargin = dp(8) }
        }
        mouseBtnsRow.addView(createHoldMouseButton("LEFT CLICK", 1), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f))
        mouseBtnsRow.addView(createHoldMouseButton("MIDDLE", 4), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.8f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        })
        mouseBtnsRow.addView(createHoldMouseButton("RIGHT CLICK", 2), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f))
        tabRoot.addView(mouseBtnsRow)

        val gyroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(14), palette.outline)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        airTriggerBtn = Button(this).apply {
            text = "HOLD TO MOVE AIR POINTER (GYRO)"
            setTextColor(palette.onPrimary)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.primary, dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        isAirMouseTriggerHeld = true
                        accelCalibrated = false
                        hapticTick()
                        background = AppTheme.materialCard(palette.secondary, dp(12))
                        text = "AIR POINTER ACTIVE — POINT PHONE AT TV"
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        isAirMouseTriggerHeld = false
                        background = AppTheme.materialCard(palette.primary, dp(12))
                        text = "HOLD TO MOVE AIR POINTER (GYRO)"
                        true
                    }
                    else -> false
                }
            }
        }
        gyroCard.addView(airTriggerBtn)

        val gyroOptionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        val gyroLockBtn = Button(this).apply {
            text = if (isAirMouseLockedOn) "✓ Gyro Lock: ON" else "Gyro Lock: OFF"
            setTextColor(if (isAirMouseLockedOn) palette.onPrimary else palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(
                if (isAirMouseLockedOn) palette.primary else palette.surfaceContainer,
                dp(10),
                palette.outline
            )
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
            setOnClickListener {
                hapticTick()
                isAirMouseLockedOn = !isAirMouseLockedOn
                accelCalibrated = false
                text = if (isAirMouseLockedOn) "✓ Gyro Lock: ON" else "Gyro Lock: OFF"
                setTextColor(if (isAirMouseLockedOn) palette.onPrimary else palette.onSurface)
                background = AppTheme.materialCard(
                    if (isAirMouseLockedOn) palette.primary else palette.surfaceContainer,
                    dp(10),
                    palette.outline
                )
            }
        }
        gyroOptionsRow.addView(gyroLockBtn)

        val sensLabel = TextView(this).apply {
            text = "Speed:"
            setTextColor(palette.onSurfaceVariant)
            setPadding(dp(12), 0, dp(4), 0)
        }
        gyroOptionsRow.addView(sensLabel)

        val sensSeek = SeekBar(this).apply {
            max = 45
            progress = 20
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    gyroSensitivity = (progress + 5).toFloat()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        gyroOptionsRow.addView(sensSeek)
        gyroCard.addView(gyroOptionsRow)
        tabRoot.addView(gyroCard)

        // Inline Quick Keyboard Bar
        val kbCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(14), palette.outline)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        liveKeyboardInput = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val base = super.onCreateInputConnection(outAttrs) ?: return null
                return object : InputConnectionWrapper(base, true) {
                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0 && afterLength == 0) {
                            btController.sendKeyboardKey(0, 0x2A)
                            return true
                        }
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_DEL -> {
                                    btController.sendKeyboardKey(0, 0x2A)
                                    return true
                                }
                                KeyEvent.KEYCODE_ENTER -> {
                                    btController.sendKeyboardKey(0, 0x28)
                                    return true
                                }
                            }
                        }
                        return super.sendKeyEvent(event)
                    }
                }
            }
        }.apply {
            setText(SENTINEL_CHAR)
            setSelection(text.length)
            hint = "Tap here to type on TV..."
            setHintTextColor(palette.onSurfaceVariant)
            setTextColor(palette.onSurface)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (isResettingLiveInput || s == null) return
                    val current = s.toString()

                    if (current.isEmpty()) {
                        btController.sendKeyboardKey(0, 0x2A)
                        resetLiveInputBuffer()
                        return
                    }

                    val typedPart = current.replace(SENTINEL_CHAR, "")
                    if (typedPart.isNotEmpty()) {
                        for (ch in typedPart) {
                            sendCharImmediate(ch)
                        }
                        resetLiveInputBuffer()
                    }
                }
            })

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_NEXT
                ) {
                    btController.sendKeyboardKey(0, 0x28)
                    true
                } else false
            }
        }
        kbCard.addView(liveKeyboardInput)

        val openKbBtn = createStyledButton("Keyboard", palette.primary, palette.onPrimary) {
            liveKeyboardInput.requestFocus()
            liveKeyboardInput.setSelection(liveKeyboardInput.text.length)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(liveKeyboardInput, InputMethodManager.SHOW_FORCED)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                marginStart = dp(8)
            }
        }
        kbCard.addView(openKbBtn)
        tabRoot.addView(kbCard)

        return tabRoot
    }

    // =========================================================================
    // TAB 2: FULL LANDSCAPE PC KEYBOARD (WITH STICKY MODIFIERS & FULLSCREEN)
    // =========================================================================

    private data class KeySpec(
        val label: String,
        val hidCode: Int = 0,
        val modifierBit: Int = 0,
        val isCapsLock: Boolean = false,
        val weight: Float = 1f,
        val isAccent: Boolean = false,
        val isDanger: Boolean = false
    )

    private fun buildFullLandscapeKeyboardTab(): View {
        val kbRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(14), palette.outline)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val shortcutStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val hintText = TextView(this).apply {
            text = "Tap CTRL, ALT, SHIFT, or WIN to latch shortcuts"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f)
        }
        shortcutStrip.addView(hintText)

        val quickCombos = listOf(
            "Copy" to (HidDescriptor.MOD_LEFT_CTRL to 0x06),
            "Paste" to (HidDescriptor.MOD_LEFT_CTRL to 0x19),
            "Undo" to (HidDescriptor.MOD_LEFT_CTRL to 0x1D),
            "All" to (HidDescriptor.MOD_LEFT_CTRL to 0x04),
            "Alt+Tab" to (HidDescriptor.MOD_LEFT_ALT to 0x2B)
        )
        for ((lbl, combo) in quickCombos) {
            val btn = createStyledButton(lbl, palette.surfaceContainer, palette.onSurface) {
                hapticTick()
                btController.sendKeyboardKey(combo.first, combo.second)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 0.6f).apply { marginStart = dp(3) }
            }
            shortcutStrip.addView(btn)
        }

        val fsBtn = Button(this).apply {
            text = if (isFullscreenActive) "✕ Exit Fullscreen" else "⛶ Fullscreen"
            setTextColor(palette.onPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.primary, dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 0.95f).apply { marginStart = dp(4) }
            setOnClickListener {
                hapticTick()
                applyFullscreenState(!isFullscreenActive)
                text = if (isFullscreenActive) "✕ Exit Fullscreen" else "⛶ Fullscreen"
            }
        }
        shortcutStrip.addView(fsBtn)
        kbRoot.addView(shortcutStrip)

        modifierButtonsMap.clear()

        val rows = listOf(
            listOf(
                KeySpec("ESC", 0x29, isDanger = true),
                KeySpec("F1", 0x3A), KeySpec("F2", 0x3B), KeySpec("F3", 0x3C), KeySpec("F4", 0x3D),
                KeySpec("F5", 0x3E), KeySpec("F6", 0x3F), KeySpec("F7", 0x40), KeySpec("F8", 0x41),
                KeySpec("F9", 0x42), KeySpec("F10", 0x43), KeySpec("F11", 0x44), KeySpec("F12", 0x45),
                KeySpec("DEL", 0x4C)
            ),
            listOf(
                KeySpec("` ~", 0x35),
                KeySpec("1 !", 0x1E), KeySpec("2 @", 0x1F), KeySpec("3 #", 0x20), KeySpec("4 $", 0x21),
                KeySpec("5 %", 0x22), KeySpec("6 ^", 0x23), KeySpec("7 &", 0x24), KeySpec("8 *", 0x25),
                KeySpec("9 (", 0x26), KeySpec("0 )", 0x27), KeySpec("- _", 0x2D), KeySpec("= +", 0x2E),
                KeySpec("⌫ BKSP", 0x2A, weight = 1.6f)
            ),
            listOf(
                KeySpec("TAB ⇥", 0x2B, weight = 1.4f),
                KeySpec("Q", 0x14), KeySpec("W", 0x1A), KeySpec("E", 0x08), KeySpec("R", 0x15),
                KeySpec("T", 0x17), KeySpec("Y", 0x1C), KeySpec("U", 0x18), KeySpec("I", 0x0C),
                KeySpec("O", 0x12), KeySpec("P", 0x13), KeySpec("[ {", 0x2F), KeySpec("] }", 0x30),
                KeySpec("\\ |", 0x31, weight = 1.2f)
            ),
            listOf(
                KeySpec("CAPS", 0x39, isCapsLock = true, weight = 1.6f),
                KeySpec("A", 0x04), KeySpec("S", 0x16), KeySpec("D", 0x07), KeySpec("F", 0x09),
                KeySpec("G", 0x0A), KeySpec("H", 0x0B), KeySpec("J", 0x0D), KeySpec("K", 0x0E),
                KeySpec("L", 0x0F), KeySpec("; :", 0x33), KeySpec("' \"", 0x34),
                KeySpec("ENTER ↵", 0x28, weight = 1.8f, isAccent = true)
            ),
            listOf(
                KeySpec("⇧ SHIFT", modifierBit = HidDescriptor.MOD_LEFT_SHIFT, weight = 1.9f),
                KeySpec("Z", 0x1D), KeySpec("X", 0x1B), KeySpec("C", 0x06), KeySpec("V", 0x19),
                KeySpec("B", 0x05), KeySpec("N", 0x11), KeySpec("M", 0x10),
                KeySpec(", <", 0x36), KeySpec(". >", 0x37), KeySpec("/ ?", 0x38),
                KeySpec("▲", 0x52, isAccent = true),
                KeySpec("⇧ SHIFT", modifierBit = HidDescriptor.MOD_LEFT_SHIFT, weight = 1.5f)
            ),
            listOf(
                KeySpec("CTRL", modifierBit = HidDescriptor.MOD_LEFT_CTRL, weight = 1.3f),
                KeySpec("WIN", modifierBit = HidDescriptor.MOD_LEFT_GUI, weight = 1.2f),
                KeySpec("ALT", modifierBit = HidDescriptor.MOD_LEFT_ALT, weight = 1.3f),
                KeySpec("SPACE", 0x2C, weight = 4.5f),
                KeySpec("ALT", modifierBit = HidDescriptor.MOD_LEFT_ALT, weight = 1.2f),
                KeySpec("◀", 0x50, isAccent = true),
                KeySpec("▼", 0x51, isAccent = true),
                KeySpec("▶", 0x4F, isAccent = true)
            )
        )

        for (rowSpecs in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(38)
                ).apply { topMargin = dp(4) }
            }

            for (spec in rowSpecs) {
                val fill = when {
                    spec.isDanger -> palette.danger
                    spec.isAccent -> palette.primary
                    else -> palette.surfaceContainer
                }
                val textCol = when {
                    spec.isDanger -> Color.WHITE
                    spec.isAccent -> palette.onPrimary
                    else -> palette.onSurface
                }

                val btn = Button(this).apply {
                    text = spec.label
                    setTextColor(textCol)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    background = AppTheme.materialCard(fill, dp(6), palette.outline)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spec.weight).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    setOnClickListener {
                        hapticTick()
                        handleFullKeyboardKeyTap(spec)
                    }
                }
                if (spec.modifierBit != 0 && !modifierButtonsMap.containsKey(spec.modifierBit)) {
                    modifierButtonsMap[spec.modifierBit] = btn
                }
                if (spec.isCapsLock) {
                    capsButtonRef = btn
                }
                rowLayout.addView(btn)
            }
            kbRoot.addView(rowLayout)
        }

        return kbRoot
    }

    private fun handleFullKeyboardKeyTap(spec: KeySpec) {
        if (spec.modifierBit != 0) {
            stickyModifiersMask = stickyModifiersMask xor spec.modifierBit
            updateStickyModifierButtonStyles()
            return
        }

        if (spec.isCapsLock) {
            isCapsLocked = !isCapsLocked
            btController.sendKeyboardKey(0, 0x39)
            capsButtonRef?.background = if (isCapsLocked) {
                AppTheme.materialCard(palette.success, dp(6), palette.outline)
            } else {
                AppTheme.materialCard(palette.surfaceContainer, dp(6), palette.outline)
            }
            return
        }

        var effectiveMod = stickyModifiersMask
        if (isCapsLocked && spec.hidCode in 0x04..0x1D) {
            effectiveMod = effectiveMod xor HidDescriptor.MOD_LEFT_SHIFT
        }

        btController.sendKeyboardKey(effectiveMod, spec.hidCode)

        if (stickyModifiersMask != 0) {
            stickyModifiersMask = 0
            updateStickyModifierButtonStyles()
        }
    }

    private fun updateStickyModifierButtonStyles() {
        for ((bit, btn) in modifierButtonsMap) {
            val active = (stickyModifiersMask and bit) != 0
            btn.background = if (active) {
                AppTheme.materialCard(palette.primary, dp(6), palette.secondary)
            } else {
                AppTheme.materialCard(palette.surfaceContainer, dp(6), palette.outline)
            }
            btn.setTextColor(if (active) palette.onPrimary else palette.onSurface)
        }
    }

    // =========================================================================
    // TAB 3: MEDIA (VELOCITY-ACCELERATED JOG WHEEL & VOLUME % ROTARY DIAL)
    // =========================================================================

    private fun buildMediaTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dialsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val dialsTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        muteButtonRef = Button(this).apply {
            text = "🔊 Mute"
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.surfaceContainer, dp(10), palette.outline)
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                hapticTick()
                toggleMuteState()
            }
        }
        dialsTopBar.addView(muteButtonRef)

        val seekModeBtn = Button(this).apply {
            text = "Seek Mode: Arrow ◀/▶"
            setTextColor(palette.onPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.primary, dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1.2f).apply { marginStart = dp(4) }
            setOnClickListener {
                hapticTick()
                seekWheelUseArrowKeys = !seekWheelUseArrowKeys
                text = if (seekWheelUseArrowKeys) "Seek Mode: Arrow ◀/▶" else "Seek Mode: Media ⏪/⏩"
            }
        }
        dialsTopBar.addView(seekModeBtn)
        dialsCard.addView(dialsTopBar)

        val dialsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(185)
            )
        }

        val volumeDial = VelocityRotaryDialView(
            context = this,
            title = "VOLUME DIAL",
            accentColorInt = palette.primary,
            surfaceColorInt = palette.background,
            centerColorInt = palette.surfaceContainer,
            textColorInt = palette.onSurface,
            scrollLockCallback = { rootScrollView.isScrollLocked = it },
            hapticCallback = { hapticTick() },
            centerTextProvider = { if (isVolumeMuted) "MUTED" else "$estimatedVolumePercent%" },
            centerSubTextProvider = { "VOL" },
            onStep = { clockwise, mult ->
                val step = (2 * mult).coerceAtMost(10)
                adjustVolumeStep(if (clockwise) step else -step, mult)
            },
            onCenterTap = {
                estimatedVolumePercent = 50
                isVolumeMuted = false
                updateVolumeUi()
                Toast.makeText(this, "Volume readout synced to 50%", Toast.LENGTH_SHORT).show()
            }
        )
        volumeDialViewRef = volumeDial

        var seekDeltaCounter = 0
        val seekJogWheel = VelocityRotaryDialView(
            context = this,
            title = "SEEK JOG WHEEL",
            accentColorInt = palette.secondary,
            surfaceColorInt = palette.background,
            centerColorInt = palette.surfaceContainer,
            textColorInt = palette.onSurface,
            scrollLockCallback = { rootScrollView.isScrollLocked = it },
            hapticCallback = { hapticTick() },
            centerTextProvider = {
                when {
                    seekDeltaCounter > 0 -> "+${seekDeltaCounter}x"
                    seekDeltaCounter < 0 -> "${seekDeltaCounter}x"
                    else -> "⏯ PLAY"
                }
            },
            centerSubTextProvider = { "SLOW=1x • FAST=6x" },
            onStep = { clockwise, mult ->
                seekDeltaCounter += if (clockwise) mult else -mult
                performVelocitySeek(clockwise, mult)
            },
            onCenterTap = {
                seekDeltaCounter = 0
                btController.sendConsumerKey(0x00CD)
            },
            onReleaseWheel = {
                seekDeltaCounter = 0
            }
        )
        seekDialViewRef = seekJogWheel

        dialsRow.addView(volumeDial, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(6) })
        dialsRow.addView(seekJogWheel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(6) })
        dialsCard.addView(dialsRow)
        tabRoot.addView(dialsCard)

        val mediaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createStyledButton("VOL -", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            adjustVolumeStep(-2, 1)
        }.apply { layoutParams = weightParams() })
        row1.addView(createStyledButton("🔇 MUTE", palette.danger, Color.WHITE) {
            hapticTick()
            toggleMuteState()
        }.apply { layoutParams = weightParams() })
        row1.addView(createStyledButton("VOL +", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            adjustVolumeStep(+2, 1)
        }.apply { layoutParams = weightParams() })
        mediaCard.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        row2.addView(createConsumerButton("⏮ PREV", 0x00B6), weightParams())
        row2.addView(createConsumerButton("⏯ PLAY/PAUSE", 0x00CD, true), weightParams())
        row2.addView(createConsumerButton("NEXT ⏭", 0x00B5), weightParams())
        mediaCard.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        row3.addView(createStyledButton("⏪ SEEK -", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            performVelocitySeek(false, 1)
        }.apply { layoutParams = weightParams() })
        row3.addView(createConsumerButton("⏹ STOP", 0x00B7), weightParams())
        row3.addView(createStyledButton("SEEK + ⏩", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            performVelocitySeek(true, 1)
        }.apply { layoutParams = weightParams() })
        mediaCard.addView(row3)

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        row4.addView(createConsumerButton("CH -", 0x009D), weightParams())
        row4.addView(createConsumerButton("TV HOME", 0x0223, true), weightParams())
        row4.addView(createConsumerButton("CH +", 0x009C), weightParams())
        mediaCard.addView(row4)

        val row5 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        row5.addView(createConsumerButton("TV BACK", 0x0224), weightParams())
        row5.addView(createConsumerButton("VOICE / SEARCH", 0x0221), weightParams())
        row5.addView(createConsumerButton("TV BROWSER", 0x0196), weightParams())
        mediaCard.addView(row5)

        tabRoot.addView(mediaCard)
        return tabRoot
    }

    private fun adjustVolumeStep(deltaPercent: Int, burstCount: Int) {
        isVolumeMuted = false
        estimatedVolumePercent = (estimatedVolumePercent + deltaPercent).coerceIn(0, 100)
        val usage = if (deltaPercent > 0) 0x00E9 else 0x00EA
        val pulses = burstCount.coerceIn(1, 3)
        repeat(pulses) {
            btController.sendConsumerKey(usage)
        }
        updateVolumeUi()
    }

    private fun toggleMuteState() {
        isVolumeMuted = !isVolumeMuted
        btController.sendConsumerKey(0x00E2)
        updateVolumeUi()
    }

    private fun updateVolumeUi() {
        volumeDialViewRef?.invalidate()
        muteButtonRef?.apply {
            if (isVolumeMuted) {
                text = "🔇 Muted"
                background = AppTheme.materialCard(palette.danger, dp(10))
                setTextColor(Color.WHITE)
            } else {
                text = "🔊 Mute"
                background = AppTheme.materialCard(palette.surfaceContainer, dp(10), palette.outline)
                setTextColor(palette.onSurface)
            }
        }
    }

    private fun performVelocitySeek(forward: Boolean, multiplier: Int) {
        val pulses = multiplier.coerceIn(1, 6)
        if (seekWheelUseArrowKeys) {
            val arrowKey = if (forward) 0x4F else 0x50
            repeat(pulses) {
                btController.sendKeyboardKey(0, arrowKey)
            }
        } else {
            val mediaUsage = if (forward) 0x00B3 else 0x00B4
            repeat(pulses) {
                btController.sendConsumerKey(mediaUsage)
            }
        }
    }

    // =========================================================================
    // TAB 4: LANDSCAPE GAMEPAD (WITH FULLSCREEN MODE & XBOX / PS5 / MOBILE STYLES)
    // =========================================================================

    private fun buildLandscapeGamepadTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        gamepadContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tabRoot.addView(gamepadContentContainer)

        rebuildGamepadLayout()
        return tabRoot
    }

    private fun rebuildGamepadLayout() {
        gamepadContentContainer.removeAllViews()

        val themeBorder = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> Color.parseColor("#107C10")
            GamepadStyle.PS5_DUALSENSE -> Color.parseColor("#2563EB")
            GamepadStyle.MOBILE_PRO -> palette.primary
        }

        val padCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), themeBorder, dp(2))
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        val topTriggersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val (l2Name, l1Name, r1Name, r2Name) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("LT", "LB", "RB", "RT")
            GamepadStyle.PS5_DUALSENSE -> listOf("L2", "L1", "R1", "R2")
            GamepadStyle.MOBILE_PRO -> listOf("L2", "L1", "R1", "R2")
        }

        topTriggersRow.addView(createHoldGamepadButton(l2Name, 1 shl 6, palette.surfaceContainer), LinearLayout.LayoutParams(0, dp(38), 0.9f).apply { marginEnd = dp(3) })
        topTriggersRow.addView(createHoldGamepadButton(l1Name, 1 shl 4, palette.surfaceContainer), LinearLayout.LayoutParams(0, dp(38), 0.9f).apply { marginEnd = dp(5) })

        topTriggersRow.addView(createStyledButton("Xbox", Color.parseColor("#107C10"), Color.WHITE) {
            currentGamepadStyle = GamepadStyle.XBOX_360
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.75f).apply { marginEnd = dp(3) } })

        topTriggersRow.addView(createStyledButton("PS5", Color.parseColor("#00439C"), Color.WHITE) {
            currentGamepadStyle = GamepadStyle.PS5_DUALSENSE
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.75f).apply { marginEnd = dp(3) } })

        topTriggersRow.addView(createStyledButton("Mobile", palette.primary, palette.onPrimary) {
            currentGamepadStyle = GamepadStyle.MOBILE_PRO
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.8f).apply { marginEnd = dp(3) } })

        val fsBtn = createStyledButton(
            if (isFullscreenActive) "✕ Exit FS" else "⛶ Fullscreen",
            palette.secondary,
            Color.BLACK
        ) {
            hapticTick()
            applyFullscreenState(!isFullscreenActive)
            rebuildGamepadLayout()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.95f).apply { marginEnd = dp(5) }
        }
        topTriggersRow.addView(fsBtn)

        topTriggersRow.addView(createHoldGamepadButton(r1Name, 1 shl 5, palette.surfaceContainer), LinearLayout.LayoutParams(0, dp(38), 0.9f).apply { marginEnd = dp(3) })
        topTriggersRow.addView(createHoldGamepadButton(r2Name, 1 shl 7, palette.surfaceContainer), LinearLayout.LayoutParams(0, dp(38), 0.9f))
        padCard.addView(topTriggersRow)

        val landscapeDeck = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(175)
            ).apply { topMargin = dp(8) }
        }

        val leftStickView = VirtualJoystickView(
            context = this,
            label = "L-STICK",
            accentColorInt = themeBorder,
            baseColorInt = palette.background,
            scrollLockCallback = { rootScrollView.isScrollLocked = it },
            onStickMoved = { x, y ->
                leftStickX = x
                leftStickY = y
                btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
            }
        )
        val dpadView = buildDpadCluster()
        val rightStickView = VirtualJoystickView(
            context = this,
            label = "R-STICK",
            accentColorInt = themeBorder,
            baseColorInt = palette.background,
            scrollLockCallback = { rootScrollView.isScrollLocked = it },
            onStickMoved = { x, y ->
                rightStickX = x
                rightStickY = y
                btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
            }
        )
        val faceButtonsView = buildFaceButtonsCluster()

        val centerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f)
        }

        val (selectLabel, guideLabel, startLabel) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> Triple("BACK", "XBOX", "START")
            GamepadStyle.PS5_DUALSENSE -> Triple("CREATE", "PS", "OPTIONS")
            GamepadStyle.MOBILE_PRO -> Triple("SEL", "HOME", "START")
        }

        val sysBtnsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sysBtnsRow.addView(createHoldGamepadButton(selectLabel, 1 shl 8, palette.surfaceContainer), weightParams(dp(34)))
        sysBtnsRow.addView(createHoldGamepadButton(guideLabel, 1 shl 12, themeBorder), weightParams(dp(34)))
        sysBtnsRow.addView(createHoldGamepadButton(startLabel, 1 shl 9, palette.surfaceContainer), weightParams(dp(34)))
        centerCol.addView(sysBtnsRow)

        val l3r3Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        l3r3Row.addView(createHoldGamepadButton("L3", 1 shl 10, palette.surfaceContainer), weightParams(dp(34)))
        l3r3Row.addView(createHoldGamepadButton("R3", 1 shl 11, palette.surfaceContainer), weightParams(dp(34)))
        centerCol.addView(l3r3Row)

        val gyroBtn = createStyledButton(
            if (isGamepadGyroSteering) "Gyro Steer: ON" else "Gyro Steer: OFF",
            if (isGamepadGyroSteering) palette.success else palette.surfaceContainer,
            if (isGamepadGyroSteering) Color.BLACK else palette.onSurface
        ) {
            isGamepadGyroSteering = !isGamepadGyroSteering
            if (!isGamepadGyroSteering) {
                leftStickX = 0
                leftStickY = 0
                btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
            }
            rebuildGamepadLayout()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(6) }
        }
        centerCol.addView(gyroBtn)

        if (currentGamepadStyle == GamepadStyle.XBOX_360) {
            landscapeDeck.addView(leftStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(dpadView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
            landscapeDeck.addView(centerCol)
            landscapeDeck.addView(rightStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(faceButtonsView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
        } else {
            landscapeDeck.addView(dpadView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
            landscapeDeck.addView(leftStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(centerCol)
            landscapeDeck.addView(rightStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(faceButtonsView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
        }

        padCard.addView(landscapeDeck)
        gamepadContentContainer.addView(padCard)
    }

    private fun buildDpadCluster(): View {
        val dpadGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowTop.addView(createHatButton("▲", 0), LinearLayout.LayoutParams(dp(44), dp(42)))

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
        }
        rowMid.addView(createHatButton("◀", 6), LinearLayout.LayoutParams(dp(44), dp(42)).apply { marginEnd = dp(18) })
        rowMid.addView(createHatButton("▶", 2), LinearLayout.LayoutParams(dp(44), dp(42)))

        val rowBot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowBot.addView(createHatButton("▼", 4), LinearLayout.LayoutParams(dp(44), dp(42)))

        dpadGrid.addView(rowTop)
        dpadGrid.addView(rowMid)
        dpadGrid.addView(rowBot)
        return dpadGrid
    }

    private fun buildFaceButtonsCluster(): View {
        val cluster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val (topLbl, leftLbl, rightLbl, botLbl) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("Y", "X", "B", "A")
            GamepadStyle.PS5_DUALSENSE -> listOf("△", "□", "○", "✕")
            GamepadStyle.MOBILE_PRO -> listOf("Y", "X", "B", "A")
        }

        val (topColor, leftColor, rightColor, botColor) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("#CA8A04", "#2563EB", "#DC2626", "#16A34A").map { Color.parseColor(it) }
            GamepadStyle.PS5_DUALSENSE -> listOf("#0D9488", "#DB2777", "#E11D48", "#2563EB").map { Color.parseColor(it) }
            GamepadStyle.MOBILE_PRO -> listOf(palette.primary, palette.secondary, palette.danger, palette.success)
        }

        val btnW = dp(46)
        val btnH = dp(42)

        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowTop.addView(createHoldGamepadButton(topLbl, 1 shl 3, topColor), LinearLayout.LayoutParams(btnW, btnH))

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
        }
        rowMid.addView(createHoldGamepadButton(leftLbl, 1 shl 2, leftColor), LinearLayout.LayoutParams(btnW, btnH).apply { marginEnd = dp(16) })
        rowMid.addView(createHoldGamepadButton(rightLbl, 1 shl 1, rightColor), LinearLayout.LayoutParams(btnW, btnH))

        val rowBot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowBot.addView(createHoldGamepadButton(botLbl, 1 shl 0, botColor), LinearLayout.LayoutParams(btnW, btnH))

        cluster.addView(rowTop)
        cluster.addView(rowMid)
        cluster.addView(rowBot)
        return cluster
    }

    // =========================================================================
    // TAB 5: SYSTEM POWER CONTROLS
    // =========================================================================

    private fun buildSystemPowerTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createStyledButton("TV POWER TOGGLE", palette.danger, Color.WHITE) {
            hapticTick()
            btController.sendConsumerKey(0x0030)
        }.apply { layoutParams = weightParams(dp(48)) })
        row1.addView(createStyledButton("SYSTEM SLEEP", palette.warning, Color.BLACK) {
            hapticTick()
            btController.sendSystemControlKey(0x02)
        }.apply { layoutParams = weightParams(dp(48)) })
        tabRoot.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        row2.addView(createStyledButton("SYSTEM WAKE UP", palette.success, Color.BLACK) {
            hapticTick()
            btController.sendSystemControlKey(0x04)
        }.apply { layoutParams = weightParams(dp(48)) })
        row2.addView(createStyledButton("SYSTEM POWER OFF", Color.parseColor("#991B1B"), Color.WHITE) {
            hapticTick()
            btController.sendSystemControlKey(0x01)
        }.apply { layoutParams = weightParams(dp(48)) })
        tabRoot.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        row3.addView(createStyledButton("MEDIA SLEEP", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            btController.sendConsumerKey(0x0032)
        }.apply { layoutParams = weightParams(dp(44)) })
        row3.addView(createStyledButton("LOCK SCREEN", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            btController.sendKeyboardKey(HidDescriptor.MOD_LEFT_GUI, 0x0F)
        }.apply { layoutParams = weightParams(dp(44)) })
        tabRoot.addView(row3)

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        row4.addView(createStyledButton("CLOSE APP (ALT+F4)", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            btController.sendKeyboardKey(HidDescriptor.MOD_LEFT_ALT, 0x3D)
        }.apply { layoutParams = weightParams(dp(44)) })
        row4.addView(createStyledButton("SWITCH APP (ALT+TAB)", palette.surfaceContainer, palette.onSurface) {
            hapticTick()
            btController.sendKeyboardKey(HidDescriptor.MOD_LEFT_ALT, 0x2B)
        }.apply { layoutParams = weightParams(dp(44)) })
        tabRoot.addView(row4)

        return tabRoot
    }

    // =========================================================================
    // TAB 6: DEDICATED SETTINGS PAGE (MATERIAL YOU & MIHON THEMES + GESTURES + SUPPORT)
    // =========================================================================

    private fun buildSettingsTab(): View {
        val settingsRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Appearance & Material You / Mihon Themes Card
        val themeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val themeTitle = TextView(this).apply {
            text = "Appearance & Theme (Material You + Mihon Palettes)"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        themeCard.addView(themeTitle)

        val themesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val themesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (themeOption in AppTheme.values()) {
            val previewPal = themeOption.resolvePalette(this, settings.amoledBlack)
            val isSelected = settings.theme == themeOption
            val strokeCol = if (isSelected) previewPal.onSurface else previewPal.outline
            val strokeW = if (isSelected) dp(3) else dp(1)

            val chip = Button(this).apply {
                text = if (isSelected) "✓ ${themeOption.displayName}" else themeOption.displayName
                setTextColor(previewPal.onPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = AppTheme.materialCard(previewPal.primary, dp(12), strokeCol, strokeW)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(38)
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    hapticTick()
                    settings.theme = themeOption
                    applyThemeLive()
                }
            }
            themesRow.addView(chip)
        }
        themesScroll.addView(themesRow)
        themeCard.addView(themesScroll)

        val amoledToggle = createMaterialToggleRow(
            label = "Pure Black Dark Mode (AMOLED)",
            initial = settings.amoledBlack
        ) { checked ->
            settings.amoledBlack = checked
            applyThemeLive()
        }
        themeCard.addView(amoledToggle)
        settingsRoot.addView(themeCard)

        // 2. Trackpad Multi-Finger Gestures Card
        val gesturesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val gesturesTitle = TextView(this).apply {
            text = "Trackpad Multi-Finger Gestures"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        gesturesCard.addView(gesturesTitle)

        gesturesCard.addView(createMaterialToggleRow("2-Finger Vertical Scroll", settings.twoFingerVertScroll) {
            settings.twoFingerVertScroll = it
        })
        gesturesCard.addView(createMaterialToggleRow("2-Finger Horizontal Scroll", settings.twoFingerHorizScroll) {
            settings.twoFingerHorizScroll = it
        })
        gesturesCard.addView(createMaterialToggleRow("2-Finger Tap for Right Click", settings.twoFingerTapRightClick) {
            settings.twoFingerTapRightClick = it
        })
        gesturesCard.addView(createMaterialToggleRow("2-Finger Pinch-to-Zoom (Ctrl + Wheel)", settings.pinchToZoom) {
            settings.pinchToZoom = it
        })
        gesturesCard.addView(createMaterialToggleRow("3-Finger Swipes (Back / Forward / Home / Apps)", settings.threeFingerSwipeNav) {
            settings.threeFingerSwipeNav = it
        })
        settingsRoot.addView(gesturesCard)

        // 2b. iOS / iPadOS AssistiveTouch Compatibility Card
        val iosCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val iosTitle = TextView(this).apply {
            text = "iOS / iPadOS Compatibility Mode"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        iosCard.addView(iosTitle)

        iosCard.addView(createMaterialToggleRow(
            "iOS Mouse & Keyboard Mode (No Gamepad)
" +
                "Registers a pure 4-byte Mouse + Keyboard descriptor for iPhone/iPad AssistiveTouch. " +
                "After toggling, Forget/Unpair and re-pair via 'Pair New'.",
            settings.iosCompatibilityMode
        ) { checked ->
            settings.iosCompatibilityMode = checked
            btController.iosCompatibilityMode = checked
            btController.resetConnection()
            Toast.makeText(
                this,
                if (checked) "iOS Compatibility Mode ON — Re-registered HID profile"
                else "Full 5-in-1 TV/Gamepad Mode ON — Re-registered HID profile",
                Toast.LENGTH_LONG
            ).show()
        })
        settingsRoot.addView(iosCard)

        // 3. Hardware Volume Button Mouse Clicks Card
        val volCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val volTitle = TextView(this).apply {
            text = "Hardware Volume Button Mouse Clicks"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        volCard.addView(volTitle)

        volCard.addView(createMaterialToggleRow(
            "Use Volume Up / Down as Mouse Clicks\n(Vol Up = Left Click, Vol Down = Right Click)",
            settings.volumeButtonsAsMouse
        ) {
            settings.volumeButtonsAsMouse = it
        })
        volCard.addView(createMaterialToggleRow(
            "Invert Volume Click Buttons\n(Vol Up = Right Click, Vol Down = Left Click)",
            settings.invertVolumeButtons
        ) {
            settings.invertVolumeButtons = it
        })
        settingsRoot.addView(volCard)

        // 3b. App Updates Card (Check for Updates on GitHub Releases)
        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val currentVerName = UpdateChecker.getInstalledVersionName(this)
        val updateTitle = TextView(this).apply {
            text = "App Updates • Installed: v$currentVerName"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
        updateCard.addView(updateTitle)

        val repoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val repoLabel = TextView(this).apply {
            text = "GitHub Repo:"
            setTextColor(palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8), 0)
        }
        repoRow.addView(repoLabel)

        val repoInput = EditText(this).apply {
            setText(settings.githubRepo)
            hint = "AXVin/Air-Mouse"
            setHintTextColor(palette.onSurfaceVariant)
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            isSingleLine = true
            background = AppTheme.materialCard(palette.surfaceContainer, dp(8), palette.outline)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    settings.githubRepo = s?.toString()?.trim() ?: "AXVin/Air-Mouse"
                }
            })
        }
        repoRow.addView(repoInput)
        updateCard.addView(repoRow)

        val checkUpdateBtn = Button(this).apply {
            text = "🔄 Check for Updates on GitHub"
            setTextColor(palette.onPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.primary, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
            setOnClickListener {
                hapticTick()
                val btnRef = this
                btnRef.isEnabled = false
                btnRef.text = "⏳ Checking GitHub Releases..."
                val slug = repoInput.text?.toString()?.trim().orEmpty().ifEmpty { settings.githubRepo }
                settings.githubRepo = slug

                UpdateChecker.checkForUpdates(this@MainActivity, slug) { result ->
                    btnRef.isEnabled = true
                    btnRef.text = "🔄 Check for Updates on GitHub"
                    when (result) {
                        is UpdateChecker.UpdateResult.UpdateAvailable -> {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Update Available: ${result.latestTag}")
                                .setMessage(
                                    "A newer version of Air Mouse is available on GitHub!

" +
                                        "• Installed version: v${result.currentVersion}
" +
                                        "• Latest release: ${result.latestTag}

" +
                                        "Release Notes:
${result.releaseNotes}"
                                )
                                .setPositiveButton("Download & Update APK") { dialog, _ ->
                                    dialog.dismiss()
                                    openExternalUrl(result.apkDownloadUrl)
                                }
                                .setNeutralButton("View on GitHub") { dialog, _ ->
                                    dialog.dismiss()
                                    openExternalUrl(result.releasePageUrl)
                                }
                                .setNegativeButton("Later") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                        is UpdateChecker.UpdateResult.UpToDate -> {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Up to Date")
                                .setMessage(
                                    "You are running the latest version of Air Mouse!

" +
                                        "• Installed version: v${result.currentVersion}
" +
                                        "• Latest GitHub release: ${result.latestTag}"
                                )
                                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                .show()
                        }
                        is UpdateChecker.UpdateResult.Error -> {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Update Check Failed")
                                .setMessage(result.message)
                                .setPositiveButton("Open GitHub Releases") { dialog, _ ->
                                    dialog.dismiss()
                                    openExternalUrl("https://github.com/$slug/releases")
                                }
                                .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
                                .show()
                        }
                    }
                }
            }
        }
        updateCard.addView(checkUpdateBtn)
        settingsRoot.addView(updateCard)

        // 4. Support Creator (AXVin) Card inside Settings
        val supportCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val supportTitle = TextView(this).apply {
            text = "Support AXVin (Creator of Air Mouse)"
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        supportCard.addView(supportTitle)

        val supportBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val patreonBtn = createStyledButton("Support me on Patreon", Color.parseColor("#F96854"), Color.WHITE) {
            openExternalUrl(PATREON_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) }
        }

        val kofiBtn = createStyledButton("Buy me a Ko-fi ☕", Color.parseColor("#29ABE0"), Color.WHITE) {
            openExternalUrl(KOFI_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) }
        }

        supportBtnsRow.addView(patreonBtn)
        supportBtnsRow.addView(kofiBtn)
        supportCard.addView(supportBtnsRow)
        settingsRoot.addView(supportCard)

        return settingsRoot
    }

    /**
     * Custom Material 3 high-contrast toggle card row with a vivid checkmark badge (`✓ ON` / `OFF`)
     * that works reliably across all Android OEM skins without relying on native CheckBox drawables.
     */
    private fun createMaterialToggleRow(
        label: String,
        initial: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        var isCheckedState = initial

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.materialCard(
                palette.surfaceContainer,
                dp(12),
                if (isCheckedState) palette.primary else palette.outline,
                if (isCheckedState) dp(2) else dp(1)
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        val checkBoxBadge = TextView(this).apply {
            text = if (isCheckedState) "✓" else ""
            gravity = Gravity.CENTER
            setTextColor(palette.onPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(
                if (isCheckedState) palette.primary else palette.surface,
                dp(6),
                if (isCheckedState) palette.primary else palette.onSurfaceVariant,
                dp(2)
            )
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(12)
            }
        }

        val labelTv = TextView(this).apply {
            text = label
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val statusPill = TextView(this).apply {
            text = if (isCheckedState) "ON" else "OFF"
            setTextColor(if (isCheckedState) palette.onPrimary else palette.onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(
                if (isCheckedState) palette.primary else palette.surface,
                dp(8),
                palette.outline
            )
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }

        row.setOnClickListener {
            hapticTick()
            isCheckedState = !isCheckedState
            onChanged(isCheckedState)

            checkBoxBadge.text = if (isCheckedState) "✓" else ""
            checkBoxBadge.background = AppTheme.materialCard(
                if (isCheckedState) palette.primary else palette.surface,
                dp(6),
                if (isCheckedState) palette.primary else palette.onSurfaceVariant,
                dp(2)
            )

            statusPill.text = if (isCheckedState) "ON" else "OFF"
            statusPill.setTextColor(if (isCheckedState) palette.onPrimary else palette.onSurfaceVariant)
            statusPill.background = AppTheme.materialCard(
                if (isCheckedState) palette.primary else palette.surface,
                dp(8),
                palette.outline
            )

            row.background = AppTheme.materialCard(
                palette.surfaceContainer,
                dp(12),
                if (isCheckedState) palette.primary else palette.outline,
                if (isCheckedState) dp(2) else dp(1)
            )
        }

        row.addView(checkBoxBadge)
        row.addView(labelTv)
        row.addView(statusPill)
        return row
    }

    // =========================================================================
    // MULTI-FINGER LAPTOP GESTURE TRACKPAD ENGINE
    // =========================================================================

    private fun setScrollLockActive(locked: Boolean, statusLabel: String? = null) {
        rootScrollView.isScrollLocked = locked
        if (locked) {
            touchpadView.background = AppTheme.materialCard(palette.surface, dp(16), palette.primary, dp(2))
            touchpadView.text = statusLabel ?: "TRACKPAD ACTIVE (SCROLL LOCKED)"
            touchpadView.setTextColor(palette.primary)
        } else {
            touchpadView.background = AppTheme.materialCard(palette.surface, dp(16), palette.outline)
            touchpadView.text = "MULTI-GESTURE TRACKPAD (SCROLL LOCKED)\n" +
                "• 1 Finger: Move & Tap (Left Click)\n" +
                "• 2 Fingers: Tap (Right Click), V/H Scroll & Pinch Zoom\n" +
                "• 3 Fingers: Swipe Left/Right (Back/Fwd), Up/Down (Apps/Home)"
            touchpadView.setTextColor(palette.onSurfaceVariant)
        }
    }

    private fun handleMultiGestureTouchpad(view: View, event: MotionEvent): Boolean {
        val count = event.pointerCount
        if (count > maxPointersInGesture) {
            maxPointersInGesture = count
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                setScrollLockActive(true, "1-FINGER CURSOR ACTIVE")
                view.parent?.requestDisallowInterceptTouchEvent(true)
                maxPointersInGesture = 1
                gestureStartTime = System.currentTimeMillis()
                lastSingleX = event.x
                lastSingleY = event.y
                singleMovedDist = 0f
                twoFingerMovedDist = 0f
                threeGestureFired = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                if (count == 2) {
                    lastTwoCenterX = (event.getX(0) + event.getX(1)) * 0.5f
                    lastTwoCenterY = (event.getY(0) + event.getY(1)) * 0.5f
                    lastPinchSpan = hypot(
                        (event.getX(1) - event.getX(0)).toDouble(),
                        (event.getY(1) - event.getY(0)).toDouble()
                    ).toFloat()
                    setScrollLockActive(true, "2-FINGER SCROLL / PINCH ZOOM")
                } else if (count >= 3) {
                    threeStartX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    threeStartY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    threeGestureFired = false
                    setScrollLockActive(true, "3-FINGER SWIPE NAVIGATION")
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                rootScrollView.isScrollLocked = true
                view.parent?.requestDisallowInterceptTouchEvent(true)

                if (count == 1 && maxPointersInGesture == 1) {
                    val dxFloat = (event.x - lastSingleX) * 1.45f
                    val dyFloat = (event.y - lastSingleY) * 1.45f
                    lastSingleX = event.x
                    lastSingleY = event.y

                    singleMovedDist += abs(dxFloat) + abs(dyFloat)
                    val dx = dxFloat.roundToInt().coerceIn(-127, 127)
                    val dy = dyFloat.roundToInt().coerceIn(-127, 127)
                    if (dx != 0 || dy != 0) {
                        btController.sendMouseReport(activeMouseButtons, dx, dy, 0, 0)
                    }
                } else if (count == 2 && maxPointersInGesture == 2) {
                    val cx = (event.getX(0) + event.getX(1)) * 0.5f
                    val cy = (event.getY(0) + event.getY(1)) * 0.5f
                    val span = hypot(
                        (event.getX(1) - event.getX(0)).toDouble(),
                        (event.getY(1) - event.getY(0)).toDouble()
                    ).toFloat()

                    val dSpan = span - lastPinchSpan
                    val dx = cx - lastTwoCenterX
                    val dy = cy - lastTwoCenterY
                    twoFingerMovedDist += abs(dx) + abs(dy) + abs(dSpan)

                    if (settings.pinchToZoom && abs(dSpan) > dp(18) && abs(dSpan) > (abs(dx) + abs(dy)) * 1.15f) {
                        val zoomDir = if (dSpan > 0) 1 else -1
                        btController.sendPinchZoomStep(zoomDir)
                        lastPinchSpan = span
                        lastTwoCenterX = cx
                        lastTwoCenterY = cy
                    } else {
                        if (settings.twoFingerVertScroll && abs(dy) > dp(11) && abs(dy) >= abs(dx)) {
                            val wheelV = if (dy > 0) 1 else -1
                            btController.sendMouseReport(activeMouseButtons, 0, 0, wheelV, 0)
                            lastTwoCenterY = cy
                            lastPinchSpan = span
                        } else if (settings.twoFingerHorizScroll && abs(dx) > dp(11) && abs(dx) > abs(dy)) {
                            val panH = if (dx > 0) -1 else 1
                            btController.sendMouseReport(activeMouseButtons, 0, 0, 0, panH)
                            lastTwoCenterX = cx
                            lastPinchSpan = span
                        }
                    }
                } else if (count >= 3 && settings.threeFingerSwipeNav && !threeGestureFired) {
                    val cx = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    val cy = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    val dx = cx - threeStartX
                    val dy = cy - threeStartY
                    val threshold = dp(42)

                    if (abs(dx) > threshold && abs(dx) > abs(dy)) {
                        threeGestureFired = true
                        hapticTick()
                        if (dx < 0) {
                            btController.sendConsumerKey(0x0224)
                        } else {
                            btController.sendConsumerKey(0x0225)
                        }
                    } else if (abs(dy) > threshold && abs(dy) > abs(dx)) {
                        threeGestureFired = true
                        hapticTick()
                        if (dy < 0) {
                            btController.sendKeyboardKey(HidDescriptor.MOD_LEFT_ALT, 0x2B)
                        } else {
                            btController.sendConsumerKey(0x0223)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setScrollLockActive(false)
                view.parent?.requestDisallowInterceptTouchEvent(false)

                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val elapsed = System.currentTimeMillis() - gestureStartTime
                    if (maxPointersInGesture == 1 && elapsed < 230 && singleMovedDist < dp(10)) {
                        hapticTick()
                        btController.sendMouseReport(1, 0, 0, 0, 0)
                        btController.sendMouseReport(0, 0, 0, 0, 0)
                    } else if (maxPointersInGesture == 2 && settings.twoFingerTapRightClick && elapsed < 260 && twoFingerMovedDist < dp(16)) {
                        hapticTick()
                        btController.sendMouseReport(2, 0, 0, 0, 0)
                        btController.sendMouseReport(0, 0, 0, 0, 0)
                    }
                }
                maxPointersInGesture = 0
                return true
            }
        }
        return true
    }

    private fun handleScrollStripEvent(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rootScrollView.isScrollLocked = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                lastScrollY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                rootScrollView.isScrollLocked = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val dy = event.y - lastScrollY
                if (abs(dy) > dp(12)) {
                    val wheelStep = if (dy > 0) -1 else 1
                    lastScrollY = event.y
                    btController.sendMouseReport(activeMouseButtons, 0, 0, wheelStep, 0)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                rootScrollView.isScrollLocked = false
                view.parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    // =========================================================================
    // HARDWARE VOLUME BUTTON CLICKS & KEY EVENTS
    // =========================================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (settings.volumeButtonsAsMouse &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val buttonBit = if (!settings.invertVolumeButtons) {
                if (isVolUp) 1 else 2
            } else {
                if (isVolUp) 2 else 1
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        hapticTick()
                        activeMouseButtons = activeMouseButtons or buttonBit
                        btController.sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    activeMouseButtons = activeMouseButtons and buttonBit.inv()
                    btController.sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                    return true
                }
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    btController.sendKeyboardKey(0, 0x2A)
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    btController.sendKeyboardKey(0, 0x28)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    btController.sendKeyboardKey(0, 0x52)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    btController.sendKeyboardKey(0, 0x51)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    btController.sendKeyboardKey(0, 0x50)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    btController.sendKeyboardKey(0, 0x4F)
                    return true
                }
            }
            val unicodeChar = event.unicodeChar
            if (unicodeChar > 0) {
                sendCharImmediate(unicodeChar.toChar())
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // =========================================================================
    // GYROSCOPE & ACCELEROMETER MOTION PROCESSING
    // =========================================================================

    private fun registerSensors() {
        try {
            sensorManager?.unregisterListener(this)
            if (gyroSensor != null) {
                sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (accelSensor != null) {
                sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (isGamepadGyroSteering) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val steerRate = -event.values[2]
                leftStickX = (leftStickX + (steerRate * 14f)).roundToInt().coerceIn(-127, 127)
                btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val tiltX = event.values[1]
                leftStickX = (tiltX * 18f).roundToInt().coerceIn(-127, 127)
                btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
            }
        }

        if (!isAirMouseTriggerHeld && !isAirMouseLockedOn) return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val pitch = event.values[0]
            val yaw = event.values[2]

            val deadzone = 0.025f
            val adjYaw = if (abs(yaw) > deadzone) yaw else 0f
            val adjPitch = if (abs(pitch) > deadzone) pitch else 0f

            val rawDx = (-adjYaw * gyroSensitivity) + residualX
            val rawDy = (-adjPitch * gyroSensitivity) + residualY

            val intDx = rawDx.roundToInt().coerceIn(-127, 127)
            val intDy = rawDy.roundToInt().coerceIn(-127, 127)

            residualX = rawDx - intDx
            residualY = rawDy - intDy

            if (intDx != 0 || intDy != 0) {
                btController.sendMouseReport(activeMouseButtons, intDx, intDy, 0, 0)
            }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            if (!accelCalibrated) {
                accelBaseX = ax
                accelBaseY = ay
                accelCalibrated = true
                return
            }
            val tiltX = -(ax - accelBaseX)
            val tiltY = (ay - accelBaseY)
            val deadzone = 0.55f
            val dx = if (abs(tiltX) > deadzone) (tiltX * (gyroSensitivity * 0.18f)).roundToInt().coerceIn(-127, 127) else 0
            val dy = if (abs(tiltY) > deadzone) (tiltY * (gyroSensitivity * 0.18f)).roundToInt().coerceIn(-127, 127) else 0
            if (dx != 0 || dy != 0) {
                btController.sendMouseReport(activeMouseButtons, dx, dy, 0, 0)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =========================================================================
    // DEVICE LIST & QUICK SWITCH CHIPS
    // =========================================================================

    private fun refreshPairedDevicesUi() {
        if (!::deviceAdapter.isInitialized) return
        val names = btController.refreshPairedDevices()
        isUserSelectingDevice = false
        deviceAdapter.clear()
        deviceAdapter.addAll(names)
        deviceAdapter.notifyDataSetChanged()
        rebuildQuickSwitchChips()
    }

    private fun rebuildQuickSwitchChips() {
        if (!::quickSwitchRow.isInitialized) return
        quickSwitchRow.removeAllViews()
        if (btController.pairedDevices.isEmpty()) return

        btController.pairedDevices.forEachIndexed { index, device ->
            val isCurrent = btController.connectedDevice?.address == device.address
            val chipBg = if (isCurrent) palette.primary else palette.surfaceContainer
            val chipText = if (isCurrent) palette.onPrimary else palette.onSurface
            val devName = btController.safeDeviceName(device)
            val chipBtn = Button(this).apply {
                text = if (isCurrent) "● $devName" else devName
                setTextColor(chipText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = AppTheme.materialCard(chipBg, dp(14), palette.outline)
                setPadding(dp(10), dp(3), dp(10), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    hapticTick()
                    isUserSelectingDevice = false
                    deviceSpinner.setSelection(index)
                    btController.switchToDevice(device)
                }
            }
            quickSwitchRow.addView(chipBtn)
        }
    }

    private fun resetLiveInputBuffer() {
        isResettingLiveInput = true
        liveKeyboardInput.setText(SENTINEL_CHAR)
        liveKeyboardInput.setSelection(liveKeyboardInput.text.length)
        isResettingLiveInput = false
    }

    private fun sendCharImmediate(ch: Char) {
        val (mod, code) = HidDescriptor.charToHidUsage(ch)
        if (code != 0) {
            btController.sendKeyboardKey(mod, code)
        }
    }

    // =========================================================================
    // PERMISSIONS & WIDGET BUILDERS
    // =========================================================================

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return btController.hasPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

            if (needed.isNotEmpty()) {
                try {
                    requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS && hasBluetoothPermissions()) {
            refreshPairedDevicesUi()
            btController.initProfile()
        }
    }

    private fun createHoldMouseButton(label: String, buttonMask: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.surfaceContainer, dp(12), palette.outline)
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        activeMouseButtons = activeMouseButtons or buttonMask
                        btController.sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                        background = AppTheme.materialCard(palette.primary, dp(12))
                        setTextColor(palette.onPrimary)
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        activeMouseButtons = activeMouseButtons and buttonMask.inv()
                        btController.sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                        background = AppTheme.materialCard(palette.surfaceContainer, dp(12), palette.outline)
                        setTextColor(palette.onSurface)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createHoldGamepadButton(label: String, bitMask: Int, fillColor: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(fillColor, dp(10), palette.outline)
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        gamepadButtonMask = gamepadButtonMask or bitMask
                        btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
                        background = AppTheme.materialCard(palette.primary, dp(10))
                        setTextColor(palette.onPrimary)
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        gamepadButtonMask = gamepadButtonMask and bitMask.inv()
                        btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
                        background = AppTheme.materialCard(fillColor, dp(10), palette.outline)
                        setTextColor(palette.onSurface)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createHatButton(label: String, hatVal: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(palette.surfaceContainer, dp(10), palette.outline)
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        gamepadHatState = hatVal
                        btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
                        background = AppTheme.materialCard(palette.primary, dp(10))
                        setTextColor(palette.onPrimary)
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        gamepadHatState = 8
                        btController.sendGamepadReport(gamepadButtonMask, gamepadHatState, leftStickX, leftStickY, rightStickX, rightStickY)
                        background = AppTheme.materialCard(palette.surfaceContainer, dp(10), palette.outline)
                        setTextColor(palette.onSurface)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createConsumerButton(label: String, usage16Bit: Int, isPrimary: Boolean = false): Button {
        val bg = if (isPrimary) palette.primary else palette.surfaceContainer
        val fg = if (isPrimary) palette.onPrimary else palette.onSurface
        return createStyledButton(label, bg, fg) {
            hapticTick()
            btController.sendConsumerKey(usage16Bit)
        }
    }

    private fun createStyledButton(label: String, fillInt: Int, textInt: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(textInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = AppTheme.materialCard(fillInt, dp(10), palette.outline)
            setOnClickListener { onClick() }
        }
    }

    private fun weightParams(heightPx: Int = dp(42)): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, heightPx, 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
    }

    private fun hapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(14)
            }
        } catch (_: Throwable) {
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).roundToInt()
    }
}
