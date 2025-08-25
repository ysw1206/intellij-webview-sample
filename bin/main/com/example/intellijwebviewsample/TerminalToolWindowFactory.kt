package com.example.intellijwebviewsample

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.*

/**
 * Tool window displaying a terminal widget connected to [TerminalProcessService].
 * A simple text field sends input to the process and a text area shows echoed
 * output via [TerminalOutputBridge].
 */
class TerminalToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val webViewTerminalPanel = WebViewTerminalPanel(project)
        val content = ContentFactory.getInstance().createContent(
            webViewTerminalPanel.getComponent(),
            "WebView Terminal",
            false
        )
        
        toolWindow.contentManager.addContent(content)
    }
}
