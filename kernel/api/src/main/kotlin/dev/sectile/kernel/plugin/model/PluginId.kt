package dev.sectile.kernel.plugin.model

@JvmInline
value class PluginId(
    val value: String,
) {
    init {
        isValidName()
    }

    private fun isValidName() {
        require(value.length in 1..32) { "Plugin id need 1-32 characters." } // For readability and to prevent abuse.

        for (char in value) {
            require(char == '-' || char in 'a'..'z' || char in 'A'..'Z') {
                "Plugin id '$value' only allows letters and hyphens."
            }
        }
    }

    override fun toString(): String = value
}
