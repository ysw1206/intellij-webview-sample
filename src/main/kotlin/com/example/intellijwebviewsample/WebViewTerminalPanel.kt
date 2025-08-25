package com.example.intellijwebviewsample

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class WebViewTerminalPanel(private val project: Project) {
    
    private val logger = thisLogger()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val terminalService = project.service<TerminalProcessService>()
    private val browser = JBCefBrowser()
    private val panel = JPanel(BorderLayout())
    private var isTerminalReady = false
    
    init {
        setupWebView()
        setupMessageHandling()
        panel.add(browser.component, BorderLayout.CENTER)
    }
    
    fun getComponent(): JComponent = panel
    
    private fun setupWebView() {
        logger.info("🔧 Setting up WebView...")
        browser.loadHTML(getWebViewContent())
        logger.info("✅ WebView setup completed")
    }
    
    private fun setupMessageHandling() {
        logger.info("🔧 Setting up message handling...")
        
        val jsQuery = JBCefJSQuery.create(browser)
        
        jsQuery.addHandler { query ->
            try {
                logger.info("📨 Received message from WebView: $query")
                
                val message = objectMapper.readValue<Map<String, Any>>(query)
                val command = message["command"] as? String
                
                ApplicationManager.getApplication().invokeLater {
                    when (command) {
                        "createTerminal" -> handleCreateTerminal()
                        "executeCommand" -> {
                            val commandText = message["commandText"] as? String
                            if (commandText != null) {
                                handleExecuteCommand(commandText)
                            }
                        }
                        "userInput" -> {
                            val input = message["input"] as? String
                            if (input != null) {
                                handleUserInput(input)
                            }
                        }
                        "terminateTerminal" -> handleTerminateTerminal()
                        "killProcess" -> handleKillProcess()
                        "clearTerminal" -> handleClearTerminal()
                        "checkTerminalStatus" -> handleCheckTerminalStatus()
                        else -> logger.warn("❓ Unknown command: $command")
                    }
                }
                
                JBCefJSQuery.Response("ok")
            } catch (e: Exception) {
                logger.error("❌ Error handling message from WebView", e)
                JBCefJSQuery.Response(null, 0, "Error: ${e.message}")
            }
        }
        
        // JavaScript 브리지 설정
        browser.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: org.cef.browser.CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    val script = """
                        window.sendToKotlin = function(data) {
                            ${jsQuery.inject("JSON.stringify(data)")}
                        };
                    """.trimIndent()
                    
                    browser?.executeJavaScript(script, browser.url, 0)
                    logger.info("✅ JavaScript bridge initialized")
                }
            }
        }, browser.cefBrowser)
        
        // 터미널 서비스 출력 리스너 등록
        terminalService.setBridge(object : TerminalOutputBridge {
            override fun pushStdout(text: String) {
                sendToWebView(mapOf(
                    "command" to "terminalOutput",
                    "data" to text,
                    "isError" to false
                ))
            }
            
            override fun pushStderr(text: String) {
                sendToWebView(mapOf(
                    "command" to "terminalOutput",
                    "data" to text,
                    "isError" to true
                ))
            }
            
            override fun onInfo(message: String) {
                sendToWebView(mapOf(
                    "command" to "terminalOutput",
                    "data" to "[INFO] $message\n",
                    "isError" to false
                ))
            }
            
            override fun onError(message: String) {
                sendToWebView(mapOf(
                    "command" to "terminalOutput",
                    "data" to "[ERROR] $message\n",
                    "isError" to true
                ))
            }
        })
        
        logger.info("✅ Message handling setup completed")
    }
    
    private fun handleCreateTerminal() {
        logger.info("🚀 Creating terminal...")
        
        terminalService.initialize(System.getProperty("user.home"))
        isTerminalReady = true
        
        sendToWebView(mapOf(
            "command" to "terminalReady",
            "method" to "ProcessHandler Terminal"
        ))
    }
    
    private fun handleExecuteCommand(commandText: String) {
        logger.info("▶️ Executing command: $commandText")
        terminalService.sendInput(commandText)
    }
    
    private fun handleUserInput(input: String) {
        logger.info("⌨️ User input: $input")
        terminalService.sendInput(input)
    }
    
    private fun handleTerminateTerminal() {
        logger.info("🔄 Terminating terminal...")
        terminalService.kill()
        isTerminalReady = false
        
        sendToWebView(mapOf("command" to "terminalTerminated"))
    }
    
    private fun handleKillProcess() {
        logger.info("⚡ Killing process...")
        terminalService.kill()
    }
    
    private fun handleClearTerminal() {
        logger.info("🧹 Clearing terminal...")
        sendToWebView(mapOf("command" to "clearWebTerminal"))
    }
    
    private fun handleCheckTerminalStatus() {
        logger.info("📊 Checking terminal status...")
        
        sendToWebView(mapOf(
            "command" to "terminalStatus",
            "isActive" to isTerminalReady,
            "isRunning" to isTerminalReady,
            "hasRunningProcess" to isTerminalReady
        ))
    }
    
    private fun sendToWebView(data: Map<String, Any>) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val json = objectMapper.writeValueAsString(data)
                val script = "window.handleMessage && window.handleMessage($json);"
                browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
            } catch (e: Exception) {
                logger.error("❌ Error sending message to WebView", e)
            }
        }
    }
    
    private fun getWebViewContent(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: monospace; background: #2b2b2b; color: #a9b7c6; margin: 20px; }
        .card { background: #3c3f41; border: 1px solid #555; padding: 20px; margin: 20px 0; border-radius: 8px; }
        button { background: #4c5052; color: #a9b7c6; border: 1px solid #555; padding: 10px 20px; margin: 5px; border-radius: 4px; cursor: pointer; }
        button:hover { background: #5c6162; }
        .status { display: inline-block; padding: 4px 8px; border-radius: 12px; font-size: 12px; font-weight: bold; }
        .status.ready { background: #365880; color: white; }
        .status.error { background: #743a3a; color: white; }
        .status.pending { background: #7a6a2c; color: white; }
        .terminal-info { background: #313335; padding: 10px; margin: 10px 0; border-radius: 4px; font-family: monospace; }
        input { width: 70%; padding: 8px; margin: 10px 5px; background: #313335; color: #a9b7c6; border: 1px solid #555; border-radius: 4px; font-family: inherit; }
        .terminal-container { background: #000; border: 1px solid #555; border-radius: 4px; padding: 10px; margin: 10px 0; height: 400px; overflow-y: auto; font-family: monospace; white-space: pre-wrap; }
    </style>
</head>
<body>
    <h1>🚀 IntelliJ WebView Terminal</h1>
    
    <div class="card">
        <h2>📟 터미널 제어</h2>
        <p>ProcessHandler 기반 터미널입니다.</p>
        
        <div>
            <button onclick="createTerminal()">터미널 생성</button>
            <button onclick="terminateTerminal()">터미널 종료</button>
            <button onclick="killProcess()">프로세스 강제종료</button>
            <button onclick="clearTerminal()">터미널 지우기</button>
            <button onclick="checkStatus()">상태 확인</button>
            <span id="terminalStatus" class="status">대기중</span>
        </div>
        
        <div class="terminal-info" id="terminalInfo">
            터미널을 생성하려면 "터미널 생성" 버튼을 클릭하세요.
        </div>
    </div>
    
    <div class="card">
        <h2>🖥️ 터미널 출력</h2>
        <div class="terminal-container" id="terminal"></div>
    </div>
    
    <div class="card">
        <h2>🔧 명령어 실행</h2>
        <div>
            <button onclick="executeCommand('ls -la')">ls -la</button>
            <button onclick="executeCommand('pwd')">pwd</button>
            <button onclick="executeCommand('whoami')">whoami</button>
            <button onclick="executeCommand('date')">날짜</button>
        </div>
        
        <div style="margin-top: 15px;">
            <input id="customCommand" placeholder="사용자 정의 명령어 입력" onkeypress="handleCommandKey(event)">
            <button onclick="executeCustomCommand()">실행</button>
        </div>
    </div>

    <script>
        let terminalReady = false;
        
        function createTerminal() {
            sendToKotlin({ command: 'createTerminal' });
            updateStatus('생성중...', 'pending');
        }
        
        function executeCommand(cmd) {
            if (!cmd.trim()) return;
            sendToKotlin({ command: 'executeCommand', commandText: cmd });
            appendToTerminal('$ ' + cmd + '\\n');
        }
        
        function executeCustomCommand() {
            const input = document.getElementById('customCommand');
            const cmd = input.value.trim();
            if (cmd) {
                executeCommand(cmd);
                input.value = '';
            }
        }
        
        function handleCommandKey(event) {
            if (event.key === 'Enter') {
                executeCustomCommand();
            }
        }
        
        function clearTerminal() {
            document.getElementById('terminal').innerHTML = '';
            sendToKotlin({ command: 'clearTerminal' });
        }
        
        function terminateTerminal() {
            sendToKotlin({ command: 'terminateTerminal' });
            updateStatus('종료중...', 'pending');
            terminalReady = false;
        }
        
        function killProcess() {
            sendToKotlin({ command: 'killProcess' });
        }
        
        function checkStatus() {
            sendToKotlin({ command: 'checkTerminalStatus' });
        }
        
        function updateStatus(message, type) {
            const statusEl = document.getElementById('terminalStatus');
            statusEl.textContent = message;
            statusEl.className = 'status ' + type;
        }
        
        function appendToTerminal(text) {
            const terminal = document.getElementById('terminal');
            terminal.innerHTML += text;
            terminal.scrollTop = terminal.scrollHeight;
        }
        
        // Kotlin에서 JavaScript로 메시지를 받는 핸들러
        window.handleMessage = function(data) {
            console.log('Received from Kotlin:', data);
            
            switch (data.command) {
                case 'terminalReady':
                    terminalReady = true;
                    updateStatus('활성', 'ready');
                    document.getElementById('terminalInfo').innerHTML = 
                        '✅ 터미널이 생성되었습니다!<br/>' +
                        '방법: ' + data.method;
                    break;
                    
                case 'terminalOutput':
                    appendToTerminal(data.data);
                    break;
                    
                case 'terminalTerminated':
                    terminalReady = false;
                    updateStatus('종료됨', 'error');
                    document.getElementById('terminalInfo').innerHTML = 
                        '🔄 터미널이 완전히 종료되었습니다.';
                    break;
                    
                case 'clearWebTerminal':
                    document.getElementById('terminal').innerHTML = '';
                    break;
                    
                case 'terminalStatus':
                    updateStatus(data.isActive ? '활성' : '비활성', data.isActive ? 'ready' : 'error');
                    break;
            }
        };
        
        function sendToKotlin(data) {
            if (window.sendToKotlin) {
                window.sendToKotlin(data);
            } else {
                console.error('Kotlin bridge not available');
            }
        }
        
        // 페이지 로드 시 터미널 초기화
        document.addEventListener('DOMContentLoaded', () => {
            setTimeout(() => {
                createTerminal();
            }, 1000);
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}