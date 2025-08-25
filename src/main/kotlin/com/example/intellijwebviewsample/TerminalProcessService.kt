package com.example.intellijwebviewsample

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@Service(Service.Level.PROJECT)
class TerminalProcessService(private val project: Project) : AutoCloseable {

    @Volatile private var proc: PtyProcess? = null
    private val bridgeRef = AtomicReference<TerminalOutputBridge?>(null)

    fun setBridge(bridge: TerminalOutputBridge) {
        bridgeRef.set(bridge)
    }

    fun initialize(cwd: String, shell: Array<String> = defaultShell()): Boolean {
        kill()
        return try {
            val env = HashMap(System.getenv()).apply {
                putIfAbsent("TERM", "xterm-256color")
                putIfAbsent("LANG", "en_US.UTF-8")
                putIfAbsent("LC_ALL", "en_US.UTF-8")
            }

            val builder = PtyProcessBuilder(shell)
                .setEnvironment(env)
                .setDirectory(cwd)
                .setInitialColumns(120)
                .setInitialRows(30)
            // .setConsole(false)  // 버전에 없으면 주석

            val p = builder.start()
            proc = p

            startReader(p.inputStream) { s -> bridgeRef.get()?.pushStdout(s) }
            startReader(p.errorStream) { s -> bridgeRef.get()?.pushStderr(s) }
            true
        } catch (t: Throwable) {
            proc = null
            false
        }
    }

    fun sendInput(data: String) {
        try {
            proc?.outputStream?.apply {
                write(data.toByteArray(StandardCharsets.UTF_8))
                flush()
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    fun resize(cols: Int, rows: Int) {
        try { proc?.setWinSize(WinSize(cols, rows)) } catch (_: Throwable) { }
    }

    fun clear() {
        // 화면 지우기(선택) - xterm 측 clear면 이거 안 써도 됨
        sendInput("\u001B[2J\u001B[3J\u001B[H")
    }

    fun kill() {
        try { proc?.destroy() } catch (_: Throwable) { }
        proc = null
    }

    override fun close() = kill()

    private fun startReader(input: InputStream, sink: (String) -> Unit) {
        thread(isDaemon = true, name = "pty-reader") {
            val buf = ByteArray(8192)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    sink(String(buf, 0, n, StandardCharsets.UTF_8))
                }
            } catch (_: Throwable) { /* end */ }
        }
    }

    private fun defaultShell(): Array<String> {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) arrayOf("powershell.exe")
        else arrayOf("/bin/bash", "-l")
    }
}
