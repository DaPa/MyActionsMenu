package com.dapa1.ui

import com.dapa1.settings.MyActionsService
import com.dapa1.util.IconHelper
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.DumbAwareAction
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.JMenuItem

class MyActionsToolbarWidget : DumbAwareAction(), CustomComponentAction {

    init {
        templatePresentation.icon = AllIcons.Actions.IntentionBulb
        @Suppress("DialogTitleCapitalization")
        templatePresentation.text = "myActions"
        templatePresentation.description = "Open myActions Menu"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val service = MyActionsService.getInstance()
        val title = service.getMenuTitle()
        e.presentation.text = title
        val customIcon = IconHelper.loadCustomIcon(service.getMenuIconPath())
        e.presentation.icon = customIcon ?: AllIcons.Actions.IntentionBulb
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val inputComponent = e.inputEvent?.component as? JComponent
        val targetComponent = when {
            inputComponent != null && inputComponent.isShowing && inputComponent !is JMenuItem -> inputComponent
            else -> findVisibleToolbarWidget()
        }

        val popup = MyActionsPopup(e.project, e.dataContext)
        popup.show(targetComponent)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val button = ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
        synchronized(activeWidgets) {
            activeWidgets.add(button)
        }
        return button
    }
}

private val activeWidgets: MutableSet<JComponent> = Collections.newSetFromMap(WeakHashMap())

fun findVisibleToolbarWidget(): JComponent? {
    synchronized(activeWidgets) {
        return activeWidgets.firstOrNull { it.isShowing }
    }
}