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
        val service = project.getService(TerminalProcessService::class.java)

        val panel = JPanel(BorderLayout())

        val inputField = JTextField()
        inputField.addActionListener {
            val text = inputField.text
            service.sendInput(text)
            inputField.text = ""
        }
        panel.add(inputField, BorderLayout.NORTH)

        val outputArea = JTextArea(5, 80)
        outputArea.isEditable = false
        panel.add(JScrollPane(outputArea), BorderLayout.SOUTH)

        service.setBridge(object : TerminalOutputBridge {
            override fun pushStdout(text: String) {
                SwingUtilities.invokeLater { outputArea.append(text) }
            }
            override fun pushStderr(text: String) {
                SwingUtilities.invokeLater { outputArea.append(text) }
            }
            override fun onInfo(message: String) {
                SwingUtilities.invokeLater { outputArea.append("[INFO] ${'$'}message\n") }
            }
            override fun onError(message: String) {
                SwingUtilities.invokeLater { outputArea.append("[ERROR] ${'$'}message\n") }
            }
        })

        service.initialize(System.getProperty("user.home"))

        val terminalComponent = service.getTerminalComponent() ?: JLabel("Terminal unavailable")
        panel.add(terminalComponent, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "WebView Terminal", false)
        toolWindow.contentManager.addContent(content)
        Disposer.register(toolWindow.disposable, Disposable { service.kill() })
    }
}
