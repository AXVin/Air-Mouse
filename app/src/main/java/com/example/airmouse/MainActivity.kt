package com.example.airmouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
 * Air Mouse v3.0 — Created by AXVin
 *
 * Complete Bluetooth HID Multi-Tool Suite for Smart TVs & Media Devices:
 * 1. "Air Mouse & Keyboard" Tab:
 *    - 60Hz Gyroscope Air-Mouse (with Accelerometer fallback)
 *    - Auto Scroll-Lock Trackpad & Scroll Strip
 *    - Clean Live Keypress-by-Keypress Keyboard (no "Send Text" button or clutter)
 * 2. "Media and TV" Tab:
 *    - Consumer Control HID (Report ID 3) for native TV Volume, Channels, Transport & App keys
 *    - Interactive Circular Rotary Dial (Volume + Mute) & Jog Wheel (Timeline Seek/Scrub + Play/Pause)
 * 3. "GamePad" Tab:
 *    - Composite HID Gamepad (Report ID 4) with 16 buttons, 8-way Hat D-Pad, Dual Analog Joysticks,
 *      and Gyro Tilt Steering
 *    - 3 Switchable Controller Layouts: Xbox 360, PS5 DualSense, and Mobile Pro
 * 4. "System Power" Tab:
 *    - System Control HID (Report ID 5) for System Power Down, System Sleep, System Wake Up,
 *      TV Power Toggle, Screen Sleep, Close Active App, and Task Switcher
 * - Top Connection Bar with high-contrast cross-device Dropdown, Instant Multi-Device Switcher,
 *   Reset Connection button, and Unpair Current Device button
 * - Creator support links for AXVin on Patreon (https://www.patreon.com/axvin) and Ko-fi (https://ko-fi.com/axvin)
 */
@SuppressLint("MissingPermission", "ClickableViewAccessibility", "SetTextI18n")
class MainActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2
        private const val REPORT_ID_CONSUMER = 3
        private const val REPORT_ID_GAMEPAD = 4
        private const val REPORT_ID_SYSTEM = 5

        private const val SENTINEL_CHAR = "\u200B"
        private const val PATREON_URL = "https://www.patreon.com/axvin"
        private const val KOFI_URL = "https://ko-fi.com/axvin"

        // Composite USB/Bluetooth HID Report Descriptor:
        // ID 1 = Keyboard, ID 2 = Mouse, ID 3 = Consumer/Media, ID 4 = Gamepad, ID 5 = System Power
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // ================= 1. KEYBOARD (Report ID 1) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // Report ID (1)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224 - Left Ctrl)
            0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231 - Right GUI)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x08.toByte(), // Report Count (8)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute) - Modifier byte
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x81.toByte(), 0x01.toByte(), // Input (Constant) - Reserved byte
            0x95.toByte(), 0x06.toByte(), // Report Count (6)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x73.toByte(), // Logical Maximum (115)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
            0x29.toByte(), 0x73.toByte(), // Usage Maximum (115)
            0x81.toByte(), 0x00.toByte(), // Input (Data, Array) - 6 Key Rollover
            0xC0.toByte(),                // End Collection

            // ================= 2. MOUSE (Report ID 2) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_MOUSE.toByte(), // Report ID (2)
            0x09.toByte(), 0x01.toByte(), // Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), // Collection (Physical)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Buttons)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1 - Left)
            0x29.toByte(), 0x03.toByte(), // Usage Maximum (Button 3 - Middle)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute) - 3 buttons
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x05.toByte(), // Report Size (5)
            0x81.toByte(), 0x01.toByte(), // Input (Constant) - 5 bit padding
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y)
            0x09.toByte(), 0x38.toByte(), // Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x81.toByte(), 0x06.toByte(), // Input (Data, Variable, Relative) - X, Y, Wheel
            0xC0.toByte(),                // End Collection (Physical)
            0xC0.toByte(),                // End Collection (Application)

            // ================= 3. CONSUMER MEDIA & TV CONTROL (Report ID 3) =================
            0x05.toByte(), 0x0C.toByte(), // Usage Page (Consumer)
            0x09.toByte(), 0x01.toByte(), // Usage (Consumer Control)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_CONSUMER.toByte(), // Report ID (3)
            0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (0x03FF)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (0x03FF)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x10.toByte(), // Report Size (16)
            0x81.toByte(), 0x00.toByte(), // Input (Data, Array, Absolute)
            0xC0.toByte(),                // End Collection

            // ================= 4. GAMEPAD CONTROLLER (Report ID 4) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // Usage (Game Pad)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_GAMEPAD.toByte(), // Report ID (4)
            // 16 Buttons (2 bytes)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1)
            0x29.toByte(), 0x10.toByte(), // Usage Maximum (Button 16)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x10.toByte(), // Report Count (16)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
            // 8-Way Hat Switch D-Pad (1 byte: 0..7 active, 8 = released/null)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), // Usage (Hat switch)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x07.toByte(), // Logical Maximum (7)
            0x35.toByte(), 0x00.toByte(), // Physical Minimum (0)
            0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
            0x65.toByte(), 0x14.toByte(), // Unit (Eng Rot: Angular Pos)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x81.toByte(), 0x42.toByte(), // Input (Data, Variable, Absolute, Null State)
            // 4 Analog Thumbstick Axes: X, Y, Z, Rz (4 signed bytes: -127..127)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X - Left Stick X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y - Left Stick Y)
            0x09.toByte(), 0x32.toByte(), // Usage (Z - Right Stick X)
            0x09.toByte(), 0x35.toByte(), // Usage (Rz - Right Stick Y)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x95.toByte(), 0x04.toByte(), // Report Count (4)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
            0xC0.toByte(),                // End Collection

            // ================= 5. SYSTEM POWER CONTROL (Report ID 5) =================
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x80.toByte(), // Usage (System Control)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_SYSTEM.toByte(), // Report ID (5)
            0x19.toByte(), 0x81.toByte(), // Usage Minimum (0x81 - System Power Down)
            0x29.toByte(), 0x83.toByte(), // Usage Maximum (0x83 - System Wake Up)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x03.toByte(), // Report Count (3)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x05.toByte(), // Report Size (5)
            0x81.toByte(), 0x01.toByte(), // Input (Constant - 5 bit padding)
            0xC0.toByte()                 // End Collection
        )
    }

    /**
     * ScrollView with Scroll-Lock support so trackpad, rotary dials, and joysticks
     * never cause the screen to move up and down during touch interaction.
     */
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

    // Bluetooth HID state
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var pairedDevicesList = mutableListOf<BluetoothDevice>()
    private val hidExecutor = Executors.newSingleThreadExecutor()
    private var isUserSelectingDevice = false

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

    // Accelerometer baseline
    private var accelBaseX = 0.0f
    private var accelBaseY = 0.0f
    private var accelCalibrated = false

    // Mouse state
    private var activeMouseButtons = 0
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var touchMovedDistance = 0f
    private var lastScrollY = 0f

    // Gamepad state
    private var currentGamepadStyle = GamepadStyle.XBOX_360
    private var gamepadButtonMask = 0
    private var gamepadHatState = 8 // 8 = null/centered, 0=N, 2=E, 4=S, 6=W
    private var leftStickX = 0
    private var leftStickY = 0
    private var rightStickX = 0
    private var rightStickY = 0

    // Live keyboard state
    private var isResettingLiveInput = false

    // UI Elements
    private lateinit var rootScrollView: LockableScrollView
    private lateinit var statusBadge: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var quickSwitchRow: LinearLayout
    private lateinit var touchpadView: TextView
    private lateinit var liveKeyboardInput: EditText
    private lateinit var airTriggerBtn: Button

    // Tab Containers & Buttons
    private val tabButtons = mutableListOf<Button>()
    private val tabSections = mutableListOf<View>()
    private lateinit var gamepadContentContainer: LinearLayout

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter

        buildUserInterface()
        checkAndRequestPermissions()
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

    // =========================================================================
    // UI CONSTRUCTION (TOP CONNECTION BAR + 4 TABS + PATREON/KO-FI FOOTER)
    // =========================================================================

    private fun buildUserInterface() {
        rootScrollView = LockableScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(24))
        }

        // 1. Header Bar: Logo + "Air Mouse" + "by AXVin" + Connection Status
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val logoView = createLogoView().apply {
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                marginEnd = dp(12)
            }
        }
        headerRow.addView(logoView)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "Air Mouse"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(titleText)

        val creatorBadge = TextView(this).apply {
            text = "by AXVin"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#0F172A", dp(6), "#0284C7")
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        titleRow.addView(creatorBadge)
        titleCol.addView(titleRow)

        statusBadge = TextView(this).apply {
            text = "Status: Initializing Bluetooth HID..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(3), 0, 0)
        }
        titleCol.addView(statusBadge)
        headerRow.addView(titleCol)
        container.addView(headerRow)

        // 2. Bluetooth Device Bar (High-Contrast Dropdown + Instant Switch + Reset + Unpair + Pair New)
        val btCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Custom high-contrast ArrayAdapter so selected & dropdown items are always visible on ALL phones
        deviceAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf("Select Paired Device...")
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (super.getView(position, convertView, parent) as TextView)
                tv.setTextColor(Color.parseColor("#F8FAFC")) // Bright white text for selected item
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                tv.typeface = Typeface.DEFAULT_BOLD
                tv.setPadding(dp(8), dp(6), dp(8), dp(6))
                tv.isSingleLine = true
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (super.getDropDownView(position, convertView, parent) as TextView)
                tv.setTextColor(Color.parseColor("#F8FAFC")) // Bright white text in dropdown popup
                tv.setBackgroundColor(Color.parseColor("#0F172A")) // Dark navy background in popup
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                tv.setPadding(dp(14), dp(12), dp(14), dp(12))
                return tv
            }
        }

        deviceSpinner = Spinner(this).apply {
            adapter = deviceAdapter
            background = roundedBox("#0F172A", dp(8), "#475569")
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = dp(6)
            }
        }
        spinnerRow.addView(connectBtn)
        btCard.addView(spinnerRow)

        // Action Buttons Row: Reset Connection | Unpair Device | Pair New | Refresh
        val btActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        val resetBtn = createStyledButton("Reset Conn", "#D97706") {
            resetHidConnection()
        }.apply { layoutParams = weightParams(dp(36)) }

        val unpairBtn = createStyledButton("Unpair", "#DC2626") {
            unpairCurrentDevice()
        }.apply { layoutParams = weightParams(dp(36)) }

        val pairNewBtn = createStyledButton("Pair New", "#334155") {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                })
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }.apply { layoutParams = weightParams(dp(36)) }

        val refreshBtn = createStyledButton("Refresh", "#334155") {
            refreshPairedDevices()
            Toast.makeText(this, "Device list refreshed", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = weightParams(dp(36)) }

        btActionsRow.addView(resetBtn)
        btActionsRow.addView(unpairBtn)
        btActionsRow.addView(pairNewBtn)
        btActionsRow.addView(refreshBtn)
        btCard.addView(btActionsRow)

        // Instant One-Tap Device Switcher Bar
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
        btCard.addView(quickSwitchScroll)

        container.addView(btCard)

        // 3. Top Navigation Tabs ("Air Mouse & Keyboard", "Media and TV", "GamePad", "System Power")
        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBox("#1E293B", dp(10))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val tabTitles = listOf(
            "Air Mouse & Keyboard",
            "Media and TV",
            "GamePad",
            "System Power"
        )

        tabButtons.clear()
        tabTitles.forEachIndexed { index, title ->
            val btn = Button(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(6), dp(14), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(38)
                ).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                setOnClickListener {
                    hapticTick()
                    selectTab(index)
                }
            }
            tabButtons.add(btn)
            tabsRow.addView(btn)
        }
        tabsScroll.addView(tabsRow)
        container.addView(tabsScroll)

        // 4. Build All 4 Tab Content Views
        tabSections.clear()
        val tab1 = buildAirMouseAndKeyboardTab()
        val tab2 = buildMediaAndTvTab()
        val tab3 = buildGamepadTab()
        val tab4 = buildSystemPowerTab()

        tabSections.addAll(listOf(tab1, tab2, tab3, tab4))
        tabSections.forEach { container.addView(it) }

        selectTab(0)

        // 5. Creator Support Card (Patreon & Ko-fi links for AXVin)
        val supportCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        val supportTitle = TextView(this).apply {
            text = "Created by AXVin • Support Development"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        supportCard.addView(supportTitle)

        val supportBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val patreonBtn = createStyledButton("Support me on Patreon", "#F96854") {
            openExternalUrl(PATREON_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
        }

        val kofiBtn = createStyledButton("Buy me a Ko-fi ☕", "#29ABE0") {
            openExternalUrl(KOFI_URL)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) }
        }

        supportBtnsRow.addView(patreonBtn)
        supportBtnsRow.addView(kofiBtn)
        supportCard.addView(supportBtnsRow)

        container.addView(supportCard)
        rootScrollView.addView(container)
        setContentView(rootScrollView)
    }

    private fun selectTab(selectedIndex: Int) {
        tabButtons.forEachIndexed { idx, btn ->
            if (idx == selectedIndex) {
                btn.background = roundedBox("#0284C7", dp(8))
                btn.setTextColor(Color.WHITE)
            } else {
                btn.background = roundedBox("#1E293B", dp(8))
                btn.setTextColor(Color.parseColor("#94A3B8"))
            }
        }
        tabSections.forEachIndexed { idx, section ->
            section.visibility = if (idx == selectedIndex) View.VISIBLE else View.GONE
        }
    }

    // =========================================================================
    // TAB 1: AIR MOUSE & KEYBOARD
    // =========================================================================

    private fun buildAirMouseAndKeyboardTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        // Gyro Controls Card
        val gyroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        airTriggerBtn = Button(this).apply {
            text = "HOLD TO MOVE AIR POINTER (GYRO)"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#2563EB", dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
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
            ).apply { topMargin = dp(8) }
        }

        val gyroLockSwitch = Switch(this).apply {
            text = "Lock Air-Mouse On"
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
            setPadding(dp(14), 0, dp(4), 0)
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

        // Trackpad + Scroll Strip (Auto Scroll-Lock Mode)
        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(195)
            ).apply { topMargin = dp(10) }
        }

        touchpadView = TextView(this).apply {
            text = "TRACKPAD (AUTO SCROLL-LOCK)\nSlide to move pointer • Tap for Left Click"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundedBox("#1E293B", dp(12), "#334155")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { v, event -> handleTouchpadEvent(v, event) }
        }
        padRow.addView(touchpadView)

        val scrollStrip = TextView(this).apply {
            text = "S\nC\nR\nO\nL\nL"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedBox("#1E293B", dp(12), "#334155")
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(8)
            }
            setOnTouchListener { v, event -> handleScrollStripEvent(v, event) }
        }
        padRow.addView(scrollStrip)
        tabRoot.addView(padRow)

        // Mouse Click Buttons Row
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

        // Clean Live Keyboard Row (Removed "Sends every keypress instantly" and "Last key" labels)
        val kbCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        liveKeyboardInput = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val base = super.onCreateInputConnection(outAttrs) ?: return null
                return object : InputConnectionWrapper(base, true) {
                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0 && afterLength == 0) {
                            sendKeyboardKey(0, 0x2A) // Backspace
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
                        sendKeyboardKey(0, 0x2A) // Backspace
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
                    sendKeyboardKey(0, 0x28) // Enter
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = dp(8)
            }
        }
        kbCard.addView(openKbBtn)
        tabRoot.addView(kbCard)

        // Navigation D-Pad Card
        val navCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val navRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navRow1.addView(createKeyButton("ESC / BACK", 0x29), weightParams())
        navRow1.addView(createKeyButton("UP", 0x52), weightParams())
        navRow1.addView(createKeyButton("BACKSPACE", 0x2A), weightParams())
        navCard.addView(navRow1)

        val navRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        navRow2.addView(createKeyButton("LEFT", 0x50), weightParams())
        navRow2.addView(createKeyButton("OK / ENTER", 0x28, "#0284C7"), weightParams())
        navRow2.addView(createKeyButton("RIGHT", 0x4F), weightParams())
        navCard.addView(navRow2)

        val navRow3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        navRow3.addView(createKeyButton("SPACE", 0x2C), weightParams())
        navRow3.addView(createKeyButton("DOWN", 0x51), weightParams())
        navRow3.addView(createKeyButton("TAB", 0x2B), weightParams())
        navCard.addView(navRow3)
        tabRoot.addView(navCard)

        return tabRoot
    }

    // =========================================================================
    // TAB 2: MEDIA AND TV + DUAL CIRCULAR ROTARY DIAL & JOG WHEEL
    // =========================================================================

    private fun buildMediaAndTvTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        // 1. Dual Circular Dials Card: Volume Rotary Dial + Timeline Seek Jog Wheel
        val dialsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val dialsHeader = TextView(this).apply {
            text = "ROTARY DIAL & JOG WHEEL (AUTO SCROLL-LOCK)"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        dialsCard.addView(dialsHeader)

        val dialsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(175)
            )
        }

        // Left Circular Dial: Volume Rotary Dial
        val volumeDial = createCircularDialView(
            title = "VOLUME DIAL",
            centerLabel = "MUTE",
            accentColor = "#38BDF8",
            onStepClockwise = { sendConsumerKey(0x00E9) },      // Volume Up
            onStepCounterClockwise = { sendConsumerKey(0x00EA) }, // Volume Down
            onCenterTap = { sendConsumerKey(0x00E2) }             // Mute
        )

        // Right Circular Dial: Seek / Timeline Scrub Jog Wheel
        val seekJogWheel = createCircularDialView(
            title = "SEEK JOG WHEEL",
            centerLabel = "PLAY/PAUSE",
            accentColor = "#A855F7",
            onStepClockwise = { sendConsumerKey(0x00B3) },        // Fast Forward / Seek +
            onStepCounterClockwise = { sendConsumerKey(0x00B4) }, // Rewind / Seek -
            onCenterTap = { sendConsumerKey(0x00CD) }             // Play / Pause
        )

        dialsRow.addView(volumeDial, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(6)
        })
        dialsRow.addView(seekJogWheel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(6)
        })
        dialsCard.addView(dialsRow)
        tabRoot.addView(dialsCard)

        // 2. TV Playback & Transport Controls Card
        val mediaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createConsumerButton("VOL -", 0x00EA), weightParams())
        row1.addView(createConsumerButton("MUTE", 0x00E2, "#DC2626"), weightParams())
        row1.addView(createConsumerButton("VOL +", 0x00E9), weightParams())
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
        row3.addView(createConsumerButton("⏪ REWIND", 0x00B4), weightParams())
        row3.addView(createConsumerButton("⏹ STOP", 0x00B7), weightParams())
        row3.addView(createConsumerButton("FAST FWD ⏩", 0x00B3), weightParams())
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

    /**
     * Interactive Circular Rotary Dial & Jog Wheel View.
     * Supports circular finger rotation (24 detents per revolution) + center button tap + scroll lock.
     */
    private fun createCircularDialView(
        title: String,
        centerLabel: String,
        accentColor: String,
        onStepClockwise: () -> Unit,
        onStepCounterClockwise: () -> Unit,
        onCenterTap: () -> Unit
    ): View {
        return object : View(this) {
            private var currentAngleRad = -Math.PI / 2
            private var lastAngleRad = 0.0
            private var accumulatedAngle = 0.0
            private var isDraggingWheel = false
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
            private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(9).toFloat()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = (height / 2f) + dp(8)
                val outerR = min(width, height - dp(20)) * 0.43f
                val innerR = outerR * 0.48f

                // Title above dial
                canvas.drawText(title, cx, dp(15).toFloat(), titlePaint)

                // Outer wheel body
                canvas.drawCircle(cx, cy, outerR, ringPaint)
                canvas.drawCircle(cx, cy, outerR, borderPaint)

                // 24 rotary encoder tick marks
                for (i in 0 until 24) {
                    val a = (i * Math.PI * 2.0 / 24.0)
                    val x1 = cx + (outerR * 0.80f * cos(a)).toFloat()
                    val y1 = cy + (outerR * 0.80f * sin(a)).toFloat()
                    val x2 = cx + (outerR * 0.92f * cos(a)).toFloat()
                    val y2 = cy + (outerR * 0.92f * sin(a)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, tickPaint)
                }

                // Current rotation indicator dot on the wheel rim
                val knobDist = outerR * 0.68f
                val kx = cx + (knobDist * cos(currentAngleRad)).toFloat()
                val ky = cy + (knobDist * sin(currentAngleRad)).toFloat()
                canvas.drawCircle(kx, ky, dp(7).toFloat(), knobPaint)

                // Center button
                canvas.drawCircle(cx, cy, innerR, centerPaint)
                canvas.drawCircle(cx, cy, innerR, borderPaint)
                canvas.drawText(centerLabel, cx, cy + dp(3), centerTextPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val cx = width / 2f
                val cy = (height / 2f) + dp(8)
                val outerR = min(width, height - dp(20)) * 0.43f
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
                        currentAngleRad = lastAngleRad
                        accumulatedAngle = 0.0
                        isDraggingWheel = dist > innerR * 0.85f
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        rootScrollView.isScrollLocked = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        if (dist > innerR * 0.65f) {
                            isDraggingWheel = true
                            val angle = atan2(dy.toDouble(), dx.toDouble())
                            var delta = angle - lastAngleRad
                            if (delta > Math.PI) delta -= 2.0 * Math.PI
                            if (delta < -Math.PI) delta += 2.0 * Math.PI

                            accumulatedAngle += delta
                            lastAngleRad = angle
                            currentAngleRad = angle

                            val stepThreshold = Math.PI / 10.0 // ~18 degrees per step
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
                        isDraggingWheel = false
                        invalidate()
                        return true
                    }
                }
                return false
            }
        }
    }

    // =========================================================================
    // TAB 3: GAMEPAD CONTROLLER (XBOX 360 / PS5 DUALSENSE / MOBILE PRO)
    // =========================================================================

    private fun buildGamepadTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        // Layout Mode Selector Row (Xbox 360 | PS5 DualSense | Mobile Pro) + Gyro Tilt Steering Toggle
        val modeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val styleBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val xboxBtn = createStyledButton("Xbox 360", "#107C10") {
            currentGamepadStyle = GamepadStyle.XBOX_360
            rebuildGamepadLayout()
        }.apply { layoutParams = weightParams(dp(38)) }

        val ps5Btn = createStyledButton("PS5 DualSense", "#00439C") {
            currentGamepadStyle = GamepadStyle.PS5_DUALSENSE
            rebuildGamepadLayout()
        }.apply { layoutParams = weightParams(dp(38)) }

        val mobileProBtn = createStyledButton("Mobile Pro", "#7C3AED") {
            currentGamepadStyle = GamepadStyle.MOBILE_PRO
            rebuildGamepadLayout()
        }.apply { layoutParams = weightParams(dp(38)) }

        styleBtnsRow.addView(xboxBtn)
        styleBtnsRow.addView(ps5Btn)
        styleBtnsRow.addView(mobileProBtn)
        modeCard.addView(styleBtnsRow)

        val gyroSteerSwitch = Switch(this).apply {
            text = "Gyro Tilt Steering (Tilt phone to steer Left Stick)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(dp(4), dp(8), dp(4), 0)
            setOnCheckedChangeListener { _, isChecked ->
                isGamepadGyroSteering = isChecked
                accelCalibrated = false
                if (!isChecked) {
                    leftStickX = 0
                    leftStickY = 0
                    sendGamepadReport()
                }
            }
        }
        modeCard.addView(gyroSteerSwitch)
        tabRoot.addView(modeCard)

        gamepadContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
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
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }

        val headerText = TextView(this).apply {
            text = when (currentGamepadStyle) {
                GamepadStyle.XBOX_360 -> "XBOX 360 CONTROLLER LAYOUT"
                GamepadStyle.PS5_DUALSENSE -> "PS5 DUALSENSE CONTROLLER LAYOUT"
                GamepadStyle.MOBILE_PRO -> "MOBILE PRO ERGONOMIC TOUCH CONTROLLER"
            }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        padCard.addView(headerText)

        // 1. Shoulder & Trigger Row (L2/LT, L1/LB, R1/RB, R2/RT)
        val triggersRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (l2Name, l1Name, r1Name, r2Name) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("LT", "LB", "RB", "RT")
            GamepadStyle.PS5_DUALSENSE -> listOf("L2", "L1", "R1", "R2")
            GamepadStyle.MOBILE_PRO -> listOf("TRIG L2", "BUMP L1", "BUMP R1", "TRIG R2")
        }
        triggersRow.addView(createHoldGamepadButton(l2Name, 1 shl 6, "#334155"), weightParams(dp(44)))
        triggersRow.addView(createHoldGamepadButton(l1Name, 1 shl 4, "#475569"), weightParams(dp(44)))
        triggersRow.addView(createHoldGamepadButton(r1Name, 1 shl 5, "#475569"), weightParams(dp(44)))
        triggersRow.addView(createHoldGamepadButton(r2Name, 1 shl 7, "#334155"), weightParams(dp(44)))
        padCard.addView(triggersRow)

        // 2. Center System Buttons Row (Back/Create, Guide/PS, Start/Options)
        val centerSysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        val (selectLabel, guideLabel, startLabel) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> Triple("BACK", "XBOX GUIDE", "START")
            GamepadStyle.PS5_DUALSENSE -> Triple("CREATE", "PS HOME", "OPTIONS")
            GamepadStyle.MOBILE_PRO -> Triple("SELECT", "HOME", "START")
        }
        centerSysRow.addView(createHoldGamepadButton(selectLabel, 1 shl 8, "#334155"), weightParams(dp(38)))
        centerSysRow.addView(createHoldGamepadButton(guideLabel, 1 shl 12, themeBorder), weightParams(dp(38)))
        centerSysRow.addView(createHoldGamepadButton(startLabel, 1 shl 9, "#334155"), weightParams(dp(38)))
        padCard.addView(centerSysRow)

        // 3. Upper Controls Row:
        // Xbox 360: Left Stick on Top-Left, ABXY on Top-Right
        // PS5 DualSense: D-Pad on Top-Left, △○✕□ on Top-Right
        // Mobile Pro: Enlarged D-Pad + Enlarged Action Cluster
        val upperClusterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(165)
            ).apply { topMargin = dp(10) }
        }

        if (currentGamepadStyle == GamepadStyle.XBOX_360) {
            val leftStickView = createVirtualJoystickView("LEFT STICK (L3)", "#107C10") { x, y ->
                leftStickX = x
                leftStickY = y
                sendGamepadReport()
            }
            upperClusterRow.addView(leftStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            upperClusterRow.addView(buildFaceButtonsCluster(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        } else {
            upperClusterRow.addView(buildDpadCluster(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            upperClusterRow.addView(buildFaceButtonsCluster(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        padCard.addView(upperClusterRow)

        // 4. Lower Controls Row:
        // Xbox 360: D-Pad on Bottom-Left, Right Stick on Bottom-Right
        // PS5 / Mobile Pro: Left Stick on Bottom-Left, Right Stick on Bottom-Right
        val lowerClusterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(160)
            ).apply { topMargin = dp(10) }
        }

        if (currentGamepadStyle == GamepadStyle.XBOX_360) {
            lowerClusterRow.addView(buildDpadCluster(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        } else {
            val leftStickView = createVirtualJoystickView("LEFT STICK (L3)", themeBorder) { x, y ->
                leftStickX = x
                leftStickY = y
                sendGamepadReport()
            }
            lowerClusterRow.addView(leftStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }

        val rightStickView = createVirtualJoystickView("RIGHT STICK (R3)", themeBorder) { x, y ->
            rightStickX = x
            rightStickY = y
            sendGamepadReport()
        }
        lowerClusterRow.addView(rightStickView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        padCard.addView(lowerClusterRow)

        // 5. Stick Click Row (L3 & R3 buttons)
        val stickClicksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        stickClicksRow.addView(createHoldGamepadButton("L3 (LEFT STICK CLICK)", 1 shl 10, "#334155"), weightParams(dp(38)))
        stickClicksRow.addView(createHoldGamepadButton("R3 (RIGHT STICK CLICK)", 1 shl 11, "#334155"), weightParams(dp(38)))
        padCard.addView(stickClicksRow)

        gamepadContentContainer.addView(padCard)
    }

    private fun buildDpadCluster(): View {
        val dpadGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowTop.addView(createHatButton("▲", 0), LinearLayout.LayoutParams(dp(52), dp(46)))

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
        rowMid.addView(createHatButton("◀", 6), LinearLayout.LayoutParams(dp(52), dp(46)).apply { marginEnd = dp(24) })
        rowMid.addView(createHatButton("▶", 2), LinearLayout.LayoutParams(dp(52), dp(46)))

        val rowBot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowBot.addView(createHatButton("▼", 4), LinearLayout.LayoutParams(dp(52), dp(46)))

        dpadGrid.addView(rowTop)
        dpadGrid.addView(rowMid)
        dpadGrid.addView(rowBot)
        return dpadGrid
    }

    private fun buildFaceButtonsCluster(): View {
        val cluster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val (topLbl, leftLbl, rightLbl, botLbl) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("Y", "X", "B", "A")
            GamepadStyle.PS5_DUALSENSE -> listOf("△", "□", "○", "✕")
            GamepadStyle.MOBILE_PRO -> listOf("Y / △", "X / □", "B / ○", "A / ✕")
        }

        val (topColor, leftColor, rightColor, botColor) = when (currentGamepadStyle) {
            GamepadStyle.XBOX_360 -> listOf("#CA8A04", "#2563EB", "#DC2626", "#16A34A")
            GamepadStyle.PS5_DUALSENSE -> listOf("#0D9488", "#DB2777", "#E11D48", "#2563EB")
            GamepadStyle.MOBILE_PRO -> listOf("#7C3AED", "#2563EB", "#DC2626", "#16A34A")
        }

        val btnW = if (currentGamepadStyle == GamepadStyle.MOBILE_PRO) dp(64) else dp(54)
        val btnH = dp(46)

        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowTop.addView(createHoldGamepadButton(topLbl, 1 shl 3, topColor), LinearLayout.LayoutParams(btnW, btnH))

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
        rowMid.addView(createHoldGamepadButton(leftLbl, 1 shl 2, leftColor), LinearLayout.LayoutParams(btnW, btnH).apply { marginEnd = dp(18) })
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
                textSize = dp(10).toFloat()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = (height / 2f) + dp(6)
                val maxR = min(width, height - dp(16)) * 0.40f
                val thumbR = maxR * 0.42f

                canvas.drawText(label, cx, dp(13).toFloat(), labelPaint)
                canvas.drawCircle(cx, cy, maxR, basePaint)
                canvas.drawCircle(cx, cy, maxR, ringPaint)

                val drawX = if (isTouching) cx + knobX else cx
                val drawY = if (isTouching) cy + knobY else cy
                canvas.drawCircle(drawX, drawY, thumbR, stickPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val cx = width / 2f
                val cy = (height / 2f) + dp(6)
                val maxR = min(width, height - dp(16)) * 0.40f

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
    // TAB 4: SYSTEM POWER CONTROLS
    // =========================================================================

    private fun buildSystemPowerTab(): View {
        val tabRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val title = TextView(this).apply {
            text = "SYSTEM POWER & HOST CONTROLS"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        }
        tabRoot.addView(title)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createStyledButton("TV POWER TOGGLE", "#DC2626") {
            hapticTick()
            sendConsumerKey(0x0030) // Consumer Power
        }.apply { layoutParams = weightParams(dp(50)) })

        row1.addView(createStyledButton("SYSTEM SLEEP", "#D97706") {
            hapticTick()
            sendSystemControlKey(0x02) // System Sleep (0x82)
        }.apply { layoutParams = weightParams(dp(50)) })
        tabRoot.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row2.addView(createStyledButton("SYSTEM WAKE UP", "#16A34A") {
            hapticTick()
            sendSystemControlKey(0x04) // System Wake Up (0x83)
        }.apply { layoutParams = weightParams(dp(50)) })

        row2.addView(createStyledButton("SYSTEM POWER OFF", "#991B1B") {
            hapticTick()
            sendSystemControlKey(0x01) // System Power Down (0x81)
        }.apply { layoutParams = weightParams(dp(50)) })
        tabRoot.addView(row2)

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row3.addView(createStyledButton("MEDIA SLEEP", "#475569") {
            hapticTick()
            sendConsumerKey(0x0032) // Consumer Sleep
        }.apply { layoutParams = weightParams(dp(46)) })

        row3.addView(createStyledButton("LOCK SCREEN", "#475569") {
            hapticTick()
            sendKeyboardKey(0x08, 0x0F) // GUI (Win/Cmd) + L
        }.apply { layoutParams = weightParams(dp(46)) })
        tabRoot.addView(row3)

        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        row4.addView(createStyledButton("CLOSE APP (ALT+F4)", "#475569") {
            hapticTick()
            sendKeyboardKey(0x04, 0x3D) // Left Alt + F4
        }.apply { layoutParams = weightParams(dp(46)) })

        row4.addView(createStyledButton("SWITCH APP (ALT+TAB)", "#475569") {
            hapticTick()
            sendKeyboardKey(0x04, 0x2B) // Left Alt + Tab
        }.apply { layoutParams = weightParams(dp(46)) })
        tabRoot.addView(row4)

        return tabRoot
    }

    // =========================================================================
    // LOGO VIEW ("Air Mouse" Custom Logo)
    // =========================================================================

    private fun createLogoView(): View {
        val resId = resources.getIdentifier("air_mouse_logo", "drawable", packageName)
        if (resId != 0) {
            return ImageView(this).apply {
                setImageResource(resId)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        return object : View(this) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#38BDF8")
                style = Paint.Style.FILL
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = dp(7).toFloat()
            }
            private val rect = RectF()
            private val cursorPath = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                rect.set(dp(2).toFloat(), dp(2).toFloat(), w - dp(2), h - dp(2))
                canvas.drawRoundRect(rect, dp(10).toFloat(), dp(10).toFloat(), bgPaint)
                canvas.drawRoundRect(rect, dp(10).toFloat(), dp(10).toFloat(), borderPaint)

                cursorPath.reset()
                cursorPath.moveTo(w * 0.50f, h * 0.16f)
                cursorPath.lineTo(w * 0.72f, h * 0.56f)
                cursorPath.lineTo(w * 0.54f, h * 0.49f)
                cursorPath.lineTo(w * 0.48f, h * 0.64f)
                cursorPath.lineTo(w * 0.30f, h * 0.54f)
                cursorPath.close()
                canvas.drawPath(cursorPath, pointerPaint)

                canvas.drawText("AIR MOUSE", w * 0.5f, h * 0.86f, textPaint)
            }
        }
    }

    // =========================================================================
    // GYROSCOPE & ACCELEROMETER MOTION PROCESSING (AIR MOUSE + GAMEPAD STEERING)
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

        // 1. Gamepad Gyro Tilt Steering Mode
        if (isGamepadGyroSteering) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val steerRate = -event.values[2]
                leftStickX = (leftStickX + (steerRate * 14f)).roundToInt().coerceIn(-127, 127)
                sendGamepadReport()
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val tiltX = -event.values[0]
                leftStickX = (tiltX * 18f).roundToInt().coerceIn(-127, 127)
                sendGamepadReport()
            }
        }

        // 2. Air-Mouse Pointer Mode
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
                sendMouseReport(activeMouseButtons, intDx, intDy, 0)
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
                sendMouseReport(activeMouseButtons, dx, dy, 0)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =========================================================================
    // TRACKPAD & SCROLL STRIP HANDLERS (AUTO SCROLL-LOCK)
    // =========================================================================

    private fun setScrollLockActive(locked: Boolean) {
        rootScrollView.isScrollLocked = locked
        if (locked) {
            touchpadView.background = roundedBox("#1E293B", dp(12), "#38BDF8")
            touchpadView.text = "TRACKPAD ACTIVE (SCROLL LOCKED)\nMoving TV Cursor..."
            touchpadView.setTextColor(Color.parseColor("#38BDF8"))
        } else {
            touchpadView.background = roundedBox("#1E293B", dp(12), "#334155")
            touchpadView.text = "TRACKPAD (AUTO SCROLL-LOCK)\nSlide to move pointer • Tap for Left Click"
            touchpadView.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun handleTouchpadEvent(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                setScrollLockActive(true)
                view.parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                touchDownTime = System.currentTimeMillis()
                touchMovedDistance = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                rootScrollView.isScrollLocked = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                val dxFloat = (event.x - lastTouchX) * 1.4f
                val dyFloat = (event.y - lastTouchY) * 1.4f
                lastTouchX = event.x
                lastTouchY = event.y

                touchMovedDistance += abs(dxFloat) + abs(dyFloat)
                val dx = dxFloat.roundToInt().coerceIn(-127, 127)
                val dy = dyFloat.roundToInt().coerceIn(-127, 127)
                if (dx != 0 || dy != 0) {
                    sendMouseReport(activeMouseButtons, dx, dy, 0)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setScrollLockActive(false)
                view.parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    if (elapsed < 220 && touchMovedDistance < dp(10)) {
                        hapticTick()
                        sendMouseReport(1, 0, 0, 0)
                        sendMouseReport(0, 0, 0, 0)
                    }
                }
                return true
            }
        }
        return false
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
                    sendMouseReport(activeMouseButtons, 0, 0, wheelStep)
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
    // BLUETOOTH HID PROFILE, MULTI-DEVICE INSTANT SWITCHING, RESET & UNPAIR
    // =========================================================================

    private fun initHidProfile() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            statusBadge.text = "Status: Please enable Bluetooth"
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
                    "Air Mouse, Media, Gamepad & Keyboard by AXVin",
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
                                statusBadge.text = "Status: HID Ready — Select or Tap a Device"
                                statusBadge.setTextColor(Color.parseColor("#38BDF8"))
                                if (pluggedDevice != null) {
                                    switchToDeviceInstantly(pluggedDevice)
                                }
                            } else {
                                statusBadge.text = "Status: HID Service Stopped"
                            }
                        }
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        runOnUiThread {
                            when (state) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connectedDevice = device
                                    statusBadge.text = "Connected: ${device?.name ?: "Device"}"
                                    statusBadge.setTextColor(Color.parseColor("#4ADE80"))
                                    hapticTick()
                                    rebuildQuickSwitchChips()
                                }
                                BluetoothProfile.STATE_CONNECTING -> {
                                    statusBadge.text = "Connecting to ${device?.name ?: "Device"}..."
                                    statusBadge.setTextColor(Color.parseColor("#FACC15"))
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    if (connectedDevice == device) connectedDevice = null
                                    statusBadge.text = "Disconnected — Select Device or Tap Reset"
                                    statusBadge.setTextColor(Color.parseColor("#F87171"))
                                    rebuildQuickSwitchChips()
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

    /**
     * Builds one-tap instant switch buttons for every paired Bluetooth device
     * so the user can jump between multiple TVs/devices immediately.
     */
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBox(chipColor, dp(16), chipStroke)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32)
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

    /**
     * Instantly disconnects from any previously connected HID host and connects
     * to the newly chosen target Bluetooth device.
     */
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

    /**
     * Resets the Bluetooth HID profile proxy and reconnects to the selected device.
     */
    private fun resetHidConnection() {
        hapticTick()
        statusBadge.text = "Resetting Bluetooth HID connection..."
        statusBadge.setTextColor(Color.parseColor("#FACC15"))
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

    /**
     * Unpairs (removes bond) and disconnects the currently selected or connected device.
     */
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
            statusBadge.text = "Unpaired ${target.name ?: "Device"}"
            statusBadge.setTextColor(Color.parseColor("#F87171"))
            window.decorView.postDelayed({ refreshPairedDevices() }, 600)
        } else {
            Toast.makeText(this, "Opening Bluetooth settings to finish unpairing ${target.name ?: ""}", Toast.LENGTH_SHORT).show()
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    // =========================================================================
    // HID REPORT SENDERS (KEYBOARD, MOUSE, CONSUMER MEDIA, GAMEPAD, SYSTEM)
    // =========================================================================

    private fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
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
        val downReport = byteArrayOf(low, high)
        val upReport = byteArrayOf(0x00, 0x00)
        hid.sendReport(target, REPORT_ID_CONSUMER, downReport)
        hid.sendReport(target, REPORT_ID_CONSUMER, upReport)
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

    // =========================================================================
    // REAL-TIME KEYPRESS SENDER
    // =========================================================================

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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

    private fun charToHidUsage(c: Char): Pair<Int, Int> {
        return when (c) {
            in 'a'..'z' -> 0x00 to (0x04 + (c - 'a'))
            in 'A'..'Z' -> 0x02 to (0x04 + (c - 'A'))
            in '1'..'9' -> 0x00 to (0x1E + (c - '1'))
            '0' -> 0x00 to 0x27
            ' ' -> 0x00 to 0x2C
            '\n' -> 0x00 to 0x28
            '!' -> 0x02 to 0x1E
            '@' -> 0x02 to 0x1F
            '#' -> 0x02 to 0x20
            '$' -> 0x02 to 0x21
            '%' -> 0x02 to 0x22
            '^' -> 0x02 to 0x23
            '&' -> 0x02 to 0x24
            '*' -> 0x02 to 0x25
            '(' -> 0x02 to 0x26
            ')' -> 0x02 to 0x27
            '-' -> 0x00 to 0x2D
            '_' -> 0x02 to 0x2D
            '=' -> 0x00 to 0x2E
            '+' -> 0x02 to 0x2E
            '[' -> 0x00 to 0x2F
            '{' -> 0x02 to 0x2F
            ']' -> 0x00 to 0x30
            '}' -> 0x02 to 0x30
            '\\' -> 0x00 to 0x31
            '|' -> 0x02 to 0x31
            ';' -> 0x00 to 0x33
            ':' -> 0x02 to 0x33
            '\'' -> 0x00 to 0x34
            '"' -> 0x02 to 0x34
            '`' -> 0x00 to 0x35
            '~' -> 0x02 to 0x35
            ',' -> 0x00 to 0x36
            '<' -> 0x02 to 0x36
            '.' -> 0x00 to 0x37
            '>' -> 0x02 to 0x37
            '/' -> 0x00 to 0x38
            '?' -> 0x02 to 0x38
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
                        sendMouseReport(activeMouseButtons, 0, 0, 0)
                        background = roundedBox("#0284C7", dp(10))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        activeMouseButtons = activeMouseButtons and buttonMask.inv()
                        sendMouseReport(activeMouseButtons, 0, 0, 0)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(colorHex, dp(10))
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rootScrollView.isScrollLocked = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        gamepadButtonMask = gamepadButtonMask or bitMask
                        sendGamepadReport()
                        background = roundedBox("#38BDF8", dp(10))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rootScrollView.isScrollLocked = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        gamepadButtonMask = gamepadButtonMask and bitMask.inv()
                        sendGamepadReport()
                        background = roundedBox(colorHex, dp(10))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
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
                        gamepadHatState = 8 // Null / Centered
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

    private fun createKeyButton(label: String, hidKeyCode: Int, colorHex: String = "#334155"): Button {
        return createStyledButton(label, colorHex) {
            hapticTick()
            sendKeyboardKey(0, hidKeyCode)
        }
    }

    private fun createStyledButton(label: String, colorHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(colorHex, dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun weightParams(heightPx: Int = dp(44)): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, heightPx, 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
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
                vibrator?.vibrate(VibrationEffect.createOneShot(16, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(16)
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
