package com.example.intellijwebviewsample

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.ConcurrentHashMap
import com.intellij.openapi.util.SystemInfo
import java.io.File
import com.intellij.execution.process.OSProcessHandler

@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {

    private val logger = thisLogger()
    private var terminalWidget: ShellTerminalWidget? = null
    private var isTerminalInitialized = false
    private var isTerminalRunning = false
    private val listeners = ConcurrentHashMap<String, (String, Boolean) -> Unit>()
    private var currentDirectory: String = System.getProperty("user.home")

    fun addOutputListener(id: String, listener: (output: String, isError: Boolean) -> Unit) {
        listeners[id] = listener
    }
    fun removeOutputListener(id: String) { listeners.remove(id) }

    private fun notifyListeners(output: String, isError: Boolean = false) {
        ApplicationManager.getApplication().invokeLater {
            listeners.values.forEach { it(output, isError) }
        }
    }

    fun initializeTerminal(): Boolean {
        return try {
            logger.info("Initializing IntelliJ native terminal session...")
            terminateTerminal()

            ApplicationManager.getApplication().invokeAndWait {
                // 터미널 툴윈도우를 활성화(초기화 타이밍 이슈 방지)
                ToolWindowManager.getInstance(project).getToolWindow("Terminal")?.activate(null, true)

                // 공개 API 조합
                val terminalView = TerminalView.getInstance(project)
                terminalWidget = terminalView.createLocalShellWidget(currentDirectory, "WebView Terminal")
                // 일부 버전에선 runner.run()이 필요없지만, 안전하게 러너도 만들어 둠
                LocalTerminalDirectRunner.create(project).run()

                isTerminalInitialized = true
                isTerminalRunning = true

                setupTerminalOutputCapture()

                notifyListeners("IntelliJ 네이티브 터미널이 생성되었습니다!\r\n")
                notifyListeners("vim, nano, htop 등 대화형 프로그램을 사용할 수 있습니다.\r\n")
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to initialize IntelliJ terminal", e)
            notifyListeners("❌ IntelliJ 터미널 초기화 실패: ${e.message}\r\n", true)
            isTerminalInitialized = false
            isTerminalRunning = false
            false
        }
    }

    private fun setupTerminalOutputCapture() {
        // 공개 API로 완전한 출력 캡처는 제한적이므로, 여기선 안내만.
        try {
            logger.info("Terminal output capture stub (full capture requires custom PTY handler)")
            notifyListeners("$ ")
        } catch (e: Exception) {
            logger.warn("Failed to setup terminal output capture: ${e.message}")
        }
    }

    fun handleInput(input: String) {
        val w = terminalWidget
        if (!isTerminalRunning || w == null) {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
            return
        }
        try {
            // sendString 미지원 → executeCommand 사용
            w.executeCommand(input)
            notifyListeners(input)
        } catch (e: Exception) {
            logger.error("Failed to send input to IntelliJ terminal", e)
            notifyListeners("❌ 입력 전송 실패: ${e.message}\r\n", true)
        }
    }

    fun executeCommand(command: String) {
        if (!isTerminalInitialized) initializeTerminal()
        val w = terminalWidget
        if (!isTerminalRunning || w == null) {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
            return
        }
        try {
            w.executeCommand(command)
            notifyListeners("$ $command\r\n")
        } catch (e: Exception) {
            logger.error("Failed to execute command", e)
            notifyListeners("❌ 명령어 실행 실패: ${e.message}\r\n", true)
        }
    }

    fun terminateTerminal() {
        logger.info("Terminating IntelliJ native terminal...")
        isTerminalRunning = false
        isTerminalInitialized = false
        try {
            terminalWidget?.close()
        } catch (_: Exception) {}
        terminalWidget = null
        listeners.clear()
        logger.info("IntelliJ Terminal terminated successfully")
    }

    fun killCurrentProcess() {
        val w = terminalWidget
        if (!isTerminalRunning || w == null) {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
            return
        }
        // 내장 터미널에 Ctrl+C를 정확히 보내는 공개 API는 없음.
        // 임시 시도: 줄바꿈 없이 ctrl-c 코드 전달(버전에 따라 무시될 수 있음)
        try {
            w.executeCommand("\u0003")
            notifyListeners("^C\r\n")
        } catch (e: Exception) {
            logger.warn("Ctrl+C not supported via executeCommand: ${e.message}")
            notifyListeners("⚠️ Ctrl+C 전송은 이 버전에서 보장되지 않습니다.\r\n")
        }
    }

    fun clearTerminal() {
        val w = terminalWidget
        if (!isTerminalRunning || w == null) {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
            return
        }
        try {
            w.executeCommand("clear")
            notifyListeners("\u001b[2J\u001b[H$ ")
        } catch (e: Exception) {
            logger.error("Failed to clear terminal", e)
            notifyListeners("❌ 터미널 지우기 실패\r\n", true)
        }
    }

    fun getTerminalStatus(): Map<String, Any> {
        val hasActiveProcess = terminalWidget?.hasRunningCommands() ?: false
        return mapOf(
            "isActive" to isTerminalInitialized,
            "isRunning" to isTerminalRunning,
            "hasRunningProcess" to hasActiveProcess,
            "currentDirectory" to currentDirectory,
            "listenerCount" to listeners.size,
            "terminalType" to "IntelliJ Native Terminal",
            "terminalWidget" to (terminalWidget?.let { "active" } ?: "none"),
            "supportsInteractivePrograms" to true
        )
    }

    fun changeDirectory(newDirectory: String) {
        val w = terminalWidget
        if (!isTerminalRunning || w == null) {
            notifyListeners("❌ 터미널이 실행 중이지 않습니다.\r\n", true)
            return
        }
        try {
            w.executeCommand("cd \"$newDirectory\"")
            currentDirectory = newDirectory
            notifyListeners("$ cd \"$newDirectory\"\r\n")
        } catch (e: Exception) {
            logger.error("Failed to change directory", e)
            notifyListeners("❌ 디렉토리 변경 실패: ${e.message}\r\n", true)
        }
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        // 내장 터미널은 IDE 레이아웃에 맞춰 자동 조절. 별도 처리 불필요.
        logger.info("Resize requested ${cols}x${rows} (handled automatically)")
    }

    fun getCurrentCommand(): String? {
        return try {
            if (terminalWidget?.hasRunningCommands() == true) "running" else null
        } catch (e: Exception) {
            logger.debug("Failed to get current command: ${e.message}")
            null
        }
    }

    fun syncWithWebView() {
        val w = terminalWidget ?: return
        if (!isTerminalRunning) return
        try {
            notifyListeners("📊 터미널 상태 동기화 완료\r\n")
            notifyListeners("현재 디렉토리: $currentDirectory\r\n")
            notifyListeners("$ ")
        } catch (e: Exception) {
            logger.error("Failed to sync with WebView", e)
        }
    }
}
