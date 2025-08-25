package com.example.intellijwebviewsample

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class TerminalToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WebViewTerminalPanel(project).getComponent()
        val content = ContentFactory.getInstance().createContent(panel, "WebView Terminal", false)
        toolWindow.contentManager.addContent(content)
    }
}
