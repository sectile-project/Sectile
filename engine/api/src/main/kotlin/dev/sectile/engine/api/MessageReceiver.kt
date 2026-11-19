package dev.sectile.engine.api

interface MessageReceiver {
    fun receiveMessage(message: String)
}
