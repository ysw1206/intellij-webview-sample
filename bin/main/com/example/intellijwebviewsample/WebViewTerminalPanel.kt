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
        logger.info("🔧 Setting up xterm.js WebView...")
        browser.loadHTML(getXtermTerminalContent())
        logger.info("✅ xterm.js WebView setup completed")
    }
    
    private fun setupMessageHandling() {
        logger.info("🔧 Setting up xterm.js message handling...")
        
        val jsQuery = JBCefJSQuery.create(browser)
        
        jsQuery.addHandler { query ->
            try {
                logger.info("📨 Received message: $query")
                
                val message = objectMapper.readValue<Map<String, Any>>(query)
                val command = message["command"] as? String
                
                ApplicationManager.getApplication().invokeLater {
                    when (command) {
                        "terminalReady" -> handleTerminalReady()
                        "userInput" -> {
                            val input = message["input"] as? String
                            if (input != null) {
                                handleUserInput(input)
                            }
                        }
                        "testCommand" -> {
                            val cmd = message["cmd"] as? String
                            if (cmd != null) {
                                handleTestCommand(cmd)
                            }
                        }
                        "clear" -> handleClear()
                        "kill" -> handleKill()
                        else -> logger.warn("❓ Unknown command: $command")
                    }
                }
                
                JBCefJSQuery.Response("ok")
            } catch (e: Exception) {
                logger.error("❌ Error handling message", e)
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
                    logger.info("✅ xterm.js JavaScript bridge initialized")
                }
            }
        }, browser.cefBrowser)
        
        // 터미널 서비스 출력 리스너 등록
        terminalService.setBridge(object : TerminalOutputBridge {
            override fun pushStdout(text: String) {
                logger.info("📤 STDOUT: ${text.replace("\r", "\\r").replace("\n", "\\n")}")
                sendToTerminal("write", text)
            }
            
            override fun pushStderr(text: String) {
                logger.info("📤 STDERR: ${text.replace("\r", "\\r").replace("\n", "\\n")}")
                sendToTerminal("write", "\u001b[31m$text\u001b[0m") // 빨간색
            }
            
            override fun onInfo(message: String) {
                logger.info("📤 INFO: $message")
                sendToTerminal("write", "\u001b[32m[INFO] $message\u001b[0m\r\n") // 녹색
            }
            
            override fun onError(message: String) {
                logger.error("📤 ERROR: $message")
                sendToTerminal("write", "\u001b[31m[ERROR] $message\u001b[0m\r\n") // 빨간색
            }
        })
        
        logger.info("✅ xterm.js message handling setup completed")
    }
    
    private fun handleTerminalReady() {
        logger.info("🚀 xterm.js terminal ready, initializing enhanced test terminal...")
        
        sendToTerminal("write", "\u001b[33m🔄 향상된 테스트 터미널을 초기화하는 중...\u001b[0m\r\n")
        
        val success = terminalService.initialize(System.getProperty("user.home"))
        if (success) {
            isTerminalReady = true
            sendToTerminal("write", "\u001b[32m🎯 향상된 테스트 터미널이 준비되었습니다!\u001b[0m\r\n")
            sendToTerminal("write", "\u001b[36m명령어를 입력하세요! (help를 입력하면 사용 가능한 명령어를 볼 수 있습니다)\u001b[0m\r\n")
            sendToTerminal("write", "$ ")
        } else {
            sendToTerminal("write", "\u001b[31m❌ 터미널 초기화 실패\u001b[0m\r\n")
        }
    }
    
    private fun handleUserInput(input: String) {
        logger.info("⌨️ Raw user input: '${input}' (length: ${input.length}) (charCodes: ${input.map { it.code }})")
        
        if (!isTerminalReady) {
            sendToTerminal("write", "\u001b[31m터미널이 준비되지 않았습니다.\u001b[0m\r\n")
            return
        }
        
        // 입력을 그대로 터미널 서비스로 전달 - 로깅 추가
        logger.info("🔄 Sending to TerminalService: '$input'")
        terminalService.sendInput(input)
    }
    
    private fun handleTestCommand(cmd: String) {
        logger.info("🧪 Test command (direct): $cmd")
        
        if (!isTerminalReady) {
            sendToTerminal("write", "\u001b[31m터미널이 준비되지 않았습니다.\u001b[0m\r\n")
            return
        }
        
        // 테스트 명령어는 직접 처리
        logger.info("🔄 Sending test command to TerminalService: '$cmd'")
        terminalService.sendInput(cmd + "\n")  // 개행 문자 추가
    }
    
    private fun handleClear() {
        sendToTerminal("clear", "")
        terminalService.clear()
    }
    
    private fun handleKill() {
        terminalService.kill()
        isTerminalReady = false
        sendToTerminal("write", "\u001b[31m터미널이 종료되었습니다.\u001b[0m\r\n")
    }
    
    private fun sendToTerminal(action: String, data: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val message = mapOf("action" to action, "data" to data)
                val json = objectMapper.writeValueAsString(message)
                val script = "window.handleKotlinMessage && window.handleKotlinMessage($json);"
                browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
            } catch (e: Exception) {
                logger.error("❌ Error sending to terminal", e)
            }
        }
    }
    
    private fun getXtermTerminalContent(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>xterm.js Terminal</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css" />
    <style>
        body {
            margin: 0;
            padding: 20px;
            background: #1e1e1e;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
        }
        
        .header {
            color: #a9b7c6;
            margin-bottom: 15px;
            font-size: 16px;
        }
        
        .controls {
            margin-bottom: 15px;
        }
        
        .controls button {
            background: #4c5052;
            color: #a9b7c6;
            border: 1px solid #555;
            padding: 8px 16px;
            margin-right: 10px;
            margin-bottom: 5px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        }
        
        .controls button:hover {
            background: #5c6162;
        }
        
        .controls button.test {
            background: #2d5a2d;
        }
        
        .controls button.test:hover {
            background: #3d6a3d;
        }
        
        #terminal {
            width: 100%;
            height: 400px;
            background: #000;
            border: 1px solid #555;
            border-radius: 4px;
        }
        
        .status {
            background: #2d3142;
            padding: 8px;
            margin: 10px 0;
            border-radius: 4px;
            color: #a9b7c6;
            font-size: 12px;
        }
        
        .info {
            color: #6c7b7f;
            font-size: 11px;
            margin-top: 10px;
        }
    </style>
</head>
<body>
    <div class="header">
        🚀 <strong>xterm.js 테스트 터미널</strong>
    </div>
    
    <div class="status" id="status">
        상태: 터미널 로딩 중...
    </div>
    
    <div class="controls">
        <button onclick="clearTerminal()">Clear</button>
        <button onclick="killProcess()">Kill</button>
        <button onclick="restartTerminal()">Restart</button>
    </div>
    
    <div class="controls">
        <strong>테스트 명령어:</strong>
        <button class="test" onclick="testCommand('pwd')">📁 pwd</button>
        <button class="test" onclick="testCommand('whoami')">👤 whoami</button>
        <button class="test" onclick="testCommand('date')">📅 date</button>
        <button class="test" onclick="testCommand('ls -la')">📋 ls -la</button>
        <button class="test" onclick="testCommand('echo Hello World')">💬 echo</button>
    </div>
    
    <div id="terminal"></div>
    
    <div class="info">
        💡 터미널에서 직접 입력하거나 위의 테스트 버튼을 클릭하세요. 엔터를 눌러야 명령어가 실행됩니다.
    </div>

    <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js"></script>
    
    <script>
        console.log('xterm.js loading...');
        
        let term;
        let fitAddon;
        let currentLine = ''; // 현재 입력 중인 라인
        
        function updateStatus(message) {
            const statusEl = document.getElementById('status');
            const timestamp = new Date().toLocaleTimeString();
            statusEl.textContent = '[' + timestamp + '] ' + message;
            console.log('Status:', message);
        }
        
        function initTerminal() {
            try {
                console.log('Initializing xterm.js...');
                updateStatus('xterm.js 초기화 중...');
                
                // Terminal 생성
                term = new Terminal({
                    cursorBlink: true,
                    theme: {
                        background: '#000000',
                        foreground: '#ffffff',
                        cursor: '#ffffff',
                        selection: '#ffffff40'
                    },
                    fontSize: 14,
                    fontFamily: 'Consolas, Monaco, "Courier New", monospace',
                    rows: 25,
                    cols: 80
                });
                
                // FitAddon 생성
                try {
                    if (window.FitAddon) {
                        fitAddon = new window.FitAddon.FitAddon();
                    } else {
                        fitAddon = null;
                    }
                    
                    if (fitAddon) {
                        term.loadAddon(fitAddon);
                        console.log('FitAddon loaded successfully');
                    }
                } catch (fitError) {
                    console.warn('FitAddon failed to load:', fitError);
                    fitAddon = null;
                }
                
                // DOM에 연결
                term.open(document.getElementById('terminal'));
                
                if (fitAddon) {
                    fitAddon.fit();
                } else {
                    const terminalEl = document.getElementById('terminal');
                    const rect = terminalEl.getBoundingClientRect();
                    const cols = Math.floor(rect.width / 9);
                    const rows = Math.floor(rect.height / 17);
                    term.resize(Math.max(80, cols), Math.max(25, rows));
                }
                
                // 사용자 입력 처리 - 수정된 버전
                term.onData((data) => {
                    console.log('*** RAW INPUT ***:', data, 'charCodes:', data.split('').map(c => c.charCodeAt(0)));
                    
                    // 특수 키 처리
                    const charCode = data.charCodeAt(0);
                    
                    if (charCode === 13) { // Enter
                        console.log('*** ENTER PRESSED ***, current line:', "'" + currentLine + "'");
                        handleEnterKey();
                    } else if (charCode === 127 || charCode === 8) { // Backspace/Delete
                        handleBackspace();
                    } else if (charCode === 3) { // Ctrl+C
                        console.log('*** CTRL+C PRESSED ***');
                        sendToKotlin({
                            command: 'userInput',
                            input: '\\u0003'  // Ctrl+C
                        });
                        currentLine = '';
                        term.write('^C\\r\\n$ ');
                    } else if (charCode >= 32 && charCode < 127) { // 일반 문자
                        handleRegularChar(data);
                    } else {
                        console.log('*** IGNORED CHAR ***, code:', charCode);
                    }
                });
                
                // 초기 메시지
                term.write('\\r\\n🚀 xterm.js 터미널이 로드되었습니다.\\r\\n');
                term.write('Kotlin 서비스에 연결 중...\\r\\n');
                
                // 터미널 준비 신호 전송
                setTimeout(() => {
                    sendToKotlin({ command: 'terminalReady' });
                    updateStatus('터미널 준비 완료');
                }, 1000);
                
                console.log('xterm.js initialized successfully');
                updateStatus('xterm.js 초기화 완료');
                
            } catch (e) {
                console.error('Failed to initialize xterm.js:', e);
                updateStatus('xterm.js 초기화 실패: ' + e.message);
                setupFallbackTerminal();
            }
        }
        
        function handleEnterKey() {
            // 명령어 에코 (사용자가 입력한 명령어 표시)
            term.write('\\r\\n');
            
            console.log('*** PROCESSING COMMAND ***: "' + currentLine + '"');
            
            if (currentLine.trim()) {
                // 명령어를 Kotlin으로 전송 (개행 문자 없이)
                const commandToSend = currentLine.trim();
                console.log('*** SENDING TO KOTLIN ***: "' + commandToSend + '"');
                
                sendToKotlin({
                    command: 'userInput',
                    input: commandToSend
                });
            } else {
                // 빈 라인인 경우 새 프롬프트만 표시
                term.write('$ ');
            }
            
            currentLine = '';
        }
        
        function handleBackspace() {
            if (currentLine.length > 0) {
                currentLine = currentLine.slice(0, -1);
                term.write('\\b \\b');
                console.log('*** BACKSPACE, current line now: "' + currentLine + '"');
            }
        }
        
        function handleRegularChar(char) {
            currentLine += char;
            term.write(char); // 로컬 에코
            console.log('*** CHAR ADDED: "' + char + '", current line: "' + currentLine + '"');
        }
        
        function setupFallbackTerminal() {
            console.log('Setting up fallback terminal...');
            updateStatus('폴백 터미널 설정 중...');
            
            document.getElementById('terminal').innerHTML = 
                '<div style="color: #ff6b6b; padding: 20px; background: #2c2c2c; border-radius: 4px;">' +
                '<h3>❌ xterm.js 초기화 실패</h3>' +
                '<p>기본 터미널 모드로 전환됩니다.</p>' +
                '<div id="fallback-output" style="background: #000; color: #0f0; padding: 10px; margin-top: 10px; border-radius: 4px; font-family: monospace;"></div>' +
                '</div>';
                
            window.handleKotlinMessage = function(data) {
                const outputEl = document.getElementById('fallback-output');
                if (outputEl && data.action === 'write') {
                    outputEl.innerHTML += data.data.replace(/\\n/g, '<br>').replace(/\\r/g, '');
                    outputEl.scrollTop = outputEl.scrollHeight;
                }
            };
            
            setTimeout(() => {
                sendToKotlin({ command: 'terminalReady' });
                updateStatus('폴백 터미널 준비 완료');
            }, 1000);
        }
        
        // Kotlin에서 메시지 받기
        window.handleKotlinMessage = function(data) {
            console.log('*** RECEIVED FROM KOTLIN ***:', data);
            
            if (!term) {
                console.warn('Terminal not initialized yet');
                return;
            }
            
            switch (data.action) {
                case 'write':
                    term.write(data.data);
                    break;
                case 'clear':
                    term.clear();
                    currentLine = '';
                    break;
                case 'reset':
                    term.reset();
                    currentLine = '';
                    break;
            }
        };
        
        // 컨트롤 함수들
        function clearTerminal() {
            if (term) {
                term.clear();
                currentLine = '';
            }
            sendToKotlin({ command: 'clear' });
            updateStatus('터미널 클리어됨');
        }
        
        function killProcess() {
            sendToKotlin({ command: 'kill' });
            updateStatus('프로세스 종료 요청');
        }
        
        function restartTerminal() {
            if (term) {
                term.clear();
                currentLine = '';
            }
            sendToKotlin({ command: 'terminalReady' });
            updateStatus('터미널 재시작 중...');
        }
        
        function testCommand(cmd) {
            console.log('*** TEST COMMAND BUTTON ***: ' + cmd);
            
            // 버튼으로 실행할 때는 바로 전송
            sendToKotlin({ 
                command: 'testCommand',
                cmd: cmd 
            });
            updateStatus('테스트 명령어: ' + cmd);
        }
        
        function sendToKotlin(data) {
            if (window.sendToKotlin) {
                console.log('*** SENDING TO KOTLIN ***: ', JSON.stringify(data));
                window.sendToKotlin(data);
            } else {
                console.error('Kotlin bridge not available');
                updateStatus('❌ Kotlin 브리지 사용 불가');
            }
        }
        
        // 윈도우 리사이즈 처리
        window.addEventListener('resize', () => {
            if (fitAddon) {
                try {
                    fitAddon.fit();
                } catch (e) {
                    console.warn('FitAddon resize failed:', e);
                }
            } else if (term) {
                const terminalEl = document.getElementById('terminal');
                if (terminalEl) {
                    const rect = terminalEl.getBoundingClientRect();
                    const cols = Math.floor(rect.width / 9);
                    const rows = Math.floor(rect.height / 17);
                    term.resize(Math.max(80, cols), Math.max(25, rows));
                }
            }
        });
        
        // 페이지 로드 후 초기화
        document.addEventListener('DOMContentLoaded', () => {
            console.log('DOM loaded, initializing terminal...');
            updateStatus('DOM 로드 완료');
            
            if (typeof Terminal === 'undefined') {
                updateStatus('❌ xterm.js 라이브러리 로드 실패');
                console.error('xterm.js library not loaded');
                return;
            }
            
            setTimeout(initTerminal, 500);
        });
    </script>
</body>
</html>
    """.trimIndent()
    }
}