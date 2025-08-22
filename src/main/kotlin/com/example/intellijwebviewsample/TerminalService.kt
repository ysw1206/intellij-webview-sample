package com.example.intellijwebviewsample

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * 개선된 터미널 서비스 - VS Code Pseudoterminal과 유사한 세션 관리
 * IntelliJ ProcessHandler를 사용하되 세션 상태를 유지하는 방식으로 구현
 */
@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {
    
    private val logger = thisLogger()
    private var shellProcess: ProcessHandler? = null
    private var inputWriter: OutputStreamWriter? = null
    private var isTerminalInitialized = false
    private var isTerminalRunning = false
    private val listeners = ConcurrentHashMap<String, (String, Boolean) -> Unit>()
    
    // 터미널 상태 관리
    private var currentDirectory: String = System.getProperty("user.home")
    private var environmentVariables: MutableMap<String, String> = mutableMapOf()
    
    fun addOutputListener(id: String, listener: (output: String, isError: Boolean) -> Unit) {
        listeners[id] = listener
    }
    
    fun removeOutputListener(id: String) {
        listeners.remove(id)
    }
    
    private fun notifyListeners(output: String, isError: Boolean = false) {
        listeners.values.forEach { listener ->
            ApplicationManager.getApplication().invokeLater {
                listener(output, isError)
            }
        }
    }
    
    /**
     * 지속적인 쉘 세션 초기화 - VS Code Pseudoterminal과 유사
     */
    fun initializeTerminal(): Boolean {
        return try {
            logger.info("🚀 Initializing persistent shell session...")
            
            // 기존 터미널 정리
            terminateTerminal()
            
            // 환경 변수 초기화
            environmentVariables.putAll(System.getenv())
            environmentVariables["PWD"] = currentDirectory
            
            // 지속적인 쉘 프로세스 시작
            val success = startPersistentShell()
            
            if (success) {
                isTerminalInitialized = true
                isTerminalRunning = true
                
                logger.info("✅ Persistent shell session initialized successfully")
                
                // 초기 환영 메시지
                notifyListeners("🎯 IntelliJ 지속형 터미널 세션이 시작되었습니다!\r\n")
                notifyListeners("현재 디렉토리: $currentDirectory\r\n")
                notifyListeners("$ ")
                
                true
            } else {
                false
            }
            
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize terminal", e)
            notifyListeners("❌ 터미널 초기화 실패: ${e.message}\r\n", true)
            isTerminalInitialized = false
            isTerminalRunning = false
            false
        }
    }
    
    /**
     * 지속적인 쉘 프로세스 시작
     */
    private fun startPersistentShell(): Boolean {
        return try {
            // 쉘 명령어 설정 (대화형 모드)
            val shellCommand = when {
                SystemInfo.isWindows -> arrayOf("cmd.exe")
                SystemInfo.isMac -> arrayOf("/bin/zsh", "-i") // 대화형 모드
                SystemInfo.isLinux -> arrayOf("/bin/bash", "-i") // 대화형 모드
                else -> arrayOf("/bin/sh", "-i")
            }
            
            logger.info("🔍 Starting persistent shell: ${shellCommand.joinToString(" ")}")
            
            // 프로세스 빌더 생성
            val processBuilder = ProcessBuilder(*shellCommand).apply {
                directory(File(currentDirectory))
                environment().putAll(environmentVariables)
                
                // 대화형 터미널 환경 설정
                environment()["TERM"] = "xterm-256color"
                environment()["PS1"] = "$ " // 간단한 프롬프트
            }
            
            // 프로세스 시작
            val process = processBuilder.start()
            shellProcess = OSProcessHandler(process, shellCommand.joinToString(" "))
            
            // 입력 스트림 설정
            inputWriter = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
            
            // 출력 리스너 등록
            shellProcess?.addProcessListener(object : com.intellij.execution.process.ProcessListener {
                override fun onTextAvailable(event: com.intellij.execution.process.ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    val isError = outputType == ProcessOutputTypes.STDERR
                    
                    // 출력 텍스트 처리 (ANSI 색상 및 제어 문자 유지)
                    val processedText = if (isError) {
                        "\u001b[31m$text\u001b[0m"
                    } else {
                        text.replace("\n", "\r\n")
                    }
                    
                    notifyListeners(processedText, isError)
                }
                
                override fun processTerminated(event: com.intellij.execution.process.ProcessEvent) {
                    val exitCode = event.exitCode
                    logger.info("🔄 Shell process terminated with exit code: $exitCode")
                    
                    isTerminalRunning = false
                    notifyListeners("\r\n🔄 쉘 세션이 종료되었습니다.\r\n", true)
                    
                    // 자동 재시작 시도
                    if (isTerminalInitialized && exitCode != 0) {
                        logger.info("🔄 Attempting to restart shell session...")
                        ApplicationManager.getApplication().invokeLater {
                            initializeTerminal()
                        }
                    }
                }
                
                override fun processWillTerminate(event: com.intellij.execution.process.ProcessEvent, willBeDestroyed: Boolean) {
                    logger.info("🔄 Shell process will terminate...")
                }
            })
            
            // 프로세스 시작
            shellProcess?.startNotify()
            
            true
        } catch (e: Exception) {
            logger.error("❌ Failed to start persistent shell", e)
            notifyListeners("❌ 지속적 쉘 시작 실패: ${e.message}\r\n", true)
            false
        }
    }
    
    /**
     * 사용자 입력을 쉘로 직접 전송
     */
    fun handleInput(input: String) {
        if (!isTerminalRunning || inputWriter == null) {
            logger.warn("⚠️ Terminal not running, cannot handle input")
            return
        }
        
        try {
            logger.debug("⌨️ Sending input to shell: ${input.replace("\r", "\\r").replace("\n", "\\n")}")
            inputWriter!!.write(input)
            inputWriter!!.flush()
        } catch (e: Exception) {
            logger.error("❌ Failed to send input to shell", e)
            notifyListeners("❌ 입력 전송 실패: ${e.message}\r\n", true)
        }
    }
    
    /**
     * 명령어 실행 (하위 호환성)
     */
    fun executeCommand(command: String) {
        logger.info("📝 Executing command: \"$command\"")
        
        if (!isTerminalInitialized) {
            initializeTerminal()
        }
        
        if (isTerminalRunning) {
            // 명령어 + Enter 전송
            handleInput("$command\r\n")
        } else {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
        }
    }
    
    /**
     * 터미널 완전 종료
     */
    fun terminateTerminal() {
        logger.info("🔄 Terminating persistent shell session...")
        
        isTerminalRunning = false
        isTerminalInitialized = false
        
        // 입력 스트림 정리
        try {
            inputWriter?.close()
            inputWriter = null
        } catch (e: Exception) {
            logger.warn("Warning closing input writer", e)
        }
        
        // 쉘 프로세스 종료
        try {
            shellProcess?.destroyProcess()
            shellProcess = null
        } catch (e: Exception) {
            logger.warn("Warning destroying shell process", e)
        }
        
        listeners.clear()
        
        logger.info("✅ Terminal terminated successfully")
    }
    
    /**
     * 현재 실행 중인 프로세스 강제 종료 (Ctrl+C)
     */
    fun killCurrentProcess() {
        if (isTerminalRunning) {
            handleInput("\u0003") // ASCII 3 = Ctrl+C
            logger.info("⚡ Sent Ctrl+C to shell")
        }
    }
    
    /**
     * 터미널 클리어
     */
    fun clearTerminal() {
        if (isTerminalRunning) {
            handleInput("clear\r\n")
            logger.info("🧹 Sent clear command to shell")
        }
    }
    
    /**
     * 현재 터미널 상태 확인
     */
    fun getTerminalStatus(): Map<String, Any> {
        return mapOf(
            "isActive" to isTerminalInitialized,
            "isRunning" to isTerminalRunning,
            "hasRunningProcess" to (shellProcess?.isProcessTerminated == false),
            "currentDirectory" to currentDirectory,
            "listenerCount" to listeners.size,
            "processId" to (shellProcess?.let { "active" } ?: "none"),
            "terminalType" to "IntelliJ Persistent Shell"
        )
    }
    
    /**
     * 현재 디렉토리 변경
     */
    fun changeDirectory(newDirectory: String) {
        if (isTerminalRunning) {
            // cd 명령어 전송 후 pwd로 확인
            handleInput("cd \"$newDirectory\" && pwd\r\n")
            currentDirectory = newDirectory // 로컬 상태도 업데이트
            logger.info("📁 Sent cd command: $newDirectory")
        }
    }
    
    /**
     * 터미널 크기 조정 (제한적 지원)
     */
    fun resizeTerminal(cols: Int, rows: Int) {
        logger.info("📐 Terminal resize requested: ${cols}x${rows} (limited support in ProcessHandler)")
        // ProcessHandler에서는 터미널 크기 조정이 제한적임
    }
}
