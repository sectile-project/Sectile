package dev.sectile.network.protocol.shared.contracts

interface FormattedMessageData {
    val rawText: String?
    val messageId: String?
    val colorRgb: Int
    val bold: Boolean
    val italic: Boolean
    val monospace: Boolean
    val underline: Boolean
    val strikethrough: Boolean
    val markupEnabled: Boolean
        get() = false
    val link: String?
    val children: List<FormattedMessageData>
}
