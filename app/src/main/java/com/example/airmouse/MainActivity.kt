package com.example.airmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Air Mouse v4.0 — Created by AXVin
 *
 * Key Upgrades in v4.0:
 * - Removed top branding header for maximum screen workspace; moved Status into a Collapsible
 *   Connection Bar at the top that automatically collapses when a Bluetooth device connects.
 * - Top Settings Button (⚙️) to toggle individual multi-finger trackpad gestures and hardware
 *   Volume button mouse-click behavior (Enable/Disable + Invert Volume Up/Down).
 * - 5 High-Visibility Segmented Tabs:
 *   1. "Air Mouse" (Portrait): Enlarged Trackpad with Laptop Multi-Finger Gestures (1-finger move/tap,
 *      2-finger tap right-click, 2-finger vertical & horizontal scroll, 2-finger pinch-to-zoom,
 *      3-finger swipe Back/Forward/Home/Recents) + Gyro Air-Pointer + inline quick keyboard.
 *   2. "Keyboard" (Landscape): Full PC Keyboard with Sticky Modifiers (Ctrl, Alt, Shift, GUI/Win),
 *      Caps Lock toggle, Function keys, Arrow cluster, and easy shortcut combinations (e.g. Ctrl+C).
 *   3. "Media" (Portrait): Fixed Timeline Seek Jog Wheel (sends Arrow Seek / FF-RW reliably) +
 *      Volume Rotary Dial showing live Volume % in the center and a dedicated Mute icon button nearby.
 *   4. "Gamepad" (Landscape): Wide horizontal controller layout with Xbox 360, PS5 DualSense,
 *      and Mobile Pro presets + Gyro Tilt Steering.
 *   5. "System Power" (Portrait): System Power Off, Sleep, Wake, Lock, and App control shortcuts.
 */
@SuppressLint("MissingPermission", "ClickableViewAccessibility", "SetTextI18n")
class MainActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val PREFS_NAME = "air_mouse_prefs"

        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2
        private const val REPORT_ID_CONSUMER = 3
        private const val REPORT_ID_GAMEPAD = 4
        private const val REPORT_ID_SYSTEM = 5

        // Modifier bits (USB HID Keyboard)
        private const val MOD_LEFT_CTRL = 0x01
        private const val MOD_LEFT_SHIFT = 0x02
        private const val MOD_LEFT_ALT = 0x04
        private const val MOD_LEFT_GUI = 0x08

        private const val SENTINEL_CHAR = "\u200B"
        private const val PATREON_URL = "https://www.patreon.com/axvin"
        private const val KOFI_URL = "https://ko-fi.com/axvin"

        // Composite USB/Bluetooth HID Report Descriptor (IDs 1..5)
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // ================= 1. KEYBOARD (Report ID 1) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(),
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224)
            0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x08.toByte(), // Report Count (8)
            0x81.toByte(), 0x02.toByte(), // Input (Modifier byte)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x81.toByte(), 0x01.toByte(), // Input (Reserved byte)
            0x95.toByte(), 0x06.toByte(), // Report Count (6)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x73.toByte(), // Logical Maximum (115)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
            0x29.toByte(), 0x73.toByte(), // Usage Maximum (115)
            0x81.toByte(), 0x00.toByte(), // Input (6 Key Rollover)
            0xC0.toByte(),

            // ================= 2. MOUSE (Report ID 2: Buttons, X, Y, Wheel, AC Pan) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_MOUSE.toByte(),
            0x09.toByte(), 0x01.toByte(), // Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), // Collection (Physical)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Buttons)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1)
            0x29.toByte(), 0x03.toByte(), // Usage Maximum (Button 3)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x81.toByte(), 0x02.toByte(), // Input (3 buttons)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x05.toByte(), // Report Size (5)
            0x81.toByte(), 0x01.toByte(), // Input (5 bit padding)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y)
            0x09.toByte(), 0x38.toByte(), // Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x81.toByte(), 0x06.toByte(), // Input (X, Y, Wheel)
            0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
            0x0A.toByte(), 0x38.toByte(), 0x02.toByte(), // Usage (AC Pan - Horizontal Scroll)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x81.toByte(), 0x06.toByte(), // Input (Horizontal Wheel)
            0xC0.toByte(),
            0xC0.toByte(),

            // ================= 3. CONSUMER MEDIA & TV CONTROL (Report ID 3) =================
            0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
            0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_CONSUMER.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x10.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte(),

            // ================= 4. GAMEPAD CONTROLLER (Report ID 4) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // Usage (Game Pad)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_GAMEPAD.toByte(),
            0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
            0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x10.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x10.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x05.toByte(), 0x01.toByte(), // Hat Switch
            0x09.toByte(), 0x39.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x07.toByte(),
            0x35.toByte(), 0x00.toByte(),
            0x46.toByte(), 0x3B.toByte(), 0x01.toByte(),
            0x65.toByte(), 0x14.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x42.toByte(),
            0x05.toByte(), 0x01.toByte(), // 4 Thumbstick Axes (X, Y, Z, Rz)
            0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(),
            0x09.toByte(), 0x32.toByte(),
            0x09.toByte(), 0x35.toByte(),
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x04.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0xC0.toByte(),

            // ================= 5. SYSTEM POWER CONTROL (Report ID 5) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x80.toByte(), // Usage (System Control)
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), REPORT_ID_SYSTEM.toByte(),
            0x19.toByte(), 0x81.toByte(),
            0x29.toByte(), 0x83.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x05.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0xC0.toByte()
        )
    }

    private class LockableScrollView(context: Context) : ScrollView(context) {
        var isScrollLocked: Boolean = false

        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
            if (isScrollLocked) return false
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent?): Boolean {
            if (isScrollLocked) return false
            return super.onTouchEvent(ev)
        }
    }

    private enum class GamepadStyle {
        XBOX_360, PS5_DUALSENSE, MOBILE_PRO
    }

    // Settings state (persisted in SharedPreferences)
    private lateinit var prefs: SharedPreferences
    private var prefTwoFingerVertScroll = true
    private var prefTwoFingerHorizScroll = true
    private var prefTwoFingerTapRightClick = true
    private var prefPinchToZoom = true
    private var prefThreeFingerSwipeNav = true
    private var prefVolumeButtonsAsMouse = true
    private var prefInvertVolumeButtons = false

    // Bluetooth HID state
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var pairedDevicesList = mutableListOf<BluetoothDevice>()
    private val hidExecutor = Executors.newSingleThreadExecutor()
    private var isUserSelectingDevice = false
    private var isConnectionCardExpanded = true

    // Sensor state
    private lateinit var sensorManager: SensorManager
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
    private var seekWheelUseArrowKeys = true // Default true for universal TV streaming app compatibility
    private var volumeDialViewRef: View? = null
    private var muteButtonRef: Button? = null

    // Full Landscape Keyboard Sticky Modifier state
    private var stickyModifiersMask = 0 // Bitmask of MOD_LEFT_CTRL, MOD_LEFT_SHIFT, MOD_LEFT_ALT, MOD_LEFT_GUI
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

    // UI Elements
    private lateinit var rootScrollView: LockableScrollView
    private lateinit var connectionHeaderStatus: TextView
    private lateinit var connectionCollapseArrow: TextView
    private lateinit var connectionBodyLayout: LinearLayout
    private lateinit var deviceSpinner: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var quickSwitchRow: LinearLayout
    private lateinit var touchpadView: TextView
    private lateinit var liveKeyboardInput: EditText
    private lateinit var airTriggerBtn: Button

    // Tabs
    private var currentTabIndex = 0
    private val tabItemLayouts = mutableListOf<LinearLayout>()
    private val tabTitleViews = mutableListOf<TextView>()
    private val tabIndicatorBars = mutableListOf<View>()
    private val tabSections = mutableListOf<View>()
    private lateinit var gamepadContentContainer: LinearLayout

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSettings()

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter

        buildUserInterface()
        checkAndRequestPermissions()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Preserve connection & active tab seamlessly across portrait/landscape transitions
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        if (hasBluetoothPermissions()) {
            refreshPairedDevices()
            initHidProfile()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {
        }
    }

    private fun loadSettings() {
        prefTwoFingerVertScroll = prefs.getBoolean("two_vert_scroll", true)
        prefTwoFingerHorizScroll = prefs.getBoolean("two_horiz_scroll", true)
        prefTwoFingerTapRightClick = prefs.getBoolean("two_tap_right", true)
        prefPinchToZoom = prefs.getBoolean("pinch_zoom", true)
        prefThreeFingerSwipeNav = prefs.getBoolean("three_swipe_nav", true)
        prefVolumeButtonsAsMouse = prefs.getBoolean("vol_btns_mouse", true)
        prefInvertVolumeButtons = prefs.getBoolean("invert_vol_btns", false)
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("two_vert_scroll", prefTwoFingerVertScroll)
            .putBoolean("two_horiz_scroll", prefTwoFingerHorizScroll)
            .putBoolean("two_tap_right", prefTwoFingerTapRightClick)
            .putBoolean("pinch_zoom", prefPinchToZoom)
            .putBoolean("three_swipe_nav", prefThreeFingerSwipeNav)
            .putBoolean("vol_btns_mouse", prefVolumeButtonsAsMouse)
            .putBoolean("invert_vol_btns", prefInvertVolumeButtons)
            .apply()
    }

    // =========================================================================
    // UI CONSTRUCTION (COLLAPSIBLE CONNECTION TAB + SETTINGS + 5 TABS)
    // =========================================================================

    private fun buildUserInterface() {
        rootScrollView = LockableScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(18))
        }

        // 1. Top Bar: Collapsible Connection Card + Settings Gear Button
        val topBarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val connHeaderToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(10), "#334155")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                hapticTick()
                setConnectionCardExpanded(!isConnectionCardExpanded)
            }
        }

        connectionHeaderStatus = TextView(this).apply {
            text = "🔵 Connection: Initializing HID..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        connHeaderToggle.addView(connectionHeaderStatus)

        connectionCollapseArrow = TextView(this).apply {
            text = "▲ Hide"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        connHeaderToggle.addView(connectionCollapseArrow)
        topBarRow.addView(connHeaderToggle)

        val settingsBtn = Button(this).apply {
            text = "⚙️"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = roundedBox("#1E293B", dp(10), "#38BDF8")
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(42)).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                hapticTick()
                showSettingsDialog()
            }
        }
        topBarRow.addView(settingsBtn)
        container.addView(topBarRow)

        // Collapsible Connection Body
        connectionBodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(10), "#334155")
            setPadding(dp(10), dp(8), dp(10), dp(8))
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
                tv.setTextColor(Color.parseColor("#F8FAFC"))
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.typeface = Typeface.DEFAULT_BOLD
                tv.setPadding(dp(8), dp(6), dp(8), dp(6))
                tv.isSingleLine = true
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (super.getDropDownView(position, convertView, parent) as TextView)
                tv.setTextColor(Color.parseColor("#F8FAFC"))
                tv.setBackgroundColor(Color.parseColor("#0F172A"))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                tv.setPadding(dp(14), dp(11), dp(14), dp(11))
                return tv
            }
        }

        deviceSpinner = Spinner(this).apply {
            adapter = deviceAdapter
            background = roundedBox("#0F172A", dp(8), "#475569")
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isUserSelectingDevice && position in pairedDevicesList.indices) {
                        switchToDeviceInstantly(pairedDevicesList[position])
                    }
                    isUserSelectingDevice = true
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        spinnerRow.addView(deviceSpinner)

        val connectBtn = createStyledButton("Connect", "#0284C7") {
            val idx = deviceSpinner.selectedItemPosition
            if (idx in pairedDevicesList.indices) {
                switchToDeviceInstantly(pairedDevicesList[idx])
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

        btActionsRow.addView(createStyledButton("Reset Conn", "#D97706") { resetHidConnection() }.apply { layoutParams = weightParams(dp(34)) })
        btActionsRow.addView(createStyledButton("Unpair", "#DC2626") { unpairCurrentDevice() }.apply { layoutParams = weightParams(dp(34)) })
        btActionsRow.addView(createStyledButton("Pair New", "#334155") {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                })
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }.apply { layoutParams = weightParams(dp(34)) })
        btActionsRow.addView(createStyledButton("Refresh", "#334155") {
            refreshPairedDevices()
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
        container.addView(connectionBodyLayout)

        // 2. High-Visibility 5-Tab Segmented Switcher Bar:
        // ("Air Mouse", "Keyboard", "Media", "Gamepad", "System Power")
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBox("#1E293B", dp(12), "#475569")
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val tabSpecs = listOf(
            "🖱️" to "Air Mouse",
            "⌨️" to "Keyboard",
            "📺" to "Media",
            "🎮" to "Gamepad",
            "⚡" to "System Power"
        )

        tabItemLayouts.clear()
        tabTitleViews.clear()
        tabIndicatorBars.clear()

        tabSpecs.forEachIndexed { index, (icon, label) ->
            val tabCell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    hapticTick()
                    selectTab(index)
                }
            }

            val iconAndText = TextView(this).apply {
                text = "$icon\n$label"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                setLines(2)
            }
            tabCell.addView(iconAndText)

            val bottomIndicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(3)).apply {
                    topMargin = dp(3)
                }
            }
            tabCell.addView(bottomIndicator)

            tabItemLayouts.add(tabCell)
            tabTitleViews.add(iconAndText)
            tabIndicatorBars.add(bottomIndicator)
            tabsRow.addView(tabCell)
        }
        container.addView(tabsRow)

        // 3. Build All 5 Tab Content Views
        tabSections.clear()
        val tabAirMouse = buildAirMouseTab()
        val tabFullKeyboard = buildFullLandscapeKeyboardTab()
        val tabMedia = buildMediaTab()
        val tabGamepad = buildLandscapeGamepadTab()
        val tabSystemPower = buildSystemPowerTab()

        tabSections.addAll(listOf(tabAirMouse, tabFullKeyboard, tabMedia, tabGamepad, tabSystemPower))
        tabSections.forEach { container.addView(it) }

        // 4. Support Creator Row (Patreon & Ko-fi links)
        val supportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val patreonBtn = createStyledButton("Support AXVin on Patreon", "#F96854") {
            openExternalUrl(PATREON_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) }
        }

        val kofiBtn = createStyledButton("Buy me a Ko-fi ☕", "#29ABE0") {
            openExternalUrl(KOFI_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(5) }
        }

        supportRow.addView(patreonBtn)
        supportRow.addView(kofiBtn)
        container.addView(supportRow)

        rootScrollView.addView(container)
        setContentView(rootScrollView)

        selectTab(0)
    }

    private fun setConnectionCardExpanded(expanded: Boolean) {
        isConnectionCardExpanded = expanded
        connectionBodyLayout.visibility = if (expanded) View.VISIBLE else View.GONE
        connectionCollapseArrow.text = if (expanded) "▲ Hide" else "▼ Devices"
    }

    private fun selectTab(selectedIndex: Int) {
        currentTabIndex = selectedIndex

        // Switch screen orientation automatically:
        // Tabs 1 ("Keyboard") and 3 ("Gamepad") use Landscape; others use Portrait.
        requestedOrientation = if (selectedIndex == 1 || selectedIndex == 3) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        tabItemLayouts.forEachIndexed { idx, cell ->
            val isSelected = idx == selectedIndex
            if (isSelected) {
                cell.background = roundedBox("#0284C7", dp(9), "#38BDF8")
                tabTitleViews[idx].setTextColor(Color.WHITE)
                tabIndicatorBars[idx].background = roundedBox("#38BDF8", dp(2))
            } else {
                cell.background = roundedBox("#0F172A", dp(9), "#334155")
                tabTitleViews[idx].setTextColor(Color.parseColor("#94A3B8"))
                tabIndicatorBars[idx].background = roundedBox("#00000000", dp(2))
            }
        }

        tabSections.forEachIndexed { idx, section ->
            section.visibility = if (idx == selectedIndex) View.VISIBLE else View.GONE
        }
    }

    // =========================================================================
    // SETTINGS DIALOG (GESTURE TOGGLES & VOLUME BUTTON CLICK SETTINGS)
    // =========================================================================

    private fun showSettingsDialog() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }

        fun addSectionHeader(title: String) {
            val tv = TextView(this).apply {
                text = title
                setTextColor(Color.parseColor("#38BDF8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(4))
            }
            layout.addView(tv)
        }

        fun addToggle(label: String, initial: Boolean, onChanged: (Boolean) -> Unit) {
            val cb = CheckBox(this).apply {
                text = label
                isChecked = initial
                setTextColor(Color.parseColor("#F8FAFC"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnCheckedChangeListener { _, checked ->
                    onChanged(checked)
                    saveSettings()
                }
            }
            layout.addView(cb)
        }

        addSectionHeader("Trackpad Multi-Finger Gestures")
        addToggle("2-Finger Vertical Scroll", prefTwoFingerVertScroll) { prefTwoFingerVertScroll = it }
        addToggle("2-Finger Horizontal Scroll", prefTwoFingerHorizScroll) { prefTwoFingerHorizScroll = it }
        addToggle("2-Finger Tap for Right Click", prefTwoFingerTapRightClick) { prefTwoFingerTapRightClick = it }
        addToggle("2-Finger Pinch-to-Zoom (Ctrl + Wheel)", prefPinchToZoom) { prefPinchToZoom = it }
        addToggle("3-Finger Swipe (Back / Forward / Home / Recents)", prefThreeFingerSwipeNav) { prefThreeFingerSwipeNav = it }

        addSectionHeader("Hardware Volume Button Mouse Clicks")
        addToggle("Use Volume Buttons as Mouse Clicks\n(Vol Up = Left Click, Vol Down = Right Click)", prefVolumeButtonsAsMouse) {
            prefVolumeButtonsAsMouse = it
        }
        addToggle("Invert Volume Click Buttons\n(Vol Up = Right Click, Vol Down = Left Click)", prefInvertVolumeButtons) {
            prefInvertVolumeButtons = it
        }

        scroll.addView(layout)

        AlertDialog.Builder(this)
            .setTitle("Air Mouse Settings")
            .setView(scroll)
            .setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // =========================================================================
    // TAB 1: AIR MOUSE (ENLARGED MULTI-GESTURE TRACKPAD + GYRO + QUICK KEYBOARD)
    // =========================================================================

    private fun buildAirMouseTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        // 1. Enlarged Multi-Finger Gesture Trackpad + Scroll Strip (310dp tall)
        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(310)
            )
        }

        touchpadView = TextView(this).apply {
            text = "MULTI-GESTURE TRACKPAD (SCROLL LOCKED)\n" +
                "• 1 Finger: Move & Tap (Left Click)\n" +
                "• 2 Fingers: Tap (Right Click), V/H Scroll & Pinch Zoom\n" +
                "• 3 Fingers: Swipe Left/Right (Back/Fwd), Up/Down (Apps/Home)"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBox("#1E293B", dp(14), "#334155")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { v, event -> handleMultiGestureTouchpad(v, event) }
        }
        padRow.addView(touchpadView)

        val scrollStrip = TextView(this).apply {
            text = "S\nC\nR\nO\nL\nL"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedBox("#1E293B", dp(14), "#334155")
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(6)
            }
            setOnTouchListener { v, event -> handleScrollStripEvent(v, event) }
        }
        padRow.addView(scrollStrip)
        tabRoot.addView(padRow)

        // 2. Mouse Click Buttons Row
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

        // 3. Gyro Air-Mouse Trigger & Speed Card
        val gyroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        airTriggerBtn = Button(this).apply {
            text = "HOLD TO MOVE AIR POINTER (GYRO)"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#2563EB", dp(10))
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
                        background = roundedBox("#1D4ED8", dp(10))
                        text = "AIR POINTER ACTIVE — POINT PHONE AT TV"
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        isAirMouseTriggerHeld = false
                        background = roundedBox("#2563EB", dp(10))
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

        val gyroLockSwitch = Switch(this).apply {
            text = "Lock Air-Mouse"
            setTextColor(Color.parseColor("#E2E8F0"))
            setOnCheckedChangeListener { _, isChecked ->
                isAirMouseLockedOn = isChecked
                accelCalibrated = false
                hapticTick()
            }
        }
        gyroOptionsRow.addView(gyroLockSwitch)

        val sensLabel = TextView(this).apply {
            text = "Speed:"
            setTextColor(Color.parseColor("#94A3B8"))
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

        // 4. Existing Inline Live Keyboard Bar (kept as requested)
        val kbCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
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
                            sendKeyboardKey(0, 0x2A)
                            return true
                        }
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_DEL -> {
                                    sendKeyboardKey(0, 0x2A)
                                    return true
                                }
                                KeyEvent.KEYCODE_ENTER -> {
                                    sendKeyboardKey(0, 0x28)
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
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
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
                        sendKeyboardKey(0, 0x2A)
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
                    sendKeyboardKey(0, 0x28)
                    true
                } else false
            }
        }
        kbCard.addView(liveKeyboardInput)

        val openKbBtn = createStyledButton("Keyboard", "#0284C7") {
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
    // TAB 2: FULL LANDSCAPE PC KEYBOARD (WITH STICKY SHORTCUT MODIFIERS)
    // =========================================================================

    private data class KeySpec(
        val label: String,
        val hidCode: Int = 0,
        val modifierBit: Int = 0,
        val isCapsLock: Boolean = false,
        val weight: Float = 1f,
        val colorHex: String = "#334155"
    )

    private fun buildFullLandscapeKeyboardTab(): View {
        val kbRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        // Top Shortcut Helper Strip (One-tap common combos + Sticky status)
        val shortcutStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val hintText = TextView(this).apply {
            text = "Tip: Tap CTRL, ALT, SHIFT, or WIN to latch it for combos"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
        }
        shortcutStrip.addView(hintText)

        val quickCombos = listOf(
            "Copy" to (MOD_LEFT_CTRL to 0x06),
            "Paste" to (MOD_LEFT_CTRL to 0x19),
            "Undo" to (MOD_LEFT_CTRL to 0x1D),
            "Select All" to (MOD_LEFT_CTRL to 0x04),
            "Alt+Tab" to (MOD_LEFT_ALT to 0x2B)
        )
        for ((lbl, combo) in quickCombos) {
            val btn = createStyledButton(lbl, "#0F172A") {
                hapticTick()
                sendKeyboardKey(combo.first, combo.second)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 0.7f).apply { marginStart = dp(3) }
            }
            shortcutStrip.addView(btn)
        }
        kbRoot.addView(shortcutStrip)

        modifierButtonsMap.clear()

        val rows = listOf(
            // Row 1: ESC + Function keys + DEL
            listOf(
                KeySpec("ESC", 0x29, colorHex = "#DC2626"),
                KeySpec("F1", 0x3A), KeySpec("F2", 0x3B), KeySpec("F3", 0x3C), KeySpec("F4", 0x3D),
                KeySpec("F5", 0x3E), KeySpec("F6", 0x3F), KeySpec("F7", 0x40), KeySpec("F8", 0x41),
                KeySpec("F9", 0x42), KeySpec("F10", 0x43), KeySpec("F11", 0x44), KeySpec("F12", 0x45),
                KeySpec("DEL", 0x4C, colorHex = "#475569")
            ),
            // Row 2: Number Row
            listOf(
                KeySpec("` ~", 0x35),
                KeySpec("1 !", 0x1E), KeySpec("2 @", 0x1F), KeySpec("3 #", 0x20), KeySpec("4 $", 0x21),
                KeySpec("5 %", 0x22), KeySpec("6 ^", 0x23), KeySpec("7 &", 0x24), KeySpec("8 *", 0x25),
                KeySpec("9 (", 0x26), KeySpec("0 )", 0x27), KeySpec("- _", 0x2D), KeySpec("= +", 0x2E),
                KeySpec("⌫ BKSP", 0x2A, weight = 1.6f, colorHex = "#475569")
            ),
            // Row 3: QWERTY Row
            listOf(
                KeySpec("TAB ⇥", 0x2B, weight = 1.4f, colorHex = "#475569"),
                KeySpec("Q", 0x14), KeySpec("W", 0x1A), KeySpec("E", 0x08), KeySpec("R", 0x15),
                KeySpec("T", 0x17), KeySpec("Y", 0x1C), KeySpec("U", 0x18), KeySpec("I", 0x0C),
                KeySpec("O", 0x12), KeySpec("P", 0x13), KeySpec("[ {", 0x2F), KeySpec("] }", 0x30),
                KeySpec("\\ |", 0x31, weight = 1.2f)
            ),
            // Row 4: Home Row with Caps Lock & Enter
            listOf(
                KeySpec("CAPS", 0x39, isCapsLock = true, weight = 1.6f, colorHex = "#475569"),
                KeySpec("A", 0x04), KeySpec("S", 0x16), KeySpec("D", 0x07), KeySpec("F", 0x09),
                KeySpec("G", 0x0A), KeySpec("H", 0x0B), KeySpec("J", 0x0D), KeySpec("K", 0x0E),
                KeySpec("L", 0x0F), KeySpec("; :", 0x33), KeySpec("' \"", 0x34),
                KeySpec("ENTER ↵", 0x28, weight = 1.8f, colorHex = "#0284C7")
            ),
            // Row 5: Shift Row + Up Arrow
            listOf(
                KeySpec("⇧ SHIFT", modifierBit = MOD_LEFT_SHIFT, weight = 1.9f, colorHex = "#475569"),
                KeySpec("Z", 0x1D), KeySpec("X", 0x1B), KeySpec("C", 0x06), KeySpec("V", 0x19),
                KeySpec("B", 0x05), KeySpec("N", 0x11), KeySpec("M", 0x10),
                KeySpec(", <", 0x36), KeySpec(". >", 0x37), KeySpec("/ ?", 0x38),
                KeySpec("▲", 0x52, colorHex = "#0284C7"),
                KeySpec("⇧ SHIFT", modifierBit = MOD_LEFT_SHIFT, weight = 1.5f, colorHex = "#475569")
            ),
            // Row 6: Sticky Modifiers + Spacebar + Arrow Cluster
            listOf(
                KeySpec("CTRL", modifierBit = MOD_LEFT_CTRL, weight = 1.3f, colorHex = "#475569"),
                KeySpec("WIN", modifierBit = MOD_LEFT_GUI, weight = 1.2f, colorHex = "#475569"),
                KeySpec("ALT", modifierBit = MOD_LEFT_ALT, weight = 1.3f, colorHex = "#475569"),
                KeySpec("SPACE", 0x2C, weight = 4.5f, colorHex = "#1E293B"),
                KeySpec("ALT", modifierBit = MOD_LEFT_ALT, weight = 1.2f, colorHex = "#475569"),
                KeySpec("◀", 0x50, colorHex = "#0284C7"),
                KeySpec("▼", 0x51, colorHex = "#0284C7"),
                KeySpec("▶", 0x4F, colorHex = "#0284C7")
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
                val btn = Button(this).apply {
                    text = spec.label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    background = roundedBox(spec.colorHex, dp(6), "#64748B")
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spec.weight).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    setOnClickListener {
                        hapticTick()
                        handleFullKeyboardKeyTap(spec, this)
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

    private fun handleFullKeyboardKeyTap(spec: KeySpec, buttonView: Button) {
        if (spec.modifierBit != 0) {
            // Toggle sticky latch for this modifier (Ctrl, Shift, Alt, Win)
            stickyModifiersMask = stickyModifiersMask xor spec.modifierBit
            updateStickyModifierButtonStyles()
            return
        }

        if (spec.isCapsLock) {
            isCapsLocked = !isCapsLocked
            sendKeyboardKey(0, 0x39) // HID Caps Lock key
            capsButtonRef?.background = if (isCapsLocked) {
                roundedBox("#16A34A", dp(6), "#4ADE80")
            } else {
                roundedBox("#475569", dp(6), "#64748B")
            }
            return
        }

        // Regular key: combine with any latched sticky modifiers + Caps Lock for letters
        var effectiveMod = stickyModifiersMask
        if (isCapsLocked && spec.hidCode in 0x04..0x1D) {
            effectiveMod = effectiveMod xor MOD_LEFT_SHIFT
        }

        sendKeyboardKey(effectiveMod, spec.hidCode)

        // Clear one-shot sticky modifiers after sending the shortcut combo
        if (stickyModifiersMask != 0) {
            stickyModifiersMask = 0
            updateStickyModifierButtonStyles()
        }
    }

    private fun updateStickyModifierButtonStyles() {
        for ((bit, btn) in modifierButtonsMap) {
            val active = (stickyModifiersMask and bit) != 0
            btn.background = if (active) {
                roundedBox("#0284C7", dp(6), "#38BDF8")
            } else {
                roundedBox("#475569", dp(6), "#64748B")
            }
        }
    }

    // =========================================================================
    // TAB 3: MEDIA (VOLUME DIAL WITH CENTER % + NEARBY MUTE ICON + FIXED SEEK JOG WHEEL)
    // =========================================================================

    private fun buildMediaTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val dialsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // Top Options Bar: Mute Icon Button near Volume Dial + Seek Mode Toggle for Jog Wheel
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
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#334155", dp(8), "#475569")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                hapticTick()
                toggleMuteState()
            }
        }
        dialsTopBar.addView(muteButtonRef)

        val seekModeBtn = Button(this).apply {
            text = "Seek Mode: Arrow ◀/▶"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#0284C7", dp(8), "#38BDF8")
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1.2f).apply { marginStart = dp(4) }
            setOnClickListener {
                hapticTick()
                seekWheelUseArrowKeys = !seekWheelUseArrowKeys
                text = if (seekWheelUseArrowKeys) "Seek Mode: Arrow ◀/▶" else "Seek Mode: Media ⏪/⏩"
            }
        }
        dialsTopBar.addView(seekModeBtn)
        dialsCard.addView(dialsTopBar)

        // Dual Circular Dials Row
        val dialsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(185)
            )
        }

        // Left Dial: Volume Rotary Dial showing live Volume % in the center
        val volumeDial = createRotaryDialView(
            title = "VOLUME DIAL",
            accentColor = "#38BDF8",
            centerTextProvider = {
                if (isVolumeMuted) "MUTED" else "$estimatedVolumePercent%"
            },
            centerSubTextProvider = { "VOL" },
            onStepClockwise = { adjustVolumeStep(+2) },
            onStepCounterClockwise = { adjustVolumeStep(-2) },
            onCenterTap = {
                // Tapping center resets volume display to 50% calibration
                estimatedVolumePercent = 50
                isVolumeMuted = false
                updateVolumeUi()
                Toast.makeText(this, "Volume display synced to 50%", Toast.LENGTH_SHORT).show()
            }
        )
        volumeDialViewRef = volumeDial

        // Right Dial: Fixed Seek Jog Wheel (Sends Right/Left Arrow or Media FF/RW smoothly)
        var seekOffsetSec = 0
        val seekJogWheel = createRotaryDialView(
            title = "SEEK JOG WHEEL",
            accentColor = "#A855F7",
            centerTextProvider = {
                when {
                    seekOffsetSec > 0 -> "+${seekOffsetSec}s"
                    seekOffsetSec < 0 -> "${seekOffsetSec}s"
                    else -> "⏯ PLAY"
                }
            },
            centerSubTextProvider = { "SCRUB" },
            onStepClockwise = {
                seekOffsetSec += 5
                performSeekStep(forward = true)
            },
            onStepCounterClockwise = {
                seekOffsetSec -= 5
                performSeekStep(forward = false)
            },
            onCenterTap = {
                seekOffsetSec = 0
                sendConsumerKey(0x00CD) // Play / Pause
            },
            onReleaseWheel = {
                seekOffsetSec = 0
            }
        )

        dialsRow.addView(volumeDial, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(6)
        })
        dialsRow.addView(seekJogWheel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(6)
        })
        dialsCard.addView(dialsRow)
        tabRoot.addView(dialsCard)

        // Media & TV Remote Buttons Grid
        val mediaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createStyledButton("VOL -", "#334155") {
            hapticTick()
            adjustVolumeStep(-2)
        }.apply { layoutParams = weightParams() })
        row1.addView(createStyledButton("🔇 MUTE", "#DC2626") {
            hapticTick()
            toggleMuteState()
        }.apply { layoutParams = weightParams() })
        row1.addView(createStyledButton("VOL +", "#334155") {
            hapticTick()
            adjustVolumeStep(+2)
        }.apply { layoutParams = weightParams() })
        mediaCard.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        row2.addView(createConsumerButton("⏮ PREV", 0x00B6), weightParams())
        row2.addView(createConsumerButton("⏯ PLAY/PAUSE", 0x00CD, "#0284C7"), weightParams())
        row2.addView(createConsumerButton("NEXT ⏭", 0x00B5), weightParams())
        mediaCard.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        row3.addView(createStyledButton("⏪ SEEK -", "#334155") {
            hapticTick()
            performSeekStep(forward = false)
        }.apply { layoutParams = weightParams() })
        row3.addView(createConsumerButton("⏹ STOP", 0x00B7), weightParams())
        row3.addView(createStyledButton("SEEK + ⏩", "#334155") {
            hapticTick()
            performSeekStep(forward = true)
        }.apply { layoutParams = weightParams() })
        mediaCard.addView(row3)

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        row4.addView(createConsumerButton("CH -", 0x009D), weightParams())
        row4.addView(createConsumerButton("TV HOME", 0x0223, "#16A34A"), weightParams())
        row4.addView(createConsumerButton("CH +", 0x009C), weightParams())
        mediaCard.addView(row4)

        val row5 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        row5.addView(createConsumerButton("TV BACK", 0x0224), weightParams())
        row5.addView(createConsumerButton("VOICE / SEARCH", 0x0221), weightParams())
        row5.addView(createConsumerButton("TV BROWSER", 0x0196), weightParams())
        mediaCard.addView(row5)

        tabRoot.addView(mediaCard)
        return tabRoot
    }

    private fun adjustVolumeStep(deltaPercent: Int) {
        isVolumeMuted = false
        estimatedVolumePercent = (estimatedVolumePercent + deltaPercent).coerceIn(0, 100)
        if (deltaPercent > 0) {
            sendConsumerKey(0x00E9) // Volume Up
        } else {
            sendConsumerKey(0x00EA) // Volume Down
        }
        updateVolumeUi()
    }

    private fun toggleMuteState() {
        isVolumeMuted = !isVolumeMuted
        sendConsumerKey(0x00E2) // Mute
        updateVolumeUi()
    }

    private fun updateVolumeUi() {
        volumeDialViewRef?.invalidate()
        muteButtonRef?.apply {
            if (isVolumeMuted) {
                text = "🔇 Muted"
                background = roundedBox("#DC2626", dp(8), "#F87171")
            } else {
                text = "🔊 Mute"
                background = roundedBox("#334155", dp(8), "#475569")
            }
        }
    }

    private fun performSeekStep(forward: Boolean) {
        if (seekWheelUseArrowKeys) {
            // Right Arrow (0x4F) and Left Arrow (0x50) scrub video timelines in YouTube, Netflix, VLC, Kodi, etc.
            sendKeyboardKey(0, if (forward) 0x4F else 0x50)
        } else {
            // Consumer Fast Forward (0x00B3) and Rewind (0x00B4)
            sendConsumerKey(if (forward) 0x00B3 else 0x00B4)
        }
    }

    private fun createRotaryDialView(
        title: String,
        accentColor: String,
        centerTextProvider: () -> String,
        centerSubTextProvider: () -> String,
        onStepClockwise: () -> Unit,
        onStepCounterClockwise: () -> Unit,
        onCenterTap: () -> Unit,
        onReleaseWheel: (() -> Unit)? = null
    ): View {
        return object : View(this) {
            private var currentAngleRad = -Math.PI / 2
            private var lastAngleRad = 0.0
            private var accumulatedAngle = 0.0
            private var downX = 0f
            private var downY = 0f
            private var downTime = 0L

            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentColor)
                style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
            }
            private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentColor)
                style = Paint.Style.FILL
            }
            private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = Paint.Style.FILL
            }
            private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E2E8F0")
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(11).toFloat()
            }
            private val centerMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(14).toFloat()
            }
            private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(8.5f).toFloat()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = (height / 2f) + dp(8)
                val outerR = min(width, height - dp(20)) * 0.44f
                val innerR = outerR * 0.48f

                canvas.drawText(title, cx, dp(14).toFloat(), titlePaint)
                canvas.drawCircle(cx, cy, outerR, ringPaint)
                canvas.drawCircle(cx, cy, outerR, borderPaint)

                for (i in 0 until 24) {
                    val a = (i * Math.PI * 2.0 / 24.0)
                    val x1 = cx + (outerR * 0.80f * cos(a)).toFloat()
                    val y1 = cy + (outerR * 0.80f * sin(a)).toFloat()
                    val x2 = cx + (outerR * 0.92f * cos(a)).toFloat()
                    val y2 = cy + (outerR * 0.92f * sin(a)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, tickPaint)
                }

                val knobDist = outerR * 0.67f
                val kx = cx + (knobDist * cos(currentAngleRad)).toFloat()
                val ky = cy + (knobDist * sin(currentAngleRad)).toFloat()
                canvas.drawCircle(kx, ky, dp(7).toFloat(), knobPaint)

                canvas.drawCircle(cx, cy, innerR, centerPaint)
                canvas.drawCircle(cx, cy, innerR, borderPaint)
                canvas.drawText(centerTextProvider(), cx, cy + dp(2), centerMainPaint)
                canvas.drawText(centerSubTextProvider(), cx, cy + dp(14), centerSubPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val cx = width / 2f
                val cy = (height / 2f) + dp(8)
                val outerR = min(width, height - dp(20)) * 0.44f
                val innerR = outerR * 0.48f
                val dx = event.x - cx
                val dy = event.y - cy
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()
                        lastAngleRad = atan2(dy.toDouble(), dx.toDouble())
                        if (dist > innerR * 0.65f) {
                            currentAngleRad = lastAngleRad
                        }
                        accumulatedAngle = 0.0
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        rootScrollView.isScrollLocked = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        if (dist > dp(14)) {
                            val angle = atan2(dy.toDouble(), dx.toDouble())
                            var delta = angle - lastAngleRad
                            if (delta > Math.PI) delta -= 2.0 * Math.PI
                            if (delta < -Math.PI) delta += 2.0 * Math.PI

                            accumulatedAngle += delta
                            lastAngleRad = angle
                            currentAngleRad = angle

                            val stepThreshold = Math.PI / 14.0 // Responsive ~12.8° per detent step
                            while (accumulatedAngle >= stepThreshold) {
                                accumulatedAngle -= stepThreshold
                                hapticTick()
                                onStepClockwise()
                            }
                            while (accumulatedAngle <= -stepThreshold) {
                                accumulatedAngle += stepThreshold
                                hapticTick()
                                onStepCounterClockwise()
                            }
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            val elapsed = System.currentTimeMillis() - downTime
                            val moved = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                            if (dist <= innerR && elapsed < 300 && moved < dp(12)) {
                                hapticTick()
                                onCenterTap()
                            }
                        }
                        onReleaseWheel?.invoke()
                        invalidate()
                        return true
                    }
                }
                return false
            }
        }
    }

    // =========================================================================
    // TAB 4: LANDSCAPE GAMEPAD CONTROLLER (HORIZONTAL WIDESCREEN LAYOUT)
    // =========================================================================

    private fun buildLandscapeGamepadTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
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

        val themeBg = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> "#14261C"
            GamepadStyle.PS5_DUALSENSE -> "#131C31"
            GamepadStyle.MOBILE_PRO -> "#1E1B2E"
        }
        val themeBorder = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> "#107C10"
            GamepadStyle.PS5_DUALSENSE -> "#2563EB"
            GamepadStyle.MOBILE_PRO -> "#8B5CF6"
        }

        val padCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox(themeBg, dp(14), themeBorder)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        // Row 1 (Horizontal Top Bar): Left Triggers (LT/LB) + Style Switcher + Right Triggers (RB/RT)
        val topTriggersAndStyleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val (l2Name, l1Name, r1Name, r2Name) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("LT", "LB", "RB", "RT")
            GamepadStyle.PS5_DUALSENSE -> listOf("L2", "L1", "R1", "R2")
            GamepadStyle.MOBILE_PRO -> listOf("L2", "L1", "R1", "R2")
        }

        topTriggersAndStyleRow.addView(createHoldGamepadButton(l2Name, 1 shl 6, "#334155"), LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        topTriggersAndStyleRow.addView(createHoldGamepadButton(l1Name, 1 shl 4, "#475569"), LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })

        // Center Style Pills (Xbox 360 | PS5 | Mobile)
        topTriggersAndStyleRow.addView(createStyledButton("Xbox", "#107C10") {
            currentGamepadStyle = GamepadStyle.XBOX_360
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.85f).apply { marginEnd = dp(3) } })

        topTriggersAndStyleRow.addView(createStyledButton("PS5", "#00439C") {
            currentGamepadStyle = GamepadStyle.PS5_DUALSENSE
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.85f).apply { marginEnd = dp(3) } })

        topTriggersAndStyleRow.addView(createStyledButton("Mobile", "#7C3AED") {
            currentGamepadStyle = GamepadStyle.MOBILE_PRO
            rebuildGamepadLayout()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.85f).apply { marginEnd = dp(6) } })

        topTriggersAndStyleRow.addView(createHoldGamepadButton(r1Name, 1 shl 5, "#475569"), LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        topTriggersAndStyleRow.addView(createHoldGamepadButton(r2Name, 1 shl 7, "#334155"), LinearLayout.LayoutParams(0, dp(40), 1f))
        padCard.addView(topTriggersAndStyleRow)

        // Row 2 (Horizontal Widescreen Deck):
        // [Col 1: Left Stick / D-Pad] [Col 2: D-Pad / Left Stick] [Col 3: Center Controls + Gyro] [Col 4: Right Stick] [Col 5: Face Buttons]
        val landscapeDeck = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(175)
            ).apply { topMargin = dp(8) }
        }

        val leftStickView = createVirtualJoystickView("L-STICK", themeBorder) { x, y ->
            leftStickX = x
            leftStickY = y
            sendGamepadReport()
        }
        val dpadView = buildDpadCluster()
        val rightStickView = createVirtualJoystickView("R-STICK", themeBorder) { x, y ->
            rightStickX = x
            rightStickY = y
            sendGamepadReport()
        }
        val faceButtonsView = buildFaceButtonsCluster()

        // Center Column: Select/Back, Guide/PS, Start/Options, L3/R3, Gyro Tilt Steer
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
        sysBtnsRow.addView(createHoldGamepadButton(selectLabel, 1 shl 8, "#334155"), weightParams(dp(34)))
        sysBtnsRow.addView(createHoldGamepadButton(guideLabel, 1 shl 12, themeBorder), weightParams(dp(34)))
        sysBtnsRow.addView(createHoldGamepadButton(startLabel, 1 shl 9, "#334155"), weightParams(dp(34)))
        centerCol.addView(sysBtnsRow)

        val l3r3Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        l3r3Row.addView(createHoldGamepadButton("L3", 1 shl 10, "#334155"), weightParams(dp(34)))
        l3r3Row.addView(createHoldGamepadButton("R3", 1 shl 11, "#334155"), weightParams(dp(34)))
        centerCol.addView(l3r3Row)

        val gyroBtn = createStyledButton(
            if (isGamepadGyroSteering) "Gyro Steer: ON" else "Gyro Steer: OFF",
            if (isGamepadGyroSteering) "#16A34A" else "#334155"
        ) {
            isGamepadGyroSteering = !isGamepadGyroSteering
            if (!isGamepadGyroSteering) {
                leftStickX = 0
                leftStickY = 0
                sendGamepadReport()
            }
            rebuildGamepadLayout()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                topMargin = dp(6)
            }
        }
        centerCol.addView(gyroBtn)

        // Arrange Left & Right clusters horizontally based on controller style
        if (currentGamepadStyle == GamepadStyle.XBOX_360) {
            // Xbox 360: Left Stick outer-left, D-Pad inner-left, Center, Right Stick inner-right, ABXY outer-right
            landscapeDeck.addView(leftStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(dpadView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
            landscapeDeck.addView(centerCol)
            landscapeDeck.addView(rightStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            landscapeDeck.addView(faceButtonsView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
        } else {
            // PS5 DualSense / Mobile Pro: D-Pad outer-left, Left Stick inner-left, Center, Right Stick inner-right, Face Buttons outer-right
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
            GamepadStyle.XBOX_360 -> listOf("#CA8A04", "#2563EB", "#DC2626", "#16A34A")
            GamepadStyle.PS5_DUALSENSE -> listOf("#0D9488", "#DB2777", "#E11D48", "#2563EB")
            GamepadStyle.MOBILE_PRO -> listOf("#7C3AED", "#2563EB", "#DC2626", "#16A34A")
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

    private fun createVirtualJoystickView(
        label: String,
        accentHex: String,
        onStickMoved: (Int, Int) -> Unit
    ): View {
        return object : View(this) {
            private var knobX = 0f
            private var knobY = 0f
            private var isTouching = false

            private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentHex)
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentHex)
                style = Paint.Style.FILL
            }
            private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(9.5f).toFloat()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = (height / 2f) + dp(5)
                val maxR = min(width, height - dp(14)) * 0.42f
                val thumbR = maxR * 0.42f

                canvas.drawText(label, cx, dp(12).toFloat(), labelPaint)
                canvas.drawCircle(cx, cy, maxR, basePaint)
                canvas.drawCircle(cx, cy, maxR, ringPaint)

                val drawX = if (isTouching) cx + knobX else cx
                val drawY = if (isTouching) cy + knobY else cy
                canvas.drawCircle(drawX, drawY, thumbR, stickPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val cx = width / 2f
                val cy = (height / 2f) + dp(5)
                val maxR = min(width, height - dp(14)) * 0.42f

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        rootScrollView.isScrollLocked = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        isTouching = true
                        var dx = event.x - cx
                        var dy = event.y - cy
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist > maxR && maxR > 0) {
                            dx = (dx / dist) * maxR
                            dy = (dy / dist) * maxR
                        }
                        knobX = dx
                        knobY = dy
                        val normX = ((dx / maxR) * 127f).roundToInt().coerceIn(-127, 127)
                        val normY = ((dy / maxR) * 127f).roundToInt().coerceIn(-127, 127)
                        onStickMoved(normX, normY)
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        isTouching = false
                        knobX = 0f
                        knobY = 0f
                        onStickMoved(0, 0)
                        invalidate()
                        return true
                    }
                }
                return false
            }
        }
    }

    // =========================================================================
    // TAB 5: SYSTEM POWER CONTROLS
    // =========================================================================

    private fun buildSystemPowerTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createStyledButton("TV POWER TOGGLE", "#DC2626") {
            hapticTick()
            sendConsumerKey(0x0030)
        }.apply { layoutParams = weightParams(dp(48)) })
        row1.addView(createStyledButton("SYSTEM SLEEP", "#D97706") {
            hapticTick()
            sendSystemControlKey(0x02)
        }.apply { layoutParams = weightParams(dp(48)) })
        tabRoot.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row2.addView(createStyledButton("SYSTEM WAKE UP", "#16A34A") {
            hapticTick()
            sendSystemControlKey(0x04)
        }.apply { layoutParams = weightParams(dp(48)) })
        row2.addView(createStyledButton("SYSTEM POWER OFF", "#991B1B") {
            hapticTick()
            sendSystemControlKey(0x01)
        }.apply { layoutParams = weightParams(dp(48)) })
        tabRoot.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row3.addView(createStyledButton("MEDIA SLEEP", "#475569") {
            hapticTick()
            sendConsumerKey(0x0032)
        }.apply { layoutParams = weightParams(dp(44)) })
        row3.addView(createStyledButton("LOCK SCREEN", "#475569") {
            hapticTick()
            sendKeyboardKey(MOD_LEFT_GUI, 0x0F)
        }.apply { layoutParams = weightParams(dp(44)) })
        tabRoot.addView(row3)

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row4.addView(createStyledButton("CLOSE APP (ALT+F4)", "#475569") {
            hapticTick()
            sendKeyboardKey(MOD_LEFT_ALT, 0x3D)
        }.apply { layoutParams = weightParams(dp(44)) })
        row4.addView(createStyledButton("SWITCH APP (ALT+TAB)", "#475569") {
            hapticTick()
            sendKeyboardKey(MOD_LEFT_ALT, 0x2B)
        }.apply { layoutParams = weightParams(dp(44)) })
        tabRoot.addView(row4)

        return tabRoot
    }

    // =========================================================================
    // MULTI-FINGER LAPTOP GESTURE TRACKPAD ENGINE
    // =========================================================================

    private fun setScrollLockActive(locked: Boolean, statusLabel: String? = null) {
        rootScrollView.isScrollLocked = locked
        if (locked) {
            touchpadView.background = roundedBox("#1E293B", dp(14), "#38BDF8")
            touchpadView.text = statusLabel ?: "TRACKPAD ACTIVE (SCROLL LOCKED)"
            touchpadView.setTextColor(Color.parseColor("#38BDF8"))
        } else {
            touchpadView.background = roundedBox("#1E293B", dp(14), "#334155")
            touchpadView.text = "MULTI-GESTURE TRACKPAD (SCROLL LOCKED)\n" +
                "• 1 Finger: Move & Tap (Left Click)\n" +
                "• 2 Fingers: Tap (Right Click), V/H Scroll & Pinch Zoom\n" +
                "• 3 Fingers: Swipe Left/Right (Back/Fwd), Up/Down (Apps/Home)"
            touchpadView.setTextColor(Color.parseColor("#94A3B8"))
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
                    // 1-Finger Cursor Movement
                    val dxFloat = (event.x - lastSingleX) * 1.45f
                    val dyFloat = (event.y - lastSingleY) * 1.45f
                    lastSingleX = event.x
                    lastSingleY = event.y

                    singleMovedDist += abs(dxFloat) + abs(dyFloat)
                    val dx = dxFloat.roundToInt().coerceIn(-127, 127)
                    val dy = dyFloat.roundToInt().coerceIn(-127, 127)
                    if (dx != 0 || dy != 0) {
                        sendMouseReport(activeMouseButtons, dx, dy, 0, 0)
                    }
                } else if (count == 2 && maxPointersInGesture == 2) {
                    // 2-Finger Gestures: Pinch-to-Zoom OR Vertical/Horizontal Two-Finger Scroll
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

                    if (prefPinchToZoom && abs(dSpan) > dp(18) && abs(dSpan) > (abs(dx) + abs(dy)) * 1.15f) {
                        // Pinch-to-Zoom -> Ctrl + Mouse Wheel
                        val zoomDir = if (dSpan > 0) 1 else -1
                        sendPinchZoomStep(zoomDir)
                        lastPinchSpan = span
                        lastTwoCenterX = cx
                        lastTwoCenterY = cy
                    } else {
                        // 2-Finger Vertical & Horizontal Scroll
                        if (prefTwoFingerVertScroll && abs(dy) > dp(11) && abs(dy) >= abs(dx)) {
                            val wheelV = if (dy > 0) 1 else -1 // Natural laptop two-finger vertical scroll
                            sendMouseReport(activeMouseButtons, 0, 0, wheelV, 0)
                            lastTwoCenterY = cy
                            lastPinchSpan = span
                        } else if (prefTwoFingerHorizScroll && abs(dx) > dp(11) && abs(dx) > abs(dy)) {
                            val panH = if (dx > 0) -1 else 1
                            sendMouseReport(activeMouseButtons, 0, 0, 0, panH)
                            lastTwoCenterX = cx
                            lastPinchSpan = span
                        }
                    }
                } else if (count >= 3 && prefThreeFingerSwipeNav && !threeGestureFired) {
                    // 3-Finger Laptop Swipes: Left = Back, Right = Forward, Up = App Switcher, Down = Home
                    val cx = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    val cy = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    val dx = cx - threeStartX
                    val dy = cy - threeStartY
                    val threshold = dp(42)

                    if (abs(dx) > threshold && abs(dx) > abs(dy)) {
                        threeGestureFired = true
                        hapticTick()
                        if (dx < 0) {
                            // 3-Finger Swipe Left -> Navigate Back
                            sendConsumerKey(0x0224) // AC Back
                        } else {
                            // 3-Finger Swipe Right -> Navigate Forward
                            sendConsumerKey(0x0225) // AC Forward
                        }
                    } else if (abs(dy) > threshold && abs(dy) > abs(dx)) {
                        threeGestureFired = true
                        hapticTick()
                        if (dy < 0) {
                            // 3-Finger Swipe Up -> App Switcher / Multitasking
                            sendKeyboardKey(MOD_LEFT_ALT, 0x2B)
                        } else {
                            // 3-Finger Swipe Down -> TV Home
                            sendConsumerKey(0x0223)
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
                        // 1-Finger Tap -> Left Click
                        hapticTick()
                        sendMouseReport(1, 0, 0, 0, 0)
                        sendMouseReport(0, 0, 0, 0, 0)
                    } else if (maxPointersInGesture == 2 && prefTwoFingerTapRightClick && elapsed < 260 && twoFingerMovedDist < dp(16)) {
                        // 2-Finger Tap -> Right Click
                        hapticTick()
                        sendMouseReport(2, 0, 0, 0, 0)
                        sendMouseReport(0, 0, 0, 0, 0)
                    }
                }
                maxPointersInGesture = 0
                return true
            }
        }
        return true
    }

    private fun sendPinchZoomStep(wheelStep: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        // Hold Left Ctrl (0x01) + Scroll Wheel, then release Left Ctrl
        val ctrlDown = byteArrayOf(MOD_LEFT_CTRL.toByte(), 0, 0, 0, 0, 0, 0, 0)
        val ctrlUp = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        hid.sendReport(target, REPORT_ID_KEYBOARD, ctrlDown)
        sendMouseReport(0, 0, 0, wheelStep, 0)
        hid.sendReport(target, REPORT_ID_KEYBOARD, ctrlUp)
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
                    sendMouseReport(activeMouseButtons, 0, 0, wheelStep, 0)
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
    // HARDWARE VOLUME UP/DOWN BUTTONS AS LEFT/RIGHT MOUSE CLICKS
    // =========================================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 1. Intercept Hardware Volume Up / Down as Left / Right Mouse Clicks when enabled in Settings
        if (prefVolumeButtonsAsMouse &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val buttonBit = if (!prefInvertVolumeButtons) {
                if (isVolUp) 1 else 2 // Default: Volume Up = Left Click (1), Volume Down = Right Click (2)
            } else {
                if (isVolUp) 2 else 1 // Inverted: Volume Up = Right Click (2), Volume Down = Left Click (1)
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        hapticTick()
                        activeMouseButtons = activeMouseButtons or buttonBit
                        sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    activeMouseButtons = activeMouseButtons and buttonBit.inv()
                    sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                    return true
                }
            }
            return true
        }

        // 2. Standard Keyboard Key Passthrough
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    sendKeyboardKey(0, 0x2A)
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    sendKeyboardKey(0, 0x28)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    sendKeyboardKey(0, 0x52)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    sendKeyboardKey(0, 0x51)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    sendKeyboardKey(0, 0x50)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    sendKeyboardKey(0, 0x4F)
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
        sensorManager.unregisterListener(this)
        if (gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (isGamepadGyroSteering) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val steerRate = -event.values[2]
                leftStickX = (leftStickX + (steerRate * 14f)).roundToInt().coerceIn(-127, 127)
                sendGamepadReport()
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val tiltX = event.values[1] // In landscape orientation, Y axis is left/right tilt
                leftStickX = (tiltX * 18f).roundToInt().coerceIn(-127, 127)
                sendGamepadReport()
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
                sendMouseReport(activeMouseButtons, intDx, intDy, 0, 0)
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
                sendMouseReport(activeMouseButtons, dx, dy, 0, 0)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =========================================================================
    // BLUETOOTH HID PROFILE & AUTO-COLLAPSING CONNECTION CARD
    // =========================================================================

    private fun initHidProfile() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            connectionHeaderStatus.text = "🔴 Bluetooth Disabled — Tap to Enable"
            return
        }
        if (hidDevice != null) return

        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                val hid = proxy as? BluetoothHidDevice ?: return
                hidDevice = hid

                val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                    "Air Mouse",
                    "Air Mouse, Keyboard, Media & Gamepad by AXVin",
                    "AXVin",
                    BluetoothHidDevice.SUBCLASS1_COMBO,
                    HID_REPORT_DESCRIPTOR
                )

                val qosOut = BluetoothHidDeviceAppQosSettings(
                    BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                    800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
                )

                hid.registerApp(sdpSettings, null, qosOut, hidExecutor, object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        runOnUiThread {
                            if (registered) {
                                connectionHeaderStatus.text = "🔵 HID Ready — Select a Paired Device"
                                connectionHeaderStatus.setTextColor(Color.parseColor("#38BDF8"))
                                if (pluggedDevice != null) {
                                    switchToDeviceInstantly(pluggedDevice)
                                }
                            } else {
                                connectionHeaderStatus.text = "⚪ HID Service Stopped"
                            }
                        }
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        runOnUiThread {
                            when (state) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connectedDevice = device
                                    connectionHeaderStatus.text = "🟢 Connected: ${device?.name ?: "TV"}"
                                    connectionHeaderStatus.setTextColor(Color.parseColor("#4ADE80"))
                                    hapticTick()
                                    rebuildQuickSwitchChips()
                                    // Automatically collapse the Connection Panel when a device connects
                                    setConnectionCardExpanded(false)
                                }
                                BluetoothProfile.STATE_CONNECTING -> {
                                    connectionHeaderStatus.text = "🟡 Connecting to ${device?.name ?: "Device"}..."
                                    connectionHeaderStatus.setTextColor(Color.parseColor("#FACC15"))
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    if (connectedDevice == device) connectedDevice = null
                                    connectionHeaderStatus.text = "🔴 Disconnected — Tap to Connect"
                                    connectionHeaderStatus.setTextColor(Color.parseColor("#F87171"))
                                    rebuildQuickSwitchChips()
                                    setConnectionCardExpanded(true)
                                }
                            }
                        }
                    }
                })
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    private fun refreshPairedDevices() {
        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        pairedDevicesList.clear()
        pairedDevicesList.addAll(bonded)

        val names = if (pairedDevicesList.isEmpty()) {
            listOf("No Paired Devices (Tap 'Pair New')")
        } else {
            pairedDevicesList.map { "${it.name ?: "Unknown"} (${it.address})" }
        }
        isUserSelectingDevice = false
        deviceAdapter.clear()
        deviceAdapter.addAll(names)
        deviceAdapter.notifyDataSetChanged()

        rebuildQuickSwitchChips()
    }

    private fun rebuildQuickSwitchChips() {
        quickSwitchRow.removeAllViews()
        if (pairedDevicesList.isEmpty()) return

        pairedDevicesList.forEachIndexed { index, device ->
            val isCurrent = connectedDevice?.address == device.address
            val chipColor = if (isCurrent) "#16A34A" else "#0F172A"
            val chipStroke = if (isCurrent) "#4ADE80" else "#475569"
            val chipBtn = Button(this).apply {
                text = if (isCurrent) "● ${device.name ?: "Device"}" else (device.name ?: "Device")
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBox(chipColor, dp(14), chipStroke)
                setPadding(dp(10), dp(3), dp(10), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    hapticTick()
                    isUserSelectingDevice = false
                    deviceSpinner.setSelection(index)
                    switchToDeviceInstantly(device)
                }
            }
            quickSwitchRow.addView(chipBtn)
        }
    }

    private fun switchToDeviceInstantly(targetDevice: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            Toast.makeText(this, "HID initializing, re-registering...", Toast.LENGTH_SHORT).show()
            initHidProfile()
            return
        }
        val prev = connectedDevice
        if (prev != null && prev.address != targetDevice.address) {
            try {
                hid.disconnect(prev)
            } catch (_: Exception) {
            }
        }
        connectedDevice = targetDevice
        hid.connect(targetDevice)
        rebuildQuickSwitchChips()
    }

    private fun resetHidConnection() {
        hapticTick()
        connectionHeaderStatus.text = "🟡 Resetting Bluetooth HID..."
        connectionHeaderStatus.setTextColor(Color.parseColor("#FACC15"))
        try {
            connectedDevice?.let { hidDevice?.disconnect(it) }
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {
        }
        hidDevice = null
        connectedDevice = null
        refreshPairedDevices()
        initHidProfile()
        Toast.makeText(this, "Bluetooth HID connection reset", Toast.LENGTH_SHORT).show()
    }

    private fun unpairCurrentDevice() {
        hapticTick()
        val idx = deviceSpinner.selectedItemPosition
        val target = connectedDevice ?: pairedDevicesList.getOrNull(idx)
        if (target == null) {
            Toast.makeText(this, "No paired device selected to unpair", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            hidDevice?.disconnect(target)
        } catch (_: Exception) {
        }

        val removed = try {
            val method = target.javaClass.getMethod("removeBond")
            method.invoke(target) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }

        if (removed) {
            Toast.makeText(this, "Unpaired ${target.name ?: target.address}", Toast.LENGTH_SHORT).show()
            connectedDevice = null
            connectionHeaderStatus.text = "🔴 Unpaired ${target.name ?: "Device"}"
            connectionHeaderStatus.setTextColor(Color.parseColor("#F87171"))
            window.decorView.postDelayed({ refreshPairedDevices() }, 600)
        } else {
            Toast.makeText(this, "Opening Bluetooth settings to unpair ${target.name ?: ""}", Toast.LENGTH_SHORT).show()
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    // =========================================================================
    // HID REPORT SENDERS
    // =========================================================================

    private fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int, panH: Int = 0) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte(),
            panH.coerceIn(-127, 127).toByte()
        )
        hid.sendReport(target, REPORT_ID_MOUSE, report)
    }

    private fun sendKeyboardKey(modifier: Int, hidUsageCode: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val downReport = byteArrayOf(
            modifier.toByte(),
            0x00,
            hidUsageCode.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00
        )
        val upReport = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        hid.sendReport(target, REPORT_ID_KEYBOARD, downReport)
        hid.sendReport(target, REPORT_ID_KEYBOARD, upReport)
    }

    private fun sendConsumerKey(usage16Bit: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val low = (usage16Bit and 0xFF).toByte()
        val high = ((usage16Bit shr 8) and 0xFF).toByte()
        hid.sendReport(target, REPORT_ID_CONSUMER, byteArrayOf(low, high))
        hid.sendReport(target, REPORT_ID_CONSUMER, byteArrayOf(0x00, 0x00))
    }

    private fun sendGamepadReport() {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val btnLow = (gamepadButtonMask and 0xFF).toByte()
        val btnHigh = ((gamepadButtonMask shr 8) and 0xFF).toByte()
        val report = byteArrayOf(
            btnLow,
            btnHigh,
            gamepadHatState.toByte(),
            leftStickX.coerceIn(-127, 127).toByte(),
            leftStickY.coerceIn(-127, 127).toByte(),
            rightStickX.coerceIn(-127, 127).toByte(),
            rightStickY.coerceIn(-127, 127).toByte()
        )
        hid.sendReport(target, REPORT_ID_GAMEPAD, report)
    }

    private fun sendSystemControlKey(bitMask: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        hid.sendReport(target, REPORT_ID_SYSTEM, byteArrayOf(bitMask.toByte()))
        hid.sendReport(target, REPORT_ID_SYSTEM, byteArrayOf(0x00))
    }

    private fun resetLiveInputBuffer() {
        isResettingLiveInput = true
        liveKeyboardInput.setText(SENTINEL_CHAR)
        liveKeyboardInput.setSelection(liveKeyboardInput.text.length)
        isResettingLiveInput = false
    }

    private fun sendCharImmediate(ch: Char) {
        val (mod, code) = charToHidUsage(ch)
        if (code != 0) {
            sendKeyboardKey(mod, code)
        }
    }

    private fun charToHidUsage(c: Char): Pair<Int, Int> {
        return when (c) {
            in 'a'..'z' -> 0x00 to (0x04 + (c - 'a'))
            in 'A'..'Z' -> MOD_LEFT_SHIFT to (0x04 + (c - 'A'))
            in '1'..'9' -> 0x00 to (0x1E + (c - '1'))
            '0' -> 0x00 to 0x27
            ' ' -> 0x00 to 0x2C
            '\n' -> 0x00 to 0x28
            '!' -> MOD_LEFT_SHIFT to 0x1E
            '@' -> MOD_LEFT_SHIFT to 0x1F
            '#' -> MOD_LEFT_SHIFT to 0x20
            '$' -> MOD_LEFT_SHIFT to 0x21
            '%' -> MOD_LEFT_SHIFT to 0x22
            '^' -> MOD_LEFT_SHIFT to 0x23
            '&' -> MOD_LEFT_SHIFT to 0x24
            '*' -> MOD_LEFT_SHIFT to 0x25
            '(' -> MOD_LEFT_SHIFT to 0x26
            ')' -> MOD_LEFT_SHIFT to 0x27
            '-' -> 0x00 to 0x2D
            '_' -> MOD_LEFT_SHIFT to 0x2D
            '=' -> 0x00 to 0x2E
            '+' -> MOD_LEFT_SHIFT to 0x2E
            '[' -> 0x00 to 0x2F
            '{' -> MOD_LEFT_SHIFT to 0x2F
            ']' -> 0x00 to 0x30
            '}' -> MOD_LEFT_SHIFT to 0x30
            '\\' -> 0x00 to 0x31
            '|' -> MOD_LEFT_SHIFT to 0x31
            ';' -> 0x00 to 0x33
            ':' -> MOD_LEFT_SHIFT to 0x33
            '\'' -> 0x00 to 0x34
            '"' -> MOD_LEFT_SHIFT to 0x34
            '`' -> 0x00 to 0x35
            '~' -> MOD_LEFT_SHIFT to 0x35
            ',' -> 0x00 to 0x36
            '<' -> MOD_LEFT_SHIFT to 0x36
            '.' -> 0x00 to 0x37
            '>' -> MOD_LEFT_SHIFT to 0x37
            '/' -> 0x00 to 0x38
            '?' -> MOD_LEFT_SHIFT to 0x38
            else -> 0x00 to 0x00
        }
    }

    // =========================================================================
    // PERMISSIONS & UI HELPERS
    // =========================================================================

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, url, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS && hasBluetoothPermissions()) {
            refreshPairedDevices()
            initHidProfile()
        }
    }

    private fun createHoldMouseButton(label: String, buttonMask: Int): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#334155", dp(10))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        activeMouseButtons = activeMouseButtons or buttonMask
                        sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                        background = roundedBox("#0284C7", dp(10))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        activeMouseButtons = activeMouseButtons and buttonMask.inv()
                        sendMouseReport(activeMouseButtons, 0, 0, 0, 0)
                        background = roundedBox("#334155", dp(10))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createHoldGamepadButton(label: String, bitMask: Int, colorHex: String): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(colorHex, dp(9))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        gamepadButtonMask = gamepadButtonMask or bitMask
                        sendGamepadReport()
                        background = roundedBox("#38BDF8", dp(9))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        gamepadButtonMask = gamepadButtonMask and bitMask.inv()
                        sendGamepadReport()
                        background = roundedBox(colorHex, dp(9))
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
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#334155", dp(8))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        gamepadHatState = hatVal
                        sendGamepadReport()
                        background = roundedBox("#0284C7", dp(8))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        gamepadHatState = 8
                        sendGamepadReport()
                        background = roundedBox("#334155", dp(8))
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun createConsumerButton(label: String, usage16Bit: Int, colorHex: String = "#334155"): Button {
        return createStyledButton(label, colorHex) {
            hapticTick()
            sendConsumerKey(usage16Bit)
        }
    }

    private fun createStyledButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(colorHex, dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun weightParams(heightPx: Int = dp(42)): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, heightPx, 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
    }

    private fun roundedBox(fillHex: String, radiusPx: Int, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dp(1), Color.parseColor(strokeHex))
            }
        }
    }

    private fun hapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
            }
        } catch (_: Exception) {
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
