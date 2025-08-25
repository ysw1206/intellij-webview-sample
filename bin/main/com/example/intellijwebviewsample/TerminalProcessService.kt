package com.example.intellijwebviewsample

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * 출력 형식이 개선된 테스트용 터미널 서비스
 */
@Service(Level.PROJECT)
class TerminalProcessService(private val project: Project) : com.intellij.openapi.Disposable {
    private val log = logger<TerminalProcessService>()

    private var bridge: TerminalOutputBridge? = null
    private var isRunning = false
    private var currentDirectory = System.getProperty("user.home")

    fun setBridge(b: TerminalOutputBridge) {
        log.info("🔗 Bridge set: $b")
        bridge = b
    }

    fun getTerminalComponent(): JComponent? = null // WebView만 사용

    /**
     * 테스트용 초기화
     */
    fun initialize(workingDir: String? = System.getProperty("user.home")): Boolean {
        log.info("🚀 Initializing terminal service with proper formatting...")
        
        if (project.isDisposed) {
            log.warn("❌ Project is disposed")
            return false
        }

        try {
            currentDirectory = workingDir ?: System.getProperty("user.home")
            isRunning = true
            
            // 초기화 메시지
            bridge?.onInfo("Terminal service initialized with proper formatting")
            bridge?.pushStdout("Welcome to terminal!\r\n")
            bridge?.pushStdout("Working directory: $currentDirectory\r\n")
            
            log.info("✅ Terminal service initialized successfully")
            return true
            
        } catch (t: Throwable) {
            log.error("❌ Failed to initialize terminal", t)
            bridge?.onError("Failed to initialize: ${t.message}")
            return false
        }
    }

    /** 명령어 실행 형식 개선 */
    fun sendInput(text: String) {
        log.info("📤 Input received: '${text.replace("\r", "\\r").replace("\n", "\\n")}'")
        
        if (!isRunning) {
            bridge?.onError("Terminal not running")
            return
        }

        try {
            // 개행 문자 제거하고 명령어 추출
            val command = text.replace("\r", "").replace("\n", "").trim()
            
            if (command.isEmpty()) {
                // 빈 명령어는 새 프롬프트만 표시
                bridge?.pushStdout("$ ")
                return
            }
            
            log.info("📋 Executing command: '$command'")
            
            // 명령어 실행 및 출력 (프롬프트는 출력하지 않음)
            when {
                command == "pwd" -> {
                    bridge?.pushStdout("$currentDirectory\r\n")
                }
                command == "whoami" -> {
                    bridge?.pushStdout("${System.getProperty("user.name")}\r\n")
                }
                command == "date" -> {
                    val dateStr = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH)
                        .format(java.util.Date())
                    bridge?.pushStdout("$dateStr\r\n")
                }
                command.startsWith("echo ") -> {
                    val message = command.substring(5)
                    bridge?.pushStdout("$message\r\n")
                }
                command == "ls" || command == "ls -la" -> {
                    bridge?.pushStdout("total 8\r\n")
                    bridge?.pushStdout("drwxr-xr-x  10 user  staff   320 Aug 25 10:58 .\r\n")
                    bridge?.pushStdout("drwxr-xr-x   5 user  staff   160 Aug 25 10:00 ..\r\n")
                    bridge?.pushStdout("-rw-r--r--   1 user  staff  1234 Aug 25 10:58 build.gradle.kts\r\n")
                    bridge?.pushStdout("drwxr-xr-x   3 user  staff    96 Aug 25 10:00 src\r\n")
                    bridge?.pushStdout("-rw-r--r--   1 user  staff   567 Aug 25 10:58 README.md\r\n")
                }
                command == "clear" -> {
                    // clear는 WebView에서 처리되므로 아무것도 출력하지 않음
                    return
                }
                command.startsWith("cd ") -> {
                    val newDir = command.substring(3).trim()
                    when {
                        newDir == "~" || newDir == "" -> {
                            currentDirectory = System.getProperty("user.home")
                        }
                        newDir == ".." -> {
                            val parent = java.io.File(currentDirectory).parent
                            if (parent != null) {
                                currentDirectory = parent
                            }
                        }
                        newDir.startsWith("/") -> {
                            currentDirectory = newDir
                        }
                        else -> {
                            currentDirectory = "$currentDirectory/$newDir"
                        }
                    }
                    // cd 명령어는 출력 없음 (실제 bash처럼)
                }
                command == "exit" -> {
                    bridge?.pushStdout("logout\r\n")
                    kill()
                    return
                }
                command == "help" -> {
                    bridge?.pushStdout("Available commands:\r\n")
                    bridge?.pushStdout("  pwd         - show current directory\r\n")
                    bridge?.pushStdout("  whoami      - show current user\r\n")
                    bridge?.pushStdout("  date        - show current date\r\n")
                    bridge?.pushStdout("  ls [-la]    - list files\r\n")
                    bridge?.pushStdout("  echo <text> - print text\r\n")
                    bridge?.pushStdout("  cd <dir>    - change directory\r\n")
                    bridge?.pushStdout("  clear       - clear screen\r\n")
                    bridge?.pushStdout("  help        - show this help\r\n")
                    bridge?.pushStdout("  exit        - exit terminal\r\n")
                }
                else -> {
                    bridge?.pushStderr("bash: $command: command not found\r\n")
                }
            }
            
            // 명령어 실행 후 새 프롬프트 표시
            bridge?.pushStdout("$ ")
            
        } catch (t: Throwable) {
            log.error("❌ Failed to process input", t)
            bridge?.onError("Failed to process input: ${t.message}")
            bridge?.pushStdout("$ ")
        }
    }

    fun clear() {
        // clear는 WebView에서 처리
    }

    fun changeDirectory(path: String) {
        sendInput("cd $path")
    }

    /** 테스트용 종료 */
    fun kill() {
        log.info("🔄 Terminating terminal...")
        isRunning = false
        bridge?.onInfo("Terminal terminated")
    }

    override fun dispose() {
        kill()
    }

    fun status(): Map<String, Any> = mapOf(
        "running" to isRunning,
        "processAlive" to isRunning,
        "mode" to "formatted-test",
        "currentDirectory" to currentDirectory
    )
}
