package com.example.intellijwebviewsample

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * VS Code의 Pseudoterminal과 유사한 기능을 제공하는 터미널 서비스
 * 실제 쉘 프로세스를 생성하고 입출력을 제어합니다.
 */
@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {
    
    private val logger = thisLogger()
    private var currentProcess: ProcessHandler? = null
    private var currentDirectory: String = System.getProperty("user.home")
    private val listeners = ConcurrentHashMap<String, (String, Boolean) -> Unit>()
    
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
     * 새로운 터미널 세션 초기화
     * VS Code의 pseudoterminal.open()과 유사한 기능
     */
    fun initializeTerminal(): Boolean {
        return try {
            logger.info("🚀 Initializing terminal session...")
            
            // 터미널 준비 메시지 전송
            val welcomeMessage = "\u001b[32m터미널이 준비되었습니다!\u001b[0m\r\n" +
                    "\u001b[36m현재 디렉토리: \u001b[0m$currentDirectory\r\n" +
                    "\u001b[33m$ \u001b[0m"
            
            notifyListeners(welcomeMessage)
            
            logger.info("✅ Terminal session initialized successfully")
            true
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize terminal", e)
            notifyListeners("\u001b[31m❌ 터미널 초기화 실패: ${e.message}\u001b[0m\r\n", true)
            false
        }
    }
    
    /**
     * 명령어 실행
     * VS Code의 child_process.spawn과 유사한 기능
     */
    fun executeCommand(command: String) {
        logger.info("🔍 Executing command: \"$command\"")
        
        // 현재 실행 중인 프로세스가 있으면 종료
        currentProcess?.destroyProcess()
        
        try {
            // 명령어 표시
            val commandDisplay = "\u001b[36m> $command\u001b[0m\r\n"
            notifyListeners(commandDisplay)
            
            // 쉘 및 명령어 설정
            val shellCommand = if (SystemInfo.isWindows) {
                arrayOf("cmd.exe", "/c", command)
            } else {
                arrayOf("/bin/bash", "-c", command)
            }
            
            // 환경 변수 설정
            val envVars = mutableMapOf<String, String>().apply {
                putAll(System.getenv())
                put("PATH", System.getenv("PATH") ?: "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin")
                put("SHELL", System.getenv("SHELL") ?: "/bin/bash")
                put("HOME", System.getenv("HOME") ?: System.getProperty("user.home"))
                put("USER", System.getenv("USER") ?: "user")
            }
            
            logger.info("🔍 Shell command: ${shellCommand.joinToString(" ")}, CWD: $currentDirectory")
            
            // 프로세스 빌더 생성
            val processBuilder = ProcessBuilder(*shellCommand).apply {
                directory(File(currentDirectory))
                environment().putAll(envVars)
            }
            
            // 프로세스 시작
            val process = processBuilder.start()
            currentProcess = OSProcessHandler(process, command)
            
            // 출력 리스너 등록
            currentProcess?.addProcessListener(object : com.intellij.execution.process.ProcessListener {
                override fun onTextAvailable(event: com.intellij.execution.process.ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    val isError = outputType == ProcessOutputTypes.STDERR
                    
                    // ANSI 색상 처리
                    val coloredText = if (isError) {
                        "\u001b[31m$text\u001b[0m"
                    } else {
                        text.replace("\n", "\r\n")
                    }
                    
                    notifyListeners(coloredText, isError)
                }
                
                override fun processTerminated(event: com.intellij.execution.process.ProcessEvent) {
                    val exitCode = event.exitCode
                    logger.info("🔍 Command \"$command\" finished with exit code: $exitCode")
                    
                    val exitMessage = if (exitCode == 0) {
                        "\u001b[32m✅ 명령어가 성공적으로 완료되었습니다.\u001b[0m"
                    } else {
                        "\u001b[31m❌ 명령어가 종료 코드 $exitCode 로 완료되었습니다.\u001b[0m"
                    }
                    
                    notifyListeners("$exitMessage\r\n\u001b[33m$ \u001b[0m")
                    currentProcess = null
                }
                
                override fun processWillTerminate(event: com.intellij.execution.process.ProcessEvent, willBeDestroyed: Boolean) {
                    // 프로세스 종료 전 처리
                }
            })
            
            // 프로세스 시작
            currentProcess?.startNotify()
            
        } catch (e: Exception) {
            logger.error("❌ Command execution failed", e)
            val errorMessage = "\u001b[31m❌ 명령어 실행 오류: ${e.message}\u001b[0m\r\n" +
                    "\u001b[33m$ \u001b[0m"
            notifyListeners(errorMessage, true)
            currentProcess = null
        }
    }
    
    /**
     * 현재 실행 중인 프로세스 강제 종료
     */
    fun killCurrentProcess() {
        currentProcess?.let { process ->
            logger.info("🔄 Killing current process...")
            process.destroyProcess()
            notifyListeners("\r\n\u001b[31m⚠️ 프로세스가 강제 종료되었습니다.\u001b[0m\r\n\u001b[33m$ \u001b[0m")
            currentProcess = null
        } ?: run {
            notifyListeners("\r\n\u001b[33m💡 실행 중인 프로세스가 없습니다.\u001b[0m\r\n\u001b[33m$ \u001b[0m")
        }
    }
    
    /**
     * 터미널 완전 종료
     */
    fun terminateTerminal() {
        logger.info("🔄 Terminating terminal...")
        
        currentProcess?.destroyProcess()
        currentProcess = null
        listeners.clear()
        
        logger.info("✅ Terminal terminated successfully")
    }
    
    /**
     * 터미널 클리어
     */
    fun clearTerminal() {
        notifyListeners("\u001b[2J\u001b[H\u001b[33m$ \u001b[0m")
    }
    
    /**
     * 현재 터미널 상태 확인
     */
    fun getTerminalStatus(): Map<String, Any> {
        return mapOf(
            "isActive" to (currentProcess != null),
            "hasRunningProcess" to (currentProcess?.isProcessTerminated == false),
            "currentDirectory" to currentDirectory,
            "listenerCount" to listeners.size
        )
    }
    
    /**
     * 현재 디렉토리 변경
     */
    fun changeDirectory(newDirectory: String) {
        val dir = File(newDirectory)
        if (dir.exists() && dir.isDirectory) {
            currentDirectory = dir.absolutePath
            logger.info("📁 Directory changed to: $currentDirectory")
        } else {
            logger.warn("❌ Directory does not exist: $newDirectory")
        }
    }
}
