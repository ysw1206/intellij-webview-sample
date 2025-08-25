package com.example.intellijwebviewsample

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import javax.swing.JComponent

/**
 * PTY 준비된 터미널 서비스 (의존성 문제 해결 버전)
 */
@Service(Service.Level.PROJECT)
class TerminalProcessService(private val project: Project) : com.intellij.openapi.Disposable {
    private val log = logger<TerminalProcessService>()

    private var bridge: TerminalOutputBridge? = null
    private var isRunning = false
    private var currentDirectory = System.getProperty("user.home")

    // PTY 관련 변수들 (나중에 구현)
    private var ptyProcess: Any? = null // 나중에 PtyProcess로 변경
    private var outputThread: Thread? = null
    private var errorThread: Thread? = null

    fun setBridge(b: TerminalOutputBridge) {
        log.info("🔗 Bridge set for PTY-ready terminal")
        bridge = b
    }

    fun getTerminalComponent(): JComponent? = null

    /**
     * 향상된 시뮬레이션 터미널 (PTY 준비됨)
     */
    fun initialize(workingDir: String? = System.getProperty("user.home")): Boolean {
        log.info("🚀 Starting PTY-ready terminal service...")
        
        if (project.isDisposed) return false

        try {
            currentDirectory = workingDir ?: System.getProperty("user.home")
            isRunning = true
            
            // 향상된 시뮬레이션
            bridge?.onInfo("PTY-ready terminal service started")
            bridge?.pushStdout("PTY-ready terminal initialized\r\n")
            bridge?.pushStdout("Current directory: $currentDirectory\r\n")
            
            log.info("✅ PTY-ready terminal service started")
            return true
            
        } catch (e: Exception) {
            log.error("❌ Failed to start PTY-ready terminal", e)
            bridge?.onError("Failed to start: ${e.message}")
            return false
        }
    }

    /**
     * 향상된 명령어 처리
     */
    fun sendInput(input: String) {
        log.info("📤 PTY-ready input: '${input.replace("\r", "\\r").replace("\n", "\\n")}'")
        
        if (!isRunning) {
            bridge?.onError("Terminal not running")
            return
        }

        try {
            val command = input.replace("\r", "").replace("\n", "").trim()
            
            if (command.isEmpty()) {
                bridge?.pushStdout("$ ")
                return
            }
            
            log.info("📋 Processing enhanced command: '$command'")
            
            // 향상된 명령어 처리
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
                    // 실제 디렉토리 읽기 시도
                    try {
                        val dir = File(currentDirectory)
                        if (dir.exists() && dir.isDirectory) {
                            val files = dir.listFiles() ?: emptyArray()
                            bridge?.pushStdout("total ${files.size}\r\n")
                            for (file in files.sortedBy { it.name }) {
                                val type = if (file.isDirectory) "d" else "-"
                                val permissions = "rwxr-xr-x"
                                val size = if (file.isFile) file.length() else 0
                                bridge?.pushStdout("${type}${permissions}  1 user  staff  ${size} ${file.name}\r\n")
                            }
                        } else {
                            bridge?.pushStdout("total 0\r\n")
                        }
                    } catch (e: Exception) {
                        bridge?.pushStderr("ls: cannot access '$currentDirectory': ${e.message}\r\n")
                    }
                }
                command.startsWith("cd ") -> {
                    val newDir = command.substring(3).trim()
                    val targetDir = when {
                        newDir == "~" || newDir == "" -> System.getProperty("user.home")
                        newDir == ".." -> File(currentDirectory).parent ?: currentDirectory
                        newDir.startsWith("/") -> newDir
                        else -> "$currentDirectory${File.separator}$newDir"
                    }
                    
                    if (File(targetDir).exists()) {
                        currentDirectory = targetDir
                    } else {
                        bridge?.pushStderr("cd: no such file or directory: $newDir\r\n")
                    }
                }
                command == "clear" -> {
                    // WebView에서 처리
                }
                command == "help" -> {
                    bridge?.pushStdout("PTY-Ready Terminal Commands:\r\n")
                    bridge?.pushStdout("  pwd         - show current directory\r\n")
                    bridge?.pushStdout("  whoami      - show current user\r\n")
                    bridge?.pushStdout("  date        - show current date\r\n")
                    bridge?.pushStdout("  ls [-la]    - list files (real directory)\r\n")
                    bridge?.pushStdout("  cd <dir>    - change directory (real navigation)\r\n")
                    bridge?.pushStdout("  echo <text> - print text\r\n")
                    bridge?.pushStdout("  clear       - clear screen\r\n")
                    bridge?.pushStdout("  help        - show this help\r\n")
                    bridge?.pushStdout("  exit        - exit terminal\r\n")
                    bridge?.pushStdout("\r\n")
                    bridge?.pushStdout("🔧 Ready for PTY upgrade when dependencies are resolved.\r\n")
                }
                command == "exit" -> {
                    bridge?.pushStdout("logout\r\n")
                    kill()
                    return
                }
                else -> {
                    bridge?.pushStderr("bash: $command: command not found\r\n")
                    bridge?.pushStdout("(PTY mode will support all commands)\r\n")
                }
            }
            
            bridge?.pushStdout("$ ")
            
        } catch (e: Exception) {
            log.error("❌ Failed to process command", e)
            bridge?.onError("Command failed: ${e.message}")
            bridge?.pushStdout("$ ")
        }
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        log.info("📐 Terminal resize requested: ${cols}x${rows} (PTY-ready)")
        // PTY 구현 시 실제 resize 구현
    }

    fun clear() {
        // WebView에서 처리
    }

    fun kill() {
        log.info("🔄 Terminating PTY-ready terminal...")
        isRunning = false
        
        // 나중에 PTY 정리 코드 추가
        outputThread?.interrupt()
        errorThread?.interrupt()
    }

    override fun dispose() {
        kill()
    }

    fun status(): Map<String, Any> = mapOf(
        "running" to isRunning,
        "processAlive" to isRunning,
        "mode" to "pty-ready-simulation",
        "currentDirectory" to currentDirectory
    )
}
