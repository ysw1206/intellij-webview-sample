package com.example.intellijwebviewsample

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * 간단한 테스트용 터미널 서비스
 */
@Service(Level.PROJECT)
class TerminalProcessService(private val project: Project) : com.intellij.openapi.Disposable {
    private val log = logger<TerminalProcessService>()

    private var bridge: TerminalOutputBridge? = null
    private var isRunning = false

    fun setBridge(b: TerminalOutputBridge) {
        log.info("🔗 Bridge set: $b")
        bridge = b
    }

    fun getTerminalComponent(): JComponent? = null // WebView만 사용

    /**
     * 테스트용 초기화 (PTY 없이)
     */
    fun initialize(workingDir: String? = System.getProperty("user.home")): Boolean {
        log.info("🚀 Initializing test terminal service...")
        
        if (project.isDisposed) {
            log.warn("❌ Project is disposed")
            return false
        }

        try {
            isRunning = true
            
            // 테스트 메시지 전송
            bridge?.onInfo("Test terminal service initialized")
            bridge?.pushStdout("Welcome to test terminal!\r\n")
            bridge?.pushStdout("Working directory: $workingDir\r\n")
            bridge?.pushStdout("$ ")
            
            log.info("✅ Test terminal service initialized successfully")
            return true
            
        } catch (t: Throwable) {
            log.error("❌ Failed to initialize test terminal", t)
            bridge?.onError("Failed to initialize: ${t.message}")
            return false
        }
    }

    /** 테스트용 입력 처리 */
    fun sendInput(text: String) {
        log.info("📤 Test input: ${text.replace("\r", "\\r").replace("\n", "\\n")}")
        
        if (!isRunning) {
            bridge?.onError("Terminal not running")
            return
        }

        try {
            // 입력 에코
            bridge?.pushStdout(text)
            
            // 간단한 명령어 처리
            val command = text.trim()
            when {
                command == "pwd" -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("${System.getProperty("user.dir")}\r\n")
                    bridge?.pushStdout("$ ")
                }
                command == "whoami" -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("${System.getProperty("user.name")}\r\n")
                    bridge?.pushStdout("$ ")
                }
                command == "date" -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("${java.util.Date()}\r\n")
                    bridge?.pushStdout("$ ")
                }
                command.startsWith("echo ") -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("${command.substring(5)}\r\n")
                    bridge?.pushStdout("$ ")
                }
                command == "ls -la" -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("total 8\r\n")
                    bridge?.pushStdout("drwxr-xr-x  10 user  staff   320 Aug 25 10:17 .\r\n")
                    bridge?.pushStdout("drwxr-xr-x   5 user  staff   160 Aug 25 10:00 ..\r\n")
                    bridge?.pushStdout("-rw-r--r--   1 user  staff  1234 Aug 25 10:17 build.gradle.kts\r\n")
                    bridge?.pushStdout("drwxr-xr-x   3 user  staff    96 Aug 25 10:00 src\r\n")
                    bridge?.pushStdout("$ ")
                }
                command.isEmpty() -> {
                    bridge?.pushStdout("\r\n$ ")
                }
                else -> {
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStderr("bash: $command: command not found\r\n")
                    bridge?.pushStdout("$ ")
                }
            }
            
        } catch (t: Throwable) {
            log.error("❌ Failed to process input", t)
            bridge?.onError("Failed to process input: ${t.message}")
        }
    }

    fun clear() {
        bridge?.pushStdout("\u001b[2J\u001b[H$ ")
    }

    fun changeDirectory(path: String) {
        bridge?.pushStdout("cd: test mode - directory change simulated\r\n$ ")
    }

    /** 테스트용 종료 */
    fun kill() {
        log.info("🔄 Terminating test terminal...")
        isRunning = false
        bridge?.onInfo("Test terminal terminated")
    }

    override fun dispose() {
        kill()
    }

    fun status(): Map<String, Any> = mapOf(
        "running" to isRunning,
        "processAlive" to isRunning,
        "mode" to "test"
    )
}
