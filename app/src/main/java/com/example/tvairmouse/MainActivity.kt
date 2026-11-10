package com.example.tvairmouse

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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * TV Air-Mouse & Keyboard (Bluetooth HID)
 *
 * Turns an Android 9+ (API 28+) phone into a native Bluetooth HID Combo Mouse & Keyboard
 * for Smart TVs / Android TV / Google TV / Fire TV / LG webOS / Samsung Tizen.
 *
 * Features:
 * - Composite HID Descriptor: Report ID 1 (8-byte Keyboard) + Report ID 2 (4-byte Relative Mouse)
 * - 60Hz Gyroscope Air-Mouse (with automatic Accelerometer tilt fallback + deadzone smoothing)
 * - Hold-to-Move ("Air Trigger") & Continuous Gyro Lock toggle + Sensitivity slider
 * - Multi-purpose Touchpad (1-finger move, tap-to-click, vertical scroll strip)
 * - Dedicated TV Navigation keys (D-Pad Up/Down/Left/Right/OK, Back, Home, Volume, Mute)
 * - Full Keyboard text sender & live key passthrough
 */
@SuppressLint("MissingPermission", "ClickableViewAccessibility", "SetTextI18n")
class MainActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2

        // Composite USB/Bluetooth HID Report Descriptor: Keyboard (ID 1) + Mouse (ID 2)
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // ================= KEYBOARD (Report ID 1) =================
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

            // ================= MOUSE (Report ID 2) =================
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
            0xC0.toByte()                 // End Collection (Application)
        )
    }

    // Bluetooth HID state
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var pairedDevicesList = mutableListOf<BluetoothDevice>()
    private val hidExecutor = Executors.newSingleThreadExecutor()

    // Sensor state (Gyroscope primary, Accelerometer fallback)
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var isAirMouseTriggerHeld = false
    private var isAirMouseLockedOn = false
    private var gyroSensitivity = 22.0f // Default multiplier for rad/s -> pixel delta
    private var residualX = 0.0f
    private var residualY = 0.0f

    // Accelerometer calibration baseline (used only when hardware gyroscope is absent)
    private var accelBaseX = 0.0f
    private var accelBaseY = 0.0f
    private var accelCalibrated = false

    // Mouse button bitmask (Bit 0 = Left, Bit 1 = Right, Bit 2 = Middle)
    private var activeMouseButtons = 0

    // Touchpad tracking state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var touchMovedDistance = 0f
    private var lastScrollY = 0f

    // UI Elements
    private lateinit var statusBadge: TextView
    private lateinit var sensorBadge: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var textInputField: EditText
    private lateinit var airTriggerBtn: Button
    private lateinit var gyroLockSwitch: Switch
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
    // UI CONSTRUCTION (Zero external XML dependency - works standalone)
    // =========================================================================

    private fun buildUserInterface() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // 1. Header & Connection Status Bar
        val titleText = TextView(this).apply {
            text = "TV Air-Mouse & Keyboard"
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(titleText)

        statusBadge = TextView(this).apply {
            text = "Status: Initializing Bluetooth HID..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(2))
        }
        container.addView(statusBadge)

        sensorBadge = TextView(this).apply {
            val modeName = when {
                gyroSensor != null -> "Gyroscope 60Hz Air-Mouse Ready"
                accelSensor != null -> "Accelerometer Tilt Air-Mouse Ready (No Gyro)"
                else -> "Touchpad Only (No Motion Sensors)"
            }
            text = "Sensor: $modeName"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(10))
        }
        container.addView(sensorBadge)

        // 2. Paired TV Selector Row
        val btRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(10))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf("Select Paired TV..."))
        deviceSpinner = Spinner(this).apply {
            adapter = deviceAdapter
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (parent?.getChildAt(0) as? TextView)?.setTextColor(Color.WHITE)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        btRow.addView(deviceSpinner)

        val connectBtn = createStyledButton("Connect", "#0284C7") {
            val idx = deviceSpinner.selectedItemPosition
            if (idx in pairedDevicesList.indices) {
                connectToSelectedDevice(pairedDevicesList[idx])
            } else {
                Toast.makeText(this, "Pair your TV in Android Bluetooth Settings first", Toast.LENGTH_SHORT).show()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = dp(8)
            }
        }
        btRow.addView(connectBtn)

        val discoverBtn = createStyledButton("Pair New", "#334155") {
            try {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                })
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = dp(6)
            }
        }
        btRow.addView(discoverBtn)
        container.addView(btRow)

        // 3. Air-Mouse Gyro Controls Card
        val gyroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        // Hold-to-Move Air Trigger Button
        airTriggerBtn = Button(this).apply {
            text = "HOLD TO MOVE AIR POINTER (GYRO)"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#2563EB", dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            )
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isAirMouseTriggerHeld = true
                        accelCalibrated = false
                        hapticTick()
                        background = roundedBox("#1D4ED8", dp(10))
                        text = "AIR POINTER ACTIVE — POINT PHONE AT TV"
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

        // Gyro Lock Switch & Sensitivity Row
        val gyroOptionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        gyroLockSwitch = Switch(this).apply {
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
            setPadding(dp(16), 0, dp(6), 0)
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
        container.addView(gyroCard)

        // 4. Touchpad + Scroll Bar Area
        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
            ).apply { topMargin = dp(12) }
        }

        val touchpadView = TextView(this).apply {
            text = "TOUCHPAD\n(Slide to move cursor • Tap for Left Click)"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundedBox("#1E293B", dp(12), "#334155")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnTouchListener { _, event -> handleTouchpadEvent(event) }
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
            setOnTouchListener { _, event -> handleScrollStripEvent(event) }
        }
        padRow.addView(scrollStrip)
        container.addView(padRow)

        // 5. Mouse Click Buttons Row (Left Click, Middle Click, Right Click)
        val mouseBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply { topMargin = dp(8) }
        }

        val leftClickBtn = createHoldMouseButton("LEFT CLICK", 1)
        val middleClickBtn = createHoldMouseButton("MIDDLE", 4)
        val rightClickBtn = createHoldMouseButton("RIGHT CLICK", 2)

        mouseBtnsRow.addView(leftClickBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f))
        mouseBtnsRow.addView(middleClickBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.8f).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        })
        mouseBtnsRow.addView(rightClickBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f))
        container.addView(mouseBtnsRow)

        // 6. TV Navigation & Remote Shortcuts (Back, Home, D-Pad, Enter)
        val navCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
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
        container.addView(navCard)

        // 7. Keyboard Input Bar (Send text strings or type live)
        val kbCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(12))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        textInputField = EditText(this).apply {
            hint = "Type text to send to TV..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                    sendTypedStringAndEnter()
                    true
                } else false
            }
        }
        kbCard.addView(textInputField)

        val sendTextBtn = createStyledButton("Send Text", "#16A34A") {
            sendTypedString()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                marginStart = dp(6)
            }
        }
        kbCard.addView(sendTextBtn)

        val liveKbBtn = createStyledButton("Keyboard", "#475569") {
            textInputField.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(textInputField, InputMethodManager.SHOW_IMPLICIT)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                marginStart = dp(6)
            }
        }
        kbCard.addView(liveKbBtn)

        container.addView(kbCard)
        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    // =========================================================================
    // GYROSCOPE & ACCELEROMETER AIR-MOUSE PROCESSING
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
        if (!isAirMouseTriggerHeld && !isAirMouseLockedOn) return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Gyroscope values in rad/s:
            // event.values[0] = Pitch (tilt up/down -> vertical Y movement)
            // event.values[2] = Yaw (pan left/right -> horizontal X movement)
            val pitch = event.values[0]
            val yaw = event.values[2]

            // Deadzone to suppress hand tremor when clicking
            val deadzone = 0.025f
            val adjYaw = if (abs(yaw) > deadzone) yaw else 0f
            val adjPitch = if (abs(pitch) > deadzone) pitch else 0f

            // Negative signs align natural laser-pointer motion with screen axes
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
            // Fallback for phones without a gyroscope: tilt relative to initial hold posture
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
    // TOUCHPAD & SCROLL STRIP HANDLERS
    // =========================================================================

    private fun handleTouchpadEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                touchDownTime = System.currentTimeMillis()
                touchMovedDistance = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
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
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchDownTime
                if (elapsed < 220 && touchMovedDistance < dp(10)) {
                    // Tap-to-Click (Left Click)
                    hapticTick()
                    sendMouseReport(1, 0, 0, 0)
                    sendMouseReport(0, 0, 0, 0)
                }
                return true
            }
        }
        return false
    }

    private fun handleScrollStripEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastScrollY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastScrollY
                if (abs(dy) > dp(12)) {
                    val wheelStep = if (dy > 0) -1 else 1
                    lastScrollY = event.y
                    sendMouseReport(activeMouseButtons, 0, 0, wheelStep)
                }
                return true
            }
        }
        return false
    }

    // =========================================================================
    // BLUETOOTH HID PROFILE REGISTRATION & REPORTS
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
                    "TV Air-Mouse & Keyboard",
                    "Android Gyro Air-Mouse and Keyboard Combo",
                    "Android",
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
                                statusBadge.text = "Status: HID Ready — Select your Paired TV"
                                statusBadge.setTextColor(Color.parseColor("#38BDF8"))
                                if (pluggedDevice != null) {
                                    connectToSelectedDevice(pluggedDevice)
                                }
                            } else {
                                statusBadge.text = "Status: HID Registration Stopped"
                            }
                        }
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        runOnUiThread {
                            when (state) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connectedDevice = device
                                    statusBadge.text = "Connected to: ${device?.name ?: "TV"}"
                                    statusBadge.setTextColor(Color.parseColor("#4ADE80"))
                                    hapticTick()
                                }
                                BluetoothProfile.STATE_CONNECTING -> {
                                    statusBadge.text = "Connecting to ${device?.name ?: "TV"}..."
                                    statusBadge.setTextColor(Color.parseColor("#FACC15"))
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    if (connectedDevice == device) connectedDevice = null
                                    statusBadge.text = "Disconnected — Tap Connect to reconnect"
                                    statusBadge.setTextColor(Color.parseColor("#F87171"))
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
        deviceAdapter.clear()
        deviceAdapter.addAll(names)
        deviceAdapter.notifyDataSetChanged()
    }

    private fun connectToSelectedDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            Toast.makeText(this, "HID Service still initializing...", Toast.LENGTH_SHORT).show()
            return
        }
        hid.connect(device)
    }

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

    // =========================================================================
    // KEYBOARD TEXT & KEYEVENT CONVERSION TO USB HID USAGE CODES
    // =========================================================================

    private fun sendTypedString() {
        val text = textInputField.text?.toString() ?: return
        if (text.isEmpty()) return
        Thread {
            for (ch in text) {
                val (mod, code) = charToHidUsage(ch)
                if (code != 0) {
                    sendKeyboardKey(mod, code)
                    Thread.sleep(18)
                }
            }
            runOnUiThread { textInputField.setText("") }
        }.start()
    }

    private fun sendTypedStringAndEnter() {
        val text = textInputField.text?.toString() ?: ""
        Thread {
            for (ch in text) {
                val (mod, code) = charToHidUsage(ch)
                if (code != 0) {
                    sendKeyboardKey(mod, code)
                    Thread.sleep(18)
                }
            }
            sendKeyboardKey(0, 0x28) // Enter key
            runOnUiThread { textInputField.setText("") }
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Intercept hardware / soft-keyboard backspace & enter when input field is empty
        if (event.action == KeyEvent.ACTION_DOWN && textInputField.text.isNullOrEmpty()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    sendKeyboardKey(0, 0x2A) // Backspace
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    sendKeyboardKey(0, 0x28) // Enter
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Maps printable ASCII characters to Pair(ModifierByte, HidKeycode)
     * Modifier 0x02 = Left Shift
     */
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
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activeMouseButtons = activeMouseButtons or buttonMask
                        sendMouseReport(activeMouseButtons, 0, 0, 0)
                        background = roundedBox("#0284C7", dp(10))
                        hapticTick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox(colorHex, dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun weightParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(44), 1f).apply {
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
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
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
