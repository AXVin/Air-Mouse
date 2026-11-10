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
import android.graphics.BitmapFactory
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
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
import kotlin.math.roundToInt

/**
 * Air Mouse — Created by AXVin
 *
 * Turns an Android 9+ (API 28+) phone into a native Bluetooth HID Combo Air-Mouse & Keyboard
 * for Smart TVs / Android TV / Google TV / Fire TV / LG webOS / Samsung Tizen.
 *
 * Key Highlights:
 * - Created by AXVin ("Air Mouse" branding & embedded logo icon)
 * - Trackpad Scroll-Lock Mode: Automatically locks vertical screen scrolling while fingers are
 *   on the trackpad or scroll strip so the app layout never shifts up or down during cursor use
 * - Real-Time Live Keyboard: Sends every character, Backspace, Enter, and Space immediately
 *   as it is typed on the soft keyboard without needing a "Send Text" button
 * - 60Hz Gyroscope Air-Mouse (with automatic Accelerometer tilt fallback + deadzone smoothing)
 */
@SuppressLint("MissingPermission", "ClickableViewAccessibility", "SetTextI18n")
class MainActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMISSIONS = 1001
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2
        private const val SENTINEL_CHAR = "\u200B" // Zero-width sentinel to detect soft-keyboard Backspace reliably

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

    /**
     * Custom ScrollView that supports Scroll-Lock mode whenever the user's fingers
     * are touching the trackpad or scroll strip so the UI never moves up and down.
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
    private var gyroSensitivity = 22.0f
    private var residualX = 0.0f
    private var residualY = 0.0f

    // Accelerometer calibration baseline
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

    // Live keyboard state
    private var isResettingLiveInput = false

    // UI Elements
    private lateinit var rootScrollView: LockableScrollView
    private lateinit var statusBadge: TextView
    private lateinit var sensorBadge: TextView
    private lateinit var touchpadView: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var liveKeyboardInput: EditText
    private lateinit var lastKeyIndicator: TextView
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
    // UI CONSTRUCTION (With "Air Mouse" Logo, "AXVin" Credit & Scroll-Lock)
    // =========================================================================

    private fun buildUserInterface() {
        rootScrollView = LockableScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }

        // 1. Header Bar with "Air Mouse" Logo + Title + Creator "AXVin"
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(10), dp(14), dp(10))
        }

        val logoView = createLogoView().apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(titleText)

        val creatorBadge = TextView(this).apply {
            text = "by AXVin"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#0F172A", dp(6), "#0284C7")
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        }
        titleRow.addView(creatorBadge)
        titleCol.addView(titleRow)

        statusBadge = TextView(this).apply {
            text = "Status: Initializing Bluetooth HID..."
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(3), 0, dp(1))
        }
        titleCol.addView(statusBadge)

        sensorBadge = TextView(this).apply {
            val modeName = when {
                gyroSensor != null -> "Gyroscope 60Hz Air-Mouse Ready"
                accelSensor != null -> "Accelerometer Tilt Air-Mouse Ready"
                else -> "Touchpad Mode Ready"
            }
            text = "Sensor: $modeName"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        titleCol.addView(sensorBadge)

        headerRow.addView(titleCol)
        container.addView(headerRow)

        // 2. Paired TV Selector Row
        val btRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox("#1E293B", dp(10))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
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
            ).apply { topMargin = dp(10) }
        }

        airTriggerBtn = Button(this).apply {
            text = "HOLD TO MOVE AIR POINTER (GYRO)"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBox("#2563EB", dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
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
        container.addView(gyroCard)

        // 4. Touchpad + Scroll Bar Area (Auto Scroll-Lock Mode when touched!)
        val padRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
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
        container.addView(padRow)

        // 5. Mouse Click Buttons Row (Left Click, Middle Click, Right Click)
        val mouseBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
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

        // 6. Live Keypress-by-Keypress Keyboard Card (No "Send Text" button needed)
        val kbCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBox("#1E293B", dp(12), "#334155")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val kbTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val kbTitle = TextView(this).apply {
            text = "Live Keyboard (Sends every keypress instantly)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        kbTopRow.addView(kbTitle)

        lastKeyIndicator = TextView(this).apply {
            text = "Last key: —"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        kbTopRow.addView(lastKeyIndicator)
        kbCard.addView(kbTopRow)

        val kbInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        liveKeyboardInput = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val base = super.onCreateInputConnection(outAttrs) ?: return null
                return object : InputConnectionWrapper(base, true) {
                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0 && afterLength == 0) {
                            sendKeyboardKey(0, 0x2A) // Backspace
                            updateLastKeyLabel("BACKSPACE")
                            return true
                        }
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            when (event.keyCode) {
                                KeyEvent.KEYCODE_DEL -> {
                                    sendKeyboardKey(0, 0x2A) // Backspace
                                    updateLastKeyLabel("BACKSPACE")
                                    return true
                                }
                                KeyEvent.KEYCODE_ENTER -> {
                                    sendKeyboardKey(0, 0x28) // Enter
                                    updateLastKeyLabel("ENTER")
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
            hint = "Tap here to type live on TV..."
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
                        // Sentinel char was deleted -> user pressed Backspace on soft keyboard
                        sendKeyboardKey(0, 0x2A)
                        updateLastKeyLabel("BACKSPACE")
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
                    updateLastKeyLabel("ENTER")
                    true
                } else false
            }
        }
        kbInputRow.addView(liveKeyboardInput)

        val openKbBtn = createStyledButton("Open Keyboard", "#0284C7") {
            liveKeyboardInput.requestFocus()
            liveKeyboardInput.setSelection(liveKeyboardInput.text.length)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(liveKeyboardInput, InputMethodManager.SHOW_FORCED)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = dp(8)
            }
        }
        kbInputRow.addView(openKbBtn)
        kbCard.addView(kbInputRow)
        container.addView(kbCard)

        // 7. TV Navigation & Remote Shortcuts (Back, Home, D-Pad, Enter)
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
        container.addView(navCard)

        // 8. Creator Footer Attribution
        val footerText = TextView(this).apply {
            text = "Air Mouse • Created by AXVin"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(14), 0, dp(4))
        }
        container.addView(footerText)

        rootScrollView.addView(container)
        setContentView(rootScrollView)
    }

    /**
     * Loads the packaged Air Mouse logo drawable (`R.drawable.air_mouse_logo`) if present,
     * with a fallback custom Canvas vector logo depicting the "Air Mouse" emblem.
     */
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
                textSize = dp(8).toFloat()
            }
            private val rect = RectF()
            private val cursorPath = Path()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                rect.set(dp(2).toFloat(), dp(2).toFloat(), w - dp(2), h - dp(2))
                canvas.drawRoundRect(rect, dp(12).toFloat(), dp(12).toFloat(), bgPaint)
                canvas.drawRoundRect(rect, dp(12).toFloat(), dp(12).toFloat(), borderPaint)

                // Draw stylized Air Mouse cursor arrow
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
    // TOUCHPAD & SCROLL STRIP HANDLERS (WITH AUTOMATIC SCROLL-LOCK)
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
                // Engage Scroll-Lock so the parent ScrollView cannot move up or down
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
                    "Air Mouse",
                    "Air Mouse & Keyboard by AXVin",
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
    // REAL-TIME KEYPRESS SENDER (EVERY KEY SENT IMMEDIATELY AS TYPED)
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
            val displayChar = when (ch) {
                ' ' -> "SPACE"
                '\n' -> "ENTER"
                else -> "'$ch'"
            }
            updateLastKeyLabel(displayChar)
        }
    }

    private fun updateLastKeyLabel(label: String) {
        lastKeyIndicator.text = "Sent: $label"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    sendKeyboardKey(0, 0x2A) // Backspace
                    updateLastKeyLabel("BACKSPACE")
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    sendKeyboardKey(0, 0x28) // Enter
                    updateLastKeyLabel("ENTER")
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
            // If a hardware keyboard or direct keycode with printable unicode char is pressed
            val unicodeChar = event.unicodeChar
            if (unicodeChar > 0) {
                sendCharImmediate(unicodeChar.toChar())
                return true
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

    private fun createKeyButton(label: String, hidKeyCode: Int, colorHex: String = "#334155"): Button {
        return createStyledButton(label, colorHex) {
            hapticTick()
            sendKeyboardKey(0, hidKeyCode)
            updateLastKeyLabel(label)
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
