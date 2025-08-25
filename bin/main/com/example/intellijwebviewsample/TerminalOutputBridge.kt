package com.example.intellijwebviewsample

interface TerminalOutputBridge {
    fun pushStdout(text: String)
    fun pushStderr(text: String)
    fun onInfo(message: String)
    fun onError(message: String)
}
