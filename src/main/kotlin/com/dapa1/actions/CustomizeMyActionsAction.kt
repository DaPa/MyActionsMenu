package com.dapa1.actions

import com.dapa1.ui.MyActionsCustomizationDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class CustomizeMyActionsAction : DumbAwareAction() {

    init {
        templatePresentation.icon = AllIcons.General.GearPlain
        @Suppress("DialogTitleCapitalization")
        templatePresentation.text = "Customize myActions Menu..."
        templatePresentation.description = "Configure actions and separators for the myActions menu"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        MyActionsCustomizationDialog(e.project).show()
    }
}
