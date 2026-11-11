package com.example.airmouse

/**
 * Composite USB/Bluetooth HID Report Descriptor & ASCII-to-HID Key Mapping.
 *
 * Report IDs:
 * - Report ID 1: Standard 8-byte Keyboard (Modifiers + 6-key rollover)
 * - Report ID 2: 5-byte Relative Mouse (Buttons, X, Y, Vertical Wheel, Horizontal AC Pan)
 * - Report ID 3: 2-byte Consumer Control (Media & TV keys)
 * - Report ID 4: 7-byte Gamepad (16 Buttons, 8-way Hat Switch, Left X/Y, Right X/Y)
 * - Report ID 5: 1-byte System Control (Power Down, Sleep, Wake Up)
 */
object HidDescriptor {
    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_MOUSE = 2
    const val REPORT_ID_CONSUMER = 3
    const val REPORT_ID_GAMEPAD = 4
    const val REPORT_ID_SYSTEM = 5

    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08

    val DESCRIPTOR_BYTES = byteArrayOf(
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

        // ================= 2. MOUSE (Report ID 2) =================
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

    fun charToHidUsage(c: Char): Pair<Int, Int> {
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
}
