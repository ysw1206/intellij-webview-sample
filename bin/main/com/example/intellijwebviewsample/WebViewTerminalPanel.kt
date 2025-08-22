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
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * WebView Terminal Panel
 * VS Code Extension의 webview와 유사한 기능을 제공합니다.
 * JCEF (Java Chromium Embedded Framework)를 사용하여 웹 기반 터미널 UI를 구현합니다.
 */
class WebViewTerminalPanel(private val project: Project) {
    
    private val logger = thisLogger()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val terminalService = project.service<TerminalService>()
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
        
        // HTML 콘텐츠 로드
        browser.loadHTML(getWebViewContent())
        
        logger.info("✅ WebView setup completed")
    }
    
    private fun setupMessageHandling() {
        logger.info("🔧 Setting up message handling...")
        
        // JavaScript에서 Kotlin으로 메시지를 보내기 위한 쿼리 핸들러
        val jsQuery = JBCefJSQuery.create(browser)
        
        jsQuery.addHandler { query ->
            try {
                logger.info("📨 Received message from WebView: $query")
                
                val message = objectMapper.readValue<Map<String, Any>>(query)
                val command = message["command"] as? String
                
                when (command) {
                    "createTerminal" -> {
                        handleCreateTerminal()
                    }
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
                    "terminateTerminal" -> {
                        handleTerminateTerminal()
                    }
                    "killProcess" -> {
                        handleKillProcess()
                    }
                    "clearTerminal" -> {
                        handleClearTerminal()
                    }
                    "checkTerminalStatus" -> {
                        handleCheckTerminalStatus()
                    }
                    else -> {
                        logger.warn("❓ Unknown command: $command")
                    }
                }
                
                JBCefJSQuery.Response("ok")
            } catch (e: Exception) {
                logger.error("❌ Error handling message from WebView", e)
                JBCefJSQuery.Response(null, 0, "Error: ${e.message}")
            }
        }
        
        // JavaScript에서 사용할 수 있도록 전역 함수 등록
        browser.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: org.cef.browser.CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    // JavaScript에서 Kotlin 함수를 호출할 수 있도록 전역 함수 등록
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
        terminalService.addOutputListener("webview") { output, isError ->
            sendToWebView(mapOf(
                "command" to "terminalOutput",
                "data" to output,
                "isError" to isError
            ))
        }
        
        logger.info("✅ Message handling setup completed")
    }
    
    private fun handleCreateTerminal() {
        logger.info("🚀 Creating terminal...")
        
        val success = terminalService.initializeTerminal()
        if (success) {
            isTerminalReady = true
            sendToWebView(mapOf(
                "command" to "terminalReady",
                "method" to "IntelliJ Process Handler"
            ))
        } else {
            sendToWebView(mapOf(
                "command" to "terminalError",
                "error" to "Failed to create terminal"
            ))
        }
    }
    
    private fun handleExecuteCommand(commandText: String) {
        logger.info("▶️ Executing command: $commandText")
        
        if (!isTerminalReady) {
            terminalService.initializeTerminal()
            isTerminalReady = true
        }
        
        terminalService.executeCommand(commandText)
    }
    
    private fun handleUserInput(input: String) {
        logger.info("⌨️ User input: $input")
        handleExecuteCommand(input)
    }
    
    private fun handleTerminateTerminal() {
        logger.info("🔄 Terminating terminal...")
        
        terminalService.terminateTerminal()
        isTerminalReady = false
        
        sendToWebView(mapOf(
            "command" to "terminalTerminated"
        ))
    }
    
    private fun handleKillProcess() {
        logger.info("⚡ Killing current process...")
        terminalService.killCurrentProcess()
    }
    
    private fun handleClearTerminal() {
        logger.info("🧹 Clearing terminal...")
        terminalService.clearTerminal()
        
        sendToWebView(mapOf(
            "command" to "clearWebTerminal"
        ))
    }
    
    private fun handleCheckTerminalStatus() {
        logger.info("📊 Checking terminal status...")
        
        val status = terminalService.getTerminalStatus()
        sendToWebView(mapOf(
            "command" to "terminalStatus",
            "isActive" to (status["isActive"] ?: false),
            "hasRunningProcess" to (status["hasRunningProcess"] ?: false),
            "currentDirectory" to (status["currentDirectory"] ?: "")
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
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css" />
    <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js"></script>
    <style>
        body { 
            font-family: 'JetBrains Mono', 'Consolas', monospace;
            background: #2b2b2b;
            color: #a9b7c6;
            margin: 0;
            padding: 20px;
        }
        .card {
            background: #3c3f41;
            border: 1px solid #555555;
            padding: 20px;
            margin: 20px 0;
            border-radius: 8px;
        }
        button {
            background: #4c5052;
            color: #a9b7c6;
            border: 1px solid #555555;
            padding: 10px 20px;
            margin: 5px;
            border-radius: 4px;
            cursor: pointer;
            font-family: inherit;
        }
        button:hover {
            background: #5c6162;
        }
        button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        .terminal-container {
            background: #000;
            border: 1px solid #555555;
            border-radius: 4px;
            padding: 10px;
            margin: 10px 0;
            height: 400px;
            overflow: hidden;
        }
        .terminal-info {
            background: #313335;
            padding: 10px;
            margin: 10px 0;
            border-radius: 4px;
            font-family: monospace;
        }
        input {
            width: 70%;
            padding: 8px;
            margin: 10px 5px;
            background: #313335;
            color: #a9b7c6;
            border: 1px solid #555555;
            border-radius: 4px;
            font-family: inherit;
        }
        .status {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
        }
        .status.ready {
            background: #365880;
            color: white;
        }
        .status.error {
            background: #743a3a;
            color: white;
        }
        .status.pending {
            background: #7a6a2c;
            color: white;
        }
        .button-group {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;
            margin: 10px 0;
        }
        .terminal-controls {
            margin-bottom: 10px;
        }
        #terminal {
            height: 100%;
            width: 100%;
        }
        .intellij-mode {
            background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
            color: white;
            padding: 15px;
            border-radius: 8px;
            margin: 10px 0;
            text-align: center;
        }
        .danger-btn {
            background: #743a3a !important;
            color: white !important;
        }
        .danger-btn:hover {
            background: #8b4a4a !important;
        }
        .warning-btn {
            background: #7a6a2c !important;
            color: white !important;
        }
        .warning-btn:hover {
            background: #8b7a3c !important;
        }
    </style>
</head>
<body>
    <h1>🚀 IntelliJ WebView Terminal</h1>
    
    <div class="intellij-mode">
        <strong>🎯 IntelliJ + Process Handler 모드</strong><br>
        VS Code Pseudoterminal과 동일한 기능을 IntelliJ에서 구현했습니다!
    </div>
    
    <div class="card">
        <h2>📟 Process Handler 제어</h2>
        <p>IntelliJ의 ProcessHandler를 사용한 실제 쉘 프로세스 제어</p>
        
        <div class="terminal-controls">
            <button onclick="createTerminal()">터미널 생성</button>
            <button onclick="terminateTerminal()" id="terminateBtn" class="danger-btn">터미널 종료</button>
            <button onclick="killProcess()" id="killBtn" class="warning-btn">프로세스 강제종료</button>
            <button onclick="clearTerminal()" id="clearBtn">터미널 지우기</button>
            <button onclick="checkStatus()" id="statusBtn">상태 확인</button>
            <span id="terminalStatus" class="status">대기중</span>
        </div>
        
        <div class="terminal-info" id="terminalInfo">
            IntelliJ ProcessHandler를 사용하여 실제 쉘 프로세스를 생성합니다.<br/>
            VS Code Pseudoterminal과 동일한 방식으로 동작합니다!
        </div>
    </div>
    
    <div class="card">
        <h2>🖥️ xterm.js 터미널</h2>
        <div class="terminal-container">
            <div id="terminal"></div>
        </div>
    </div>
    
    <div class="card">
        <h2>🔧 빠른 명령어</h2>
        <p>실제 쉘 세션에서 실행되므로 cd, export 등이 다음 명령어에도 유지됩니다!</p>
        <div class="button-group">
            <button onclick="executeCommand('ls -la')" class="cmd-btn">ls -la</button>
            <button onclick="executeCommand('pwd')" class="cmd-btn">pwd</button>
            <button onclick="executeCommand('whoami')" class="cmd-btn">whoami</button>
            <button onclick="executeCommand('cd /tmp')" class="cmd-btn">cd /tmp</button>
            <button onclick="executeCommand('pwd')" class="cmd-btn">pwd (다시)</button>
            <button onclick="executeCommand('export TEST=hello')" class="cmd-btn">export TEST=hello</button>
            <button onclick="executeCommand('echo ${'$'}TEST')" class="cmd-btn">echo ${'$'}TEST</button>
            <button onclick="executeCommand('date')" class="cmd-btn">날짜</button>
            <button onclick="executeCommand('java -version')" class="cmd-btn">Java 버전</button>
            <button onclick="executeCommand('gradle --version')" class="cmd-btn">Gradle 버전</button>
        </div>
        
        <div style="margin-top: 15px;">
            <input id="customCommand" placeholder="사용자 정의 명령어 입력" onkeypress="handleCommandKey(event)">
            <button onclick="executeCustomCommand()">실행</button>
        </div>
        
        <div style="margin-top: 10px;">
            <small>💡 IntelliJ ProcessHandler + JCEF WebView로 구현된 터미널입니다!</small>
        </div>
    </div>

    <script>
        let terminalReady = false;
        let term;
        let fitAddon;
        let currentLine = '';
        let commandHistory = [];
        let historyIndex = -1;
        
        // xterm.js 터미널 초기화
        function initTerminal() {
            if (term) {
                term.dispose();
            }
            
            term = new Terminal({
                cursorBlink: true,
                fontSize: 14,
                fontFamily: 'JetBrains Mono, Consolas, monospace',
                theme: {
                    background: '#1e1e1e',
                    foreground: '#a9b7c6',
                    cursor: '#a9b7c6',
                    selection: '#214283',
                    black: '#000000',
                    red: '#ff6b68',
                    green: '#a8c023',
                    yellow: '#f77669',
                    blue: '#6897bb',
                    magenta: '#cc7832',
                    cyan: '#629755',
                    white: '#a9b7c6',
                    brightBlack: '#555555',
                    brightRed: '#ff8785',
                    brightGreen: '#bcd42a',
                    brightYellow: '#ffc66d',
                    brightBlue: '#9cc7ea',
                    brightMagenta: '#ff9137',
                    brightCyan: '#7eb369',
                    brightWhite: '#ffffff'
                }
            });
            
            // Fit addon 초기화
            fitAddon = new FitAddon.FitAddon();
            term.loadAddon(fitAddon);
            
            // 터미널을 DOM에 연결
            term.open(document.getElementById('terminal'));
            fitAddon.fit();
            
            // 터미널 리사이즈 처리
            window.addEventListener('resize', () => {
                if (fitAddon) {
                    fitAddon.fit();
                }
            });
            
            // 사용자 입력 처리
            term.onData(data => {
                const code = data.charCodeAt(0);
                
                // Ctrl+C 처리 (프로세스 중단)
                if (code === 3) { // Ctrl+C
                    sendToKotlin({
                        command: 'killProcess'
                    });
                    term.write('^C\\r\\n\$ ');
                    currentLine = '';
                    return;
                }
                
                if (code === 13) { // Enter
                    if (currentLine.trim()) {
                        console.log('Sending user input:', currentLine.trim());
                        
                        // 명령어 실행
                        commandHistory.push(currentLine.trim());
                        historyIndex = commandHistory.length;
                        
                        term.write('\\r\\n');
                        sendToKotlin({
                            command: 'userInput',
                            input: currentLine.trim()
                        });
                        currentLine = '';
                    } else {
                        term.write('\\r\\n\$ ');
                    }
                } else if (code === 127) { // Backspace
                    if (currentLine.length > 0) {
                        currentLine = currentLine.slice(0, -1);
                        term.write('\\b \\b');
                    }
                } else if (code === 27) { // Escape sequences (화살표 키 등)
                    if (data.length === 3) {
                        if (data[2] === 'A') { // Up arrow
                            if (historyIndex > 0) {
                                term.write('\\r\$ ');
                                term.write(' '.repeat(currentLine.length));
                                term.write('\\r\$ ');
                                
                                historyIndex--;
                                currentLine = commandHistory[historyIndex];
                                term.write(currentLine);
                            }
                        } else if (data[2] === 'B') { // Down arrow
                            if (historyIndex < commandHistory.length - 1) {
                                term.write('\\r\$ ');
                                term.write(' '.repeat(currentLine.length));
                                term.write('\\r\$ ');
                                
                                historyIndex++;
                                currentLine = commandHistory[historyIndex];
                                term.write(currentLine);
                            } else if (historyIndex === commandHistory.length - 1) {
                                term.write('\\r\$ ');
                                term.write(' '.repeat(currentLine.length));
                                term.write('\\r\$ ');
                                
                                historyIndex = commandHistory.length;
                                currentLine = '';
                            }
                        }
                    }
                } else if (code >= 32) { // 일반 문자
                    currentLine += data;
                    term.write(data);
                }
            });
            
            // 초기 프롬프트 표시
            term.write('🎯 IntelliJ xterm.js 터미널이 준비되었습니다!\\r\\n\$ ');
        }
        
        function createTerminal() {
            initTerminal();
            sendToKotlin({ command: 'createTerminal' });
            updateStatus('생성중...', 'pending');
        }
        
        function executeCommand(cmd) {
            if (!cmd.trim()) return;
            
            if (term) {
                term.write('\\r\\n> ' + cmd + '\\r\\n');
                currentLine = '';
            }
            
            sendToKotlin({ 
                command: 'executeCommand', 
                commandText: cmd
            });
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
            if (term) {
                term.clear();
                term.write('\$ ');
                currentLine = '';
            }
            sendToKotlin({ command: 'clearTerminal' });
        }
        
        function terminateTerminal() {
            sendToKotlin({ command: 'terminateTerminal' });
            updateStatus('종료중...', 'pending');
            
            if (term) {
                term.clear();
                term.write('🔄 터미널을 종료하고 있습니다...');
            }
            
            commandHistory = [];
            historyIndex = -1;
            currentLine = '';
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
        
        // Kotlin에서 JavaScript로 메시지를 받는 핸들러
        window.handleMessage = function(data) {
            console.log('Received from Kotlin:', data);
            
            switch (data.command) {
                case 'terminalReady':
                    terminalReady = true;
                    updateStatus('활성', 'ready');
                    document.getElementById('terminalInfo').innerHTML = 
                        '✅ IntelliJ ProcessHandler가 생성되었습니다! (' + data.method + ')<br/>' +
                        'VS Code Pseudoterminal과 동일한 방식으로 동작합니다!';
                    break;
                    
                case 'terminalError':
                    updateStatus('오류', 'error');
                    document.getElementById('terminalInfo').innerHTML = 
                        '❌ 터미널 오류: ' + data.error;
                    if (term) {
                        term.write('\\r\\n\\x1b[31m❌ 오류: ' + data.error + '\\x1b[0m\\r\\n\$ ');
                    }
                    break;
                    
                case 'terminalOutput':
                    if (term) {
                        term.write(data.data);
                    }
                    break;
                    
                case 'terminalTerminated':
                    terminalReady = false;
                    updateStatus('종료됨', 'error');
                    document.getElementById('terminalInfo').innerHTML = 
                        '🔄 터미널이 완전히 종료되었습니다.<br/>' +
                        '새 터미널을 생성하려면 "터미널 생성" 버튼을 클릭하세요.';
                    
                    if (term) {
                        term.clear();
                        term.write('\\r\\n\\x1b[32m✅ 터미널이 성공적으로 종료되었습니다.\\x1b[0m\\r\\n');
                        term.write('\\x1b[36m새 터미널을 시작하려면 "터미널 생성" 버튼을 클릭하세요.\\x1b[0m\\r\\n');
                    }
                    break;
                    
                case 'clearWebTerminal':
                    if (term) {
                        term.clear();
                        term.write('\$ ');
                        currentLine = '';
                    }
                    break;
                    
                case 'terminalStatus':
                    updateStatus(data.isActive ? '활성' : '비활성', data.isActive ? 'ready' : 'error');
                    document.getElementById('terminalInfo').innerHTML = 
                        '📊 터미널 상태: ' + (data.isActive ? '활성' : '비활성') + '<br/>' +
                        '실행 중인 프로세스: ' + (data.hasRunningProcess ? '있음' : '없음') + '<br/>' +
                        '현재 디렉토리: ' + data.currentDirectory;
                    break;
            }
        };
        
        // Kotlin으로 메시지 보내는 함수 (window.sendToKotlin은 Kotlin에서 주입됨)
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
            }, 1000); // IntelliJ JCEF 초기화를 위해 약간의 지연
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}
