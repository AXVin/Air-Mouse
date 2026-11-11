package com.example.airmouse

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Manages the Android BluetoothHidDevice service lifecycle, device switching,
 * connection resets, unpairing, and transmitting raw HID reports with full
 * permission & exception guards.
 */
@SuppressLint("MissingPermission")
class BluetoothHidController(
    private val context: Context,
    private val onStatusChanged: (statusText: String, isConnected: Boolean, isConnecting: Boolean) -> Unit,
    private val onDevicesUpdated: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = try { btManager?.adapter } catch (_: Throwable) { null }
    var hidDevice: BluetoothHidDevice? = null
        private set
    var connectedDevice: BluetoothDevice? = null
        private set

    val pairedDevices = mutableListOf<BluetoothDevice>()
    private val hidExecutor = Executors.newSingleThreadExecutor()

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun safeDeviceName(device: BluetoothDevice?): String {
        if (device == null || !hasPermissions()) return "Device"
        return try {
            device.name ?: device.address ?: "Device"
        } catch (_: Throwable) {
            "Device"
        }
    }

    fun initProfile() {
        if (!hasPermissions()) {
            mainHandler.post {
                onStatusChanged("🔵 Grant Bluetooth Permission to Connect", false, false)
            }
            return
        }
        val adapter = bluetoothAdapter ?: return
        val enabled = try { adapter.isEnabled } catch (_: Throwable) { false }
        if (!enabled) {
            mainHandler.post {
                onStatusChanged("🔴 Bluetooth Disabled", false, false)
            }
            return
        }
        if (hidDevice != null) return

        try {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile != BluetoothProfile.HID_DEVICE) return
                    val hid = proxy as? BluetoothHidDevice ?: return
                    hidDevice = hid

                    val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                        "Air Mouse",
                        "Air Mouse, Keyboard, Media & Gamepad by AXVin",
                        "AXVin",
                        BluetoothHidDevice.SUBCLASS1_COMBO,
                        HidDescriptor.DESCRIPTOR_BYTES
                    )

                    val qosOut = BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
                    )

                    try {
                        hid.registerApp(sdpSettings, null, qosOut, hidExecutor, object : BluetoothHidDevice.Callback() {
                            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                                mainHandler.post {
                                    if (registered) {
                                        onStatusChanged("🔵 HID Ready — Select a Device", false, false)
                                        if (pluggedDevice != null) {
                                            switchToDevice(pluggedDevice)
                                        }
                                    } else {
                                        onStatusChanged("⚪ HID Service Stopped", false, false)
                                    }
                                }
                            }

                            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                                mainHandler.post {
                                    when (state) {
                                        BluetoothProfile.STATE_CONNECTED -> {
                                            connectedDevice = device
                                            onStatusChanged("🟢 Connected: ${safeDeviceName(device)}", true, false)
                                            onDevicesUpdated()
                                        }
                                        BluetoothProfile.STATE_CONNECTING -> {
                                            onStatusChanged("🟡 Connecting to ${safeDeviceName(device)}...", false, true)
                                        }
                                        BluetoothProfile.STATE_DISCONNECTED -> {
                                            if (connectedDevice == device) connectedDevice = null
                                            onStatusChanged("🔴 Disconnected — Tap to Expand", false, false)
                                            onDevicesUpdated()
                                        }
                                    }
                                }
                            }
                        })
                    } catch (_: Throwable) {
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                    }
                }
            }, BluetoothProfile.HID_DEVICE)
        } catch (_: Throwable) {
        }
    }

    fun refreshPairedDevices(): List<String> {
        pairedDevices.clear()
        if (!hasPermissions()) {
            return listOf("Bluetooth Permission Needed")
        }
        return try {
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            pairedDevices.addAll(bonded)
            if (pairedDevices.isEmpty()) {
                listOf("No Paired Devices (Tap 'Pair New')")
            } else {
                pairedDevices.map { "${safeDeviceName(it)} (${it.address})" }
            }
        } catch (_: Throwable) {
            listOf("No Paired Devices (Tap 'Pair New')")
        }
    }

    fun switchToDevice(target: BluetoothDevice) {
        if (!hasPermissions()) return
        val hid = hidDevice
        if (hid == null) {
            initProfile()
            return
        }
        try {
            val prev = connectedDevice
            if (prev != null && prev.address != target.address) {
                hid.disconnect(prev)
            }
            connectedDevice = target
            hid.connect(target)
        } catch (_: Throwable) {
        }
        mainHandler.post { onDevicesUpdated() }
    }

    fun resetConnection() {
        mainHandler.post {
            onStatusChanged("🟡 Resetting Bluetooth HID...", false, true)
        }
        try {
            connectedDevice?.let { hidDevice?.disconnect(it) }
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Throwable) {
        }
        hidDevice = null
        connectedDevice = null
        refreshPairedDevices()
        initProfile()
    }

    fun unpairDevice(target: BluetoothDevice): Boolean {
        if (!hasPermissions()) return false
        try {
            hidDevice?.disconnect(target)
        } catch (_: Throwable) {
        }
        val removed = try {
            val method = target.javaClass.getMethod("removeBond")
            method.invoke(target) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
        if (removed) {
            if (connectedDevice?.address == target.address) {
                connectedDevice = null
            }
            mainHandler.post {
                onStatusChanged("🔴 Unpaired ${safeDeviceName(target)}", false, false)
            }
        }
        return removed
    }

    fun shutdown() {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Throwable) {
        }
    }

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int, panH: Int = 0) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            val report = byteArrayOf(
                buttons.toByte(),
                dx.coerceIn(-127, 127).toByte(),
                dy.coerceIn(-127, 127).toByte(),
                wheel.coerceIn(-127, 127).toByte(),
                panH.coerceIn(-127, 127).toByte()
            )
            hid.sendReport(target, HidDescriptor.REPORT_ID_MOUSE, report)
        } catch (_: Throwable) {
        }
    }

    fun sendKeyboardKey(modifier: Int, hidUsageCode: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            val downReport = byteArrayOf(
                modifier.toByte(),
                0x00,
                hidUsageCode.toByte(),
                0x00, 0x00, 0x00, 0x00, 0x00
            )
            val upReport = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
            hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, downReport)
            hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, upReport)
        } catch (_: Throwable) {
        }
    }

    fun sendPinchZoomStep(wheelStep: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            val ctrlDown = byteArrayOf(HidDescriptor.MOD_LEFT_CTRL.toByte(), 0, 0, 0, 0, 0, 0, 0)
            val ctrlUp = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
            hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, ctrlDown)
            sendMouseReport(0, 0, 0, wheelStep, 0)
            hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, ctrlUp)
        } catch (_: Throwable) {
        }
    }

    fun sendConsumerKey(usage16Bit: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            val low = (usage16Bit and 0xFF).toByte()
            val high = ((usage16Bit shr 8) and 0xFF).toByte()
            hid.sendReport(target, HidDescriptor.REPORT_ID_CONSUMER, byteArrayOf(low, high))
            hid.sendReport(target, HidDescriptor.REPORT_ID_CONSUMER, byteArrayOf(0x00, 0x00))
        } catch (_: Throwable) {
        }
    }

    fun sendGamepadReport(
        buttonMask: Int,
        hatState: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int
    ) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            val btnLow = (buttonMask and 0xFF).toByte()
            val btnHigh = ((buttonMask shr 8) and 0xFF).toByte()
            val report = byteArrayOf(
                btnLow,
                btnHigh,
                hatState.toByte(),
                lx.coerceIn(-127, 127).toByte(),
                ly.coerceIn(-127, 127).toByte(),
                rx.coerceIn(-127, 127).toByte(),
                ry.coerceIn(-127, 127).toByte()
            )
            hid.sendReport(target, HidDescriptor.REPORT_ID_GAMEPAD, report)
        } catch (_: Throwable) {
        }
    }

    fun sendSystemControlKey(bitMask: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            hid.sendReport(target, HidDescriptor.REPORT_ID_SYSTEM, byteArrayOf(bitMask.toByte()))
            hid.sendReport(target, HidDescriptor.REPORT_ID_SYSTEM, byteArrayOf(0x00))
        } catch (_: Throwable) {
        }
    }
}
