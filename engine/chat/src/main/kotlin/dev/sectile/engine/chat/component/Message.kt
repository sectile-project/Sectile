package dev.sectile.engine.chat.component

import dev.sectile.engine.chat.color.Color

class Message {
    private var bitmaskData: Int = 0
    var color: Color = Color.WHITE
    var rawText: String? = null
    var messageId: String? = null
    var link: String? = null

    var bold: Boolean
        get() = (bitmaskData and 1) != 0
        set(enabled) = setStyle(enabled, 1)
    var italic: Boolean
        get() = (bitmaskData and 1 shl 1) != 0
        set(enabled) = setStyle(enabled, 1 shl 1)
    var monospace: Boolean
        get() = (bitmaskData and 1 shl 2) != 0
        set(enabled) = setStyle(enabled, 1 shl 2)
    var strikethrough: Boolean
        get() = (bitmaskData and 1 shl 3) != 0
        set(enabled) = setStyle(enabled, 1 shl 3)
    var underline: Boolean
        get() = (bitmaskData and 1 shl 4) != 0
        set(enabled) = setStyle(enabled, 1 shl 4)

    private fun setStyle(enabled: Boolean, mask: Int) {
        bitmaskData = if (enabled) bitmaskData or mask else bitmaskData and mask.inv()
    }
}
