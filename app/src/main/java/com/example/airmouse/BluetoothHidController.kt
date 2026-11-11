package com.example.airmouse

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.Executors

/**
 * Manages the Android BluetoothHidDevice service lifecycle, device switching,
 * connection resets, unpairing, and transmitting raw HID reports.
 */
@SuppressLint("MissingPermission")
class BluetoothHidController(
    private val context: Context,
    private val onStatusChanged: (statusText: String, isConnected: Boolean, isConnecting: Boolean) -> Unit,
    private val onDevicesUpdated: () -> Unit
) {
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = btManager?.adapter
    var hidDevice: BluetoothHidDevice? = null
        private set
    var connectedDevice: BluetoothDevice? = null
        private set

    val pairedDevices = mutableListOf<BluetoothDevice>()
    private val hidExecutor = Executors.newSingleThreadExecutor()

    fun initProfile() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            onStatusChanged("🔴 Bluetooth Disabled", false, false)
            return
        }
        if (hidDevice != null) return

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

                hid.registerApp(sdpSettings, null, qosOut, hidExecutor, object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                        if (registered) {
                            onStatusChanged("🔵 HID Ready — Select a Device", false, false)
                            if (pluggedDevice != null) {
                                switchToDevice(pluggedDevice)
                            }
                        } else {
                            onStatusChanged("⚪ HID Service Stopped", false, false)
                        }
                    }

                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        when (state) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                connectedDevice = device
                                onStatusChanged("🟢 Connected: ${device?.name ?: "TV"}", true, false)
                                onDevicesUpdated()
                            }
                            BluetoothProfile.STATE_CONNECTING -> {
                                onStatusChanged("🟡 Connecting to ${device?.name ?: "Device"}...", false, true)
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (connectedDevice == device) connectedDevice = null
                                onStatusChanged("🔴 Disconnected — Tap to Expand", false, false)
                                onDevicesUpdated()
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

    fun refreshPairedDevices(): List<String> {
        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        pairedDevices.clear()
        pairedDevices.addAll(bonded)
        return if (pairedDevices.isEmpty()) {
            listOf("No Paired Devices (Tap 'Pair New')")
        } else {
            pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }
        }
    }

    fun switchToDevice(target: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            initProfile()
            return
        }
        val prev = connectedDevice
        if (prev != null && prev.address != target.address) {
            try {
                hid.disconnect(prev)
            } catch (_: Exception) {
            }
        }
        connectedDevice = target
        hid.connect(target)
        onDevicesUpdated()
    }

    fun resetConnection() {
        onStatusChanged("🟡 Resetting Bluetooth HID...", false, true)
        try {
            connectedDevice?.let { hidDevice?.disconnect(it) }
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {
        }
        hidDevice = null
        connectedDevice = null
        refreshPairedDevices()
        initProfile()
    }

    fun unpairDevice(target: BluetoothDevice): Boolean {
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
            if (connectedDevice?.address == target.address) {
                connectedDevice = null
            }
            onStatusChanged("🔴 Unpaired ${target.name ?: "Device"}", false, false)
        }
        return removed
    }

    fun shutdown() {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {
        }
    }

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int, panH: Int = 0) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte(),
            panH.coerceIn(-127, 127).toByte()
        )
        hid.sendReport(target, HidDescriptor.REPORT_ID_MOUSE, report)
    }

    fun sendKeyboardKey(modifier: Int, hidUsageCode: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val downReport = byteArrayOf(
            modifier.toByte(),
            0x00,
            hidUsageCode.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00
        )
        val upReport = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, downReport)
        hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, upReport)
    }

    fun sendPinchZoomStep(wheelStep: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val ctrlDown = byteArrayOf(HidDescriptor.MOD_LEFT_CTRL.toByte(), 0, 0, 0, 0, 0, 0, 0)
        val ctrlUp = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, ctrlDown)
        sendMouseReport(0, 0, 0, wheelStep, 0)
        hid.sendReport(target, HidDescriptor.REPORT_ID_KEYBOARD, ctrlUp)
    }

    fun sendConsumerKey(usage16Bit: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        val low = (usage16Bit and 0xFF).toByte()
        val high = ((usage16Bit shr 8) and 0xFF).toByte()
        hid.sendReport(target, HidDescriptor.REPORT_ID_CONSUMER, byteArrayOf(low, high))
        hid.sendReport(target, HidDescriptor.REPORT_ID_CONSUMER, byteArrayOf(0x00, 0x00))
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
    }

    fun sendSystemControlKey(bitMask: Int) {
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return
        hid.sendReport(target, HidDescriptor.REPORT_ID_SYSTEM, byteArrayOf(bitMask.toByte()))
        hid.sendReport(target, HidDescriptor.REPORT_ID_SYSTEM, byteArrayOf(0x00))
    }
}
