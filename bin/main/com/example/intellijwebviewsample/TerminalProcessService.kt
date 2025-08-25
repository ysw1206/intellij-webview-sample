package com.example.intellijwebviewsample

import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.pty4j.PtyProcess
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import java.io.File
import java.nio.charset.Charset
import javax.swing.JComponent

/**
 * Project-level service managing a real shell process and bridging it to both
 * a terminal UI widget and an external WebView via [TerminalOutputBridge].
 */
@Service(Level.PROJECT)
class TerminalProcessService(private val project: Project) : com.intellij.openapi.Disposable {
    private val log = logger<TerminalProcessService>()

    private var pty: PtyProcess? = null
    private var handler: ColoredProcessHandler? = null
    private var terminalComponent: JComponent? = null
    private var bridge: TerminalOutputBridge? = null
    private var workingDir: File? = null

    fun setBridge(b: TerminalOutputBridge) {
        bridge = b
    }

    fun getTerminalComponent(): JComponent? = terminalComponent

    /**
     * Initialize and attach the shell process to a terminal widget.
     * The UI attachment prefers [TerminalExecutionConsole] when available and
     * falls back to [JBTerminalWidget] with a [TtyConnector] otherwise.
     */
    fun initialize(workingDir: String? = System.getProperty("user.home")): Boolean {
        if (project.isDisposed) return false
        terminate()

        val wd = File(workingDir ?: System.getProperty("user.home"))
        val env: MutableMap<String, String> = HashMap(System.getenv())
        val cmd: Array<String> =
            if (SystemInfo.isWindows) arrayOf("cmd.exe")
            else arrayOf("/bin/bash", "-l")

        return try {
            pty = PtyProcess.exec(cmd, env, wd.path)   // ← File 대신 wd.path
            handler = ColoredProcessHandler(pty!!, "", Charset.defaultCharset())

            handler!!.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    if (outputType === ProcessOutputTypes.STDERR) {
                        bridge?.pushStderr(text)
                    } else {
                        bridge?.pushStdout(text)
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    bridge?.onInfo("Process terminated: exitCode=${'$'}{event.exitCode}")
                }
            })

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    // Primary path: TerminalExecutionConsole
                    val console = TerminalExecutionConsole(project, null)
                    console.attachToProcess(handler!!)
                    terminalComponent = console.component
                } catch (t: Throwable) {
                    // Fallback: JBTerminalWidget with TTY connector
                    val settings = object : JBTerminalSystemSettingsProviderBase() {}
                    val widget = JBTerminalWidget(project, settings, this)
                    val connector: TtyConnector = PtyProcessTtyConnector(pty!!, Charset.defaultCharset())
                    widget.start(connector)
                    terminalComponent = widget.component
                }
            }

            handler!!.startNotify()
            bridge?.onInfo("Terminal started in ${'$'}{wd.path}")
            true
        } catch (t: Throwable) {
            log.warn("Failed to start terminal", t)
            bridge?.onError("Failed to start terminal: ${'$'}{t.message}")
            terminate()
            false
        }
    }

    /** Send raw input to the process, appending a newline if missing. */
    fun sendInput(text: String) {
        try {
            val out = pty?.outputStream ?: return
            val normalized = if (text.endsWith("\n") || text.endsWith("\r\n")) {
                text
            } else {
                text + if (SystemInfo.isWindows) "\r\n" else "\n"
            }
            out.write(normalized.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (t: Throwable) {
            bridge?.onError("Failed to send input: ${'$'}{t.message}")
        }
    }

    fun clear() = sendInput("clear")

    fun changeDirectory(path: String) = sendInput("cd \"${'$'}path\"")

    /** Attempt graceful termination then destroy the process if needed. */
    fun kill() {
        try {
            handler?.let { if (!it.isProcessTerminated) it.destroyProcess() }
            pty?.destroy()
        } catch (t: Throwable) {
            log.warn("Failed to kill process", t)
        }
    }

    private fun terminate() {
        try {
            handler?.destroyProcess()
        } catch (_: Throwable) {
        }
        try {
            pty?.destroy()
        } catch (_: Throwable) {
        }
        handler = null
        pty = null
        terminalComponent = null
    }

    override fun dispose() {
        terminate()
    }

    fun status(): Map<String, Any> = mapOf(
        "running" to (pty != null),
        "workingDir" to (workingDir?.path ?: "")
    )
}
