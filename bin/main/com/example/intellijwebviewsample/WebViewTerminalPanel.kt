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
                    "terminalReady" -> {
                        ready = terminalService.initialize(System.getProperty("user.home"))
                    }
                    "userInput" -> {
                        val input = (msg["input"] as? String) ?: return@addHandler JBCefJSQuery.Response("ok")
                        if (ready) terminalService.sendInput(input)
                    }
                    "resize" -> {
                        val cols = (msg["cols"] as Number).toInt()
                        val rows = (msg["rows"] as Number).toInt()
                        if (ready) terminalService.resize(cols, rows)
                    }
                    "clear" -> terminalService.clear()
                    "kill"  -> { terminalService.kill(); ready = false }
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
  html,body { height:100%; margin:0; background:#1e1e1e; }
  #terminal { position:absolute; inset:0; margin:12px; border:1px solid #444; border-radius:6px; }
</style>
</head>
<body>
<div id="toolbar">
    <strong>Quick Test:</strong>
    <button onclick="runTop()">top (interactive)</button>
    <button onclick="runTopOnce()">top once</button>
    <button onclick="quitTop()">send 'q'</button>
    <button onclick="sendEnter()">Enter</button>
</div>
<div id="terminal"></div>
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

  function init() {
      term = new Terminal({
        cursorBlink: true,
        convertEol: true,
        fontSize: 13,
        theme: { background: '#000000' },
        // disableStdin: false // 기본이 false지만 혹시 몰라 참고
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
        // 🔹 입력을 Kotlin으로 전달
        window.sendToKotlin && window.sendToKotlin({ command:'userInput', input:data });
      });
    
      term.onResize(({cols, rows}) => {
        window.sendToKotlin && window.sendToKotlin({ command:'resize', cols, rows });
      });
    
      // 🔹 브리지 준비될 때까지 대기 후 최초 신호 전송
      const kick = () => {
        if (window.sendToKotlin) {
          window.sendToKotlin({ command:'terminalReady' });
          window.sendToKotlin({ command:'resize', cols: term.cols, rows: term.rows });
          return true;
        }
        return false;
      };
      if (!kick()) {
        const t = setInterval(() => { if (kick()) clearInterval(t); }, 50);
      }
    
      window.addEventListener('resize', () => {
        try { fitAddon.fit(); window.sendToKotlin && window.sendToKotlin({ command:'resize', cols: term.cols, rows: term.rows }); } catch(e){}
      });
    
      term.write('\x1b[33mPTY terminal ready.\x1b[0m\r\n');
    }

    // ===== 버튼 동작 =====
    function runTop() {
      window.sendToKotlin({ command:'exec', cmd:'top' });  // 인터랙티브 top
    }
    function runTopOnce() {
      // 1회 출력 모드 (mac vs linux)
      const ua = navigator.userAgent;
      const isMac = ua.includes('Mac OS X') || ua.includes('Macintosh');
      const cmd = isMac ? 'top -l 1' : 'top -b -n 1';
      window.sendToKotlin({ command:'exec', cmd: cmd });
    }
    function quitTop() {
      window.sendToKotlin({ command:'userInput', input:'q' }); // top 종료
    }
    function sendEnter() {
      window.sendToKotlin({ command:'userInput', input:'\r' });
    }


  document.addEventListener('DOMContentLoaded', init);
</script>
</body>
</html>
""".trimIndent()
}
