package com.example.intellijwebviewsample

interface TerminalOutputBridge {
    fun pushStdout(text: String)
    fun pushStderr(text: String)
}