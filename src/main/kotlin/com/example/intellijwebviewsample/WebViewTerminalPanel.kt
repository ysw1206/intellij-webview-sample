package com.example.intellijwebviewsample

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.JComponent
import javax.swing.JPanel

class WebViewTerminalPanel(private val project: Project) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val terminalService = project.service<TerminalProcessService>()
    private val browser = JBCefBrowser()
    private val panel = JPanel(BorderLayout())
    @Volatile private var ready = false

    init {
        setupWebView()
        setupBridge()
        panel.add(browser.component, BorderLayout.CENTER)

        // Kotlin → JS 출력 브리지
        terminalService.setBridge(object : TerminalOutputBridge {
            override fun pushStdout(text: String) = sendWrite(text)
            override fun pushStderr(text: String) = sendWrite(text) // 단순화: 색 입히려면 ANSI 추가
        })
    }

    fun getComponent(): JComponent = panel

    private fun setupWebView() {
        browser.loadHTML(xtermHtml())
    }

    private fun setupBridge() {
        val jsQuery = JBCefJSQuery.create(browser)

        jsQuery.addHandler { raw ->
            try {
                val msg: Map<String, Any?> = objectMapper.readValue(raw)
                when (msg["command"]) {
                    "ping" -> {
                        println("🏓 브리지 테스트: ${msg["test"]}")
                    }
                    "terminalReady" -> {
                        println("🚀 Terminal Ready 요청")
                        ready = terminalService.initialize(System.getProperty("user.home"))
                        println("🎯 Terminal 초기화 결과: $ready")
                    }
                    "userInput" -> {
                        val input = (msg["input"] as? String) ?: return@addHandler JBCefJSQuery.Response("ok")
                        println("⌨️ 사용자 입력: '$input'")
                        if (ready) terminalService.sendInput(input)
                    }
                    "exec" -> {  // 새로 추가!
                        val cmd = (msg["cmd"] as? String) ?: return@addHandler JBCefJSQuery.Response("ok")
                        println("🔥 명령어 실행 요청: '$cmd'")
                        if (ready) {
                            terminalService.sendInput("$cmd\n")
                            println("✅ 명령어 전송 완료: '$cmd'")
                        } else {
                            println("❌ 터미널이 준비되지 않음")
                        }
                    }
                    "resize" -> {
                        val cols = (msg["cols"] as Number).toInt()
                        val rows = (msg["rows"] as Number).toInt()
                        if (ready) terminalService.resize(cols, rows)
                    }
                    "clear" -> terminalService.clear()
                    "kill"  -> { terminalService.kill(); ready = false }
                    else -> {
                        println("❓ 알 수 없는 명령어: ${msg["command"]}")
                    }
                }
                JBCefJSQuery.Response("ok")
            } catch (t: Throwable) {
                JBCefJSQuery.Response(null, 0, t.message ?: "error")
            }
        }

        // JS에서 Kotlin 호출 함수 주입
        browser.jbCefClient.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: org.cef.browser.CefBrowser?, f: org.cef.browser.CefFrame?, code: Int) {
                if (f?.isMain == true) {
                    val hook = """
                        window.sendToKotlin = function(data){
                          ${jsQuery.inject("JSON.stringify(data)")}
                        };
                    """.trimIndent()
                    b?.executeJavaScript(hook, b.url, 0)
                }
            }
        }, browser.cefBrowser)
    }

    private fun sendWrite(text: String) {
        // 안전하게 보내기 위해 base64로 감싸 JS에서 atob → UTF-8 decode
        val b64 = Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
        val script = "window.handleKotlinMessage && window.handleKotlinMessage({action:'write-b64', data:'$b64'});"
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        }
    }

    private fun xtermHtml(): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>PTY Terminal</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css">
<style>
  html,body { height:100%; margin:0; background:#1e1e1e; color:#fff; font-family:monospace; }
  #toolbar { padding:8px; background:#2d2d2d; border-bottom:1px solid #444; }
  #toolbar button { margin:4px; padding:6px 12px; background:#444; color:#fff; border:1px solid #666; border-radius:4px; cursor:pointer; }
  #toolbar button:hover { background:#555; }
  #terminal { position:absolute; top:60px; left:12px; right:12px; bottom:12px; border:1px solid #444; border-radius:6px; }
</style>
</head>
<body>
<div id="toolbar">
    <strong>🔧 Debug:</strong>
    <button onclick="manualInit()">수동 터미널 초기화</button>
    <button onclick="checkBridge()">브리지 상태 확인</button>
    |
    <strong>📋 Quick Test:</strong>
    <button onclick="runTop()">top (interactive)</button>
    <button onclick="runTopOnce()">top once</button>
    <button onclick="quitTop()">send 'q'</button>
    <button onclick="sendEnter()">Enter</button>
</div>
<div id="terminal"></div>
<script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
<script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js"></script>
<script>
  let term, fitAddon;

  // Kotlin → JS
  window.handleKotlinMessage = (msg) => {
    if (!term) return;
    if (msg.action === 'write-b64') {
      // base64 → utf8 문자열
      const bin = atob(msg.data);
      const utf8 = decodeURIComponent(Array.prototype.map.call(bin, c => {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      term.write(utf8);
    }
  };

  // JS → Kotlin (JCEF에서 주입)
  window.sendToKotlin = window.sendToKotlin || function(_) { console.warn('bridge not ready'); };

  // 🔧 디버그 함수들
  function manualInit() {
    console.log('🔧 수동 터미널 초기화 시도');
    if (window.sendToKotlin) {
        window.sendToKotlin({ command:'terminalReady' });
        console.log('✅ terminalReady 신호 전송');
    } else {
        console.error('❌ Kotlin 브리지 없음');
    }
  }

  function checkBridge() {
    console.log('🔍 브리지 상태:', {
        sendToKotlin: !!window.sendToKotlin,
        handleKotlinMessage: !!window.handleKotlinMessage,
        term: !!term
    });
    // 브리지 테스트
    if (window.sendToKotlin) {
        window.sendToKotlin({ command:'ping', test: 'bridge-test' });
    }
  }

  function init() {
      console.log('🚀 터미널 초기화 시작');
      term = new Terminal({
        cursorBlink: true,
        convertEol: true,
        fontSize: 13,
        theme: { background: '#000000' },
      });
      fitAddon = new window.FitAddon.FitAddon();
      term.loadAddon(fitAddon);
      term.open(document.getElementById('terminal'));
      fitAddon.fit();
    
      // 🔹 포커스 강제
      term.focus();
      document.getElementById('terminal').addEventListener('mousedown', () => term.focus());
    
      // 디버그: 실제로 키가 들어오는지 확인
      term.onKey(e => console.log('[xterm key]', e.key, e.domEvent.code));
      term.onData(data => {
        console.log('[xterm data]', data, data.charCodeAt(0));
        // 🔹 입력을 Kotlin으로 전달
        window.sendToKotlin && window.sendToKotlin({ command:'userInput', input:data });
      });
    
      term.onResize(({cols, rows}) => {
        console.log('[xterm resize]', cols, rows);
        window.sendToKotlin && window.sendToKotlin({ command:'resize', cols, rows });
      });
    
      term.write('\\x1b[33mPTY terminal ready. 터미널 UI가 로드되었습니다.\\x1b[0m\\r\\n');
      term.write('\\x1b[36m"수동 터미널 초기화" 버튼을 클릭하거나 자동 초기화를 기다려주세요.\\x1b[0m\\r\\n');
      
      console.log('✅ 터미널 UI 초기화 완료');
    }

  // 🔹 자동 브리지 연결 + 초기화
  function tryAutoInit() {
      console.log('🔄 자동 초기화 시도...');
      if (window.sendToKotlin && typeof window.sendToKotlin === 'function') {
          console.log('✅ 브리지 연결됨, terminalReady 신호 전송');
          window.sendToKotlin({ command:'terminalReady' });
          window.sendToKotlin({ command:'resize', cols: term.cols, rows: term.rows });
          return true;
      }
      console.log('⏳ 브리지 대기 중...');
      return false;
  }

  // 나머지 함수들...
  function runTop() {
    console.log('🔥 top 명령어 실행');
    window.sendToKotlin({ command:'exec', cmd:'top' });
  }
  function runTopOnce() {
    console.log('🔥 top -l 1 명령어 실행');
    const cmd = /Mac/.test(navigator.platform) ? 'top -l 1' : 'top -b -n 1';
    window.sendToKotlin({ command:'exec', cmd });
  }
  function quitTop() {
    console.log('🛑 q 전송 (top 종료)');
    window.sendToKotlin({ command:'userInput', input:'q' });
  }
  function sendEnter() {
    console.log('↩️ Enter 전송');
    window.sendToKotlin({ command:'userInput', input:'\\r' });
  }

  // 🚀 DOM 로드 후 초기화
  document.addEventListener('DOMContentLoaded', () => {
    console.log('📄 DOM 로드 완료');
    setTimeout(() => {
      init();
      // 자동 초기화 시도 (100ms 간격으로 최대 5초)
      let attempts = 0;
      const autoInitTimer = setInterval(() => {
        attempts++;
        if (tryAutoInit() || attempts > 50) {
          clearInterval(autoInitTimer);
        }
      }, 100);
    }, 100);
  });
</script>
</body>
</html>
""".trimIndent()
}
