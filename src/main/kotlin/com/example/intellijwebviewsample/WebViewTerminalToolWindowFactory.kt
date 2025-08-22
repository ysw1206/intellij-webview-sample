package com.example.intellijwebviewsample

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * WebView Terminal을 위한 Tool Window Factory
 * VS Code Extension의 webview panel과 유사한 기능을 제공합니다.
 */
class WebViewTerminalToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val webViewTerminalPanel = WebViewTerminalPanel(project)
        val content = ContentFactory.getInstance().createContent(
            webViewTerminalPanel.getComponent(),
            "WebView Terminal",
            false
        )
        
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}
