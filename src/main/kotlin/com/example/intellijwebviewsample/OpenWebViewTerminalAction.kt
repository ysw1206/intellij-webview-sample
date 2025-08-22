package com.example.intellijwebviewsample

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * WebView Terminal을 여는 액션
 * VS Code Extension의 command와 유사한 기능을 제공합니다.
 */
class OpenWebViewTerminalAction : AnAction() {
    
    private val logger = thisLogger()
    
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        
        logger.info("🚀 Opening WebView Terminal...")
        
        try {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("WebView Terminal")
            
            if (toolWindow != null) {
                toolWindow.activate(null)
                logger.info("✅ WebView Terminal activated")
            } else {
                logger.error("❌ WebView Terminal tool window not found")
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to open WebView Terminal", e)
        }
    }
    
    override fun update(e: AnActionEvent) {
        // 프로젝트가 열려있을 때만 액션을 활성화
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
