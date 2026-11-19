package com.dapa1.ui

import com.dapa1.model.ActionItemConfig
import com.dapa1.settings.MyActionsService
import com.dapa1.util.IconHelper
import com.dapa1.util.KeyboardShortcutExportModel
import com.dapa1.util.MenuImportExportHelper
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

class MyActionsCustomizationDialog(private val project: Project?) : DialogWrapper(project, true) {
    private val listModel = DefaultListModel<ActionItemConfig>()
    private val itemList = JBList(listModel)
    private val titleField = JBTextField()
    private val menuIconButton = JButton("Icon...")
    private var currentMenuIconPath: String? = null

    init {
        @Suppress("DialogTitleCapitalization")
        title = "Customize myActions Menu"
        loadCurrentSettings()
        init()
        ChooseActionDialog.loadActionsInBackground()
        ChooseActionIconDialog.loadActionIconsInBackground()
    }

    private fun loadCurrentSettings() {
        val service = MyActionsService.getInstance()
        titleField.text = service.getMenuTitle()
        currentMenuIconPath = service.getMenuIconPath()
        updateMenuIconButton()
        listModel.clear()
        for (item in service.getItems()) {
            listModel.addElement(item.copyItem())
        }
    }

    private fun updateMenuIconButton() {
        val icon = IconHelper.loadCustomIcon(currentMenuIconPath) ?: AllIcons.Actions.IntentionBulb
        menuIconButton.icon = icon
        menuIconButton.setToolTipText(
            HtmlChunk.text(
                if (currentMenuIconPath != null) {
                    "Custom menu icon: $currentMenuIconPath (Click to change)"
                } else {
                    "Default menu icon (Click to change)"
                }
            )
        )
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, JBUI.scale(10)))
        mainPanel.preferredSize = Dimension(JBUI.scale(580), JBUI.scale(420))

        val headerPanel = JPanel(BorderLayout(JBUI.scale(8), 0))
        headerPanel.add(JLabel("Menu Title:"), BorderLayout.WEST)
        headerPanel.add(titleField, BorderLayout.CENTER)

        menuIconButton.apply {
            addActionListener {
                val dialog = ChooseActionIconDialog(project, currentMenuIconPath, AllIcons.Actions.IntentionBulb)
                if (dialog.showAndGet()) {
                    currentMenuIconPath = dialog.getSelectedIconPath()
                    updateMenuIconButton()
                }
            }
        }
        updateMenuIconButton()
        headerPanel.add(menuIconButton, BorderLayout.EAST)
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        itemList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        itemList.cellRenderer = object : ColoredListCellRenderer<ActionItemConfig>() {
            override fun customizeCellRenderer(
                list: JList<out ActionItemConfig>,
                value: ActionItemConfig?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return
                if (value.isSeparator) {
                    icon = null
                    append("────────── Separator ──────────", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    return
                }

                val actionManager = ActionManager.getInstance()
                val action: AnAction? = actionManager.getAction(value.actionId)

                val defaultIcon = action?.templatePresentation?.icon ?: AllIcons.Nodes.EmptyNode
                val customIcon = IconHelper.loadCustomIcon(value.customIconPath)
                icon = customIcon ?: defaultIcon

                val defaultText = action?.templatePresentation?.text ?: value.actionId
                val displayText = value.customText ?: defaultText
                append(displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                if (value.customText != null && value.customText != defaultText) {
                    append(" (Default: $defaultText)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                } else {
                    append(" (${value.actionId})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                val activeKeymap = try { KeymapManager.getInstance()?.activeKeymap } catch (_: Throwable) { null }
                val keymapShortcuts = activeKeymap?.getShortcuts(value.actionId)
                val shortcuts = keymapShortcuts ?: action?.shortcutSet?.shortcuts
                val shortcutText = shortcuts?.firstOrNull { it is KeyboardShortcut }?.let {
                    KeymapUtil.getShortcutText(it)
                }
                if (!shortcutText.isNullOrEmpty()) {
                    append("  [$shortcutText]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
        }

        itemList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_F4) {
                    val index = itemList.selectedIndex
                    if (index in 0 until listModel.size()) {
                        val item = listModel.get(index)
                        if (!item.isSeparator) {
                            editIcon(item, index)
                            e.consume()
                        }
                    }
                }
                if (e.keyCode == KeyEvent.VK_F2) {
                    val index = itemList.selectedIndex
                    if (index in 0 until listModel.size()) {
                        val item = listModel.get(index)
                        if (!item.isSeparator) {
                            editLabel(item, index)
                            e.consume()
                        }
                    }
                }
            }
        })

        setupContextMenu()

        val decorator = ToolbarDecorator.createDecorator(itemList)
            .setAddAction {
                showAddActionDialog()
            }
            .setAddActionName("Add Action")
            .addExtraAction(object : DumbAwareAction("Edit Icon", "Edit action icon", AllIcons.Actions.Edit) {
                init {
                    shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0))
                }
                override fun actionPerformed(e: AnActionEvent) {
                    val index = itemList.selectedIndex
                    if (index in 0 until listModel.size()) {
                        val item = listModel.get(index)
                        if (!item.isSeparator) {
                            editIcon(item, index)
                        }
                    }
                }

                override fun update(e: AnActionEvent) {
                    val index = itemList.selectedIndex
                    val isAction = index in 0 until listModel.size() && !listModel.get(index).isSeparator
                    e.presentation.isEnabled = isAction
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            .addExtraAction(object : DumbAwareAction("Edit Label", "Edit action label", AllIcons.Actions.Edit) {
                init {
                    shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0))
                }

                override fun actionPerformed(e: AnActionEvent) {
                    val index = itemList.selectedIndex
                    if (index in 0 until listModel.size()) {
                        val item = listModel.get(index)
                        if (!item.isSeparator) editLabel(item, index)
                    }
                }

                override fun update(e: AnActionEvent) {
                    val index = itemList.selectedIndex
                    e.presentation.isEnabled = index in 0 until listModel.size() && !listModel.get(index).isSeparator
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
            .addExtraAction(object : DumbAwareAction("Edit Shortcut", "Edit action shortcuts in Keymap settings", AllIcons.Actions.Edit) {
                override fun actionPerformed(e: AnActionEvent) {
                    val item = itemList.selectedValue ?: return
                    if (canEditShortcut(item)) editShortcut(item)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = itemList.selectedValue?.let(::canEditShortcut) == true
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })
            .addExtraAction(object : DumbAwareAction("Add Separator", null, AllIcons.General.SeparatorH) {
                override fun actionPerformed(e: AnActionEvent) {
                    addSeparator()
                }
            })
            .addExtraAction(object : DumbAwareAction("Export...", "Export menu configuration to file", AllIcons.ToolbarDecorator.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    exportConfiguration()
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            .addExtraAction(object : DumbAwareAction("Import...", "Import menu configuration from file", AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    importConfiguration()
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            .setRemoveAction {
                removeSelectedItem()
            }
            .setMoveUpAction {
                moveSelectedItem(-1)
            }
            .setMoveDownAction {
                moveSelectedItem(1)
            }

        mainPanel.add(decorator.createPanel(), BorderLayout.CENTER)
        return mainPanel
    }

    private fun showAddActionDialog(insertIndex: Int? = null) {
        val chooser = ChooseActionDialog(project)
        if (chooser.showAndGet()) {
            val actionId = chooser.selectedActionId ?: return
            val newItem = ActionItemConfig(actionId = actionId)
            val targetIdx = insertIndex ?: if (itemList.selectedIndex >= 0) itemList.selectedIndex + 1 else listModel.size()
            if (targetIdx in 0..listModel.size()) {
                listModel.add(targetIdx, newItem)
                itemList.selectedIndex = targetIdx
            } else {
                listModel.addElement(newItem)
                itemList.selectedIndex = listModel.size() - 1
            }
        }
    }

    private fun addSeparator(insertIndex: Int? = null) {
        val separatorItem = ActionItemConfig(isSeparator = true)
        val targetIdx = insertIndex ?: if (itemList.selectedIndex >= 0) itemList.selectedIndex + 1 else listModel.size()
        if (targetIdx in 0..listModel.size()) {
            listModel.add(targetIdx, separatorItem)
            itemList.selectedIndex = targetIdx
        } else {
            listModel.addElement(separatorItem)
            itemList.selectedIndex = listModel.size() - 1
        }
    }

    private fun removeSelectedItem() {
        val index = itemList.selectedIndex
        if (index in 0 until listModel.size()) {
            listModel.remove(index)
            if (!listModel.isEmpty) itemList.selectedIndex = index.coerceAtMost(listModel.size() - 1)
        }
    }

    private fun moveSelectedItem(offset: Int) {
        val index = itemList.selectedIndex
        val target = index + offset
        if (index in 0 until listModel.size() && target in 0 until listModel.size()) {
            val item = listModel.remove(index)
            listModel.add(target, item)
            itemList.selectedIndex = target
        }
    }

    private fun commonShortcut(button: CommonActionsPanel.Buttons): KeyStroke? =
        CommonActionsPanel.getCommonShortcut(button).shortcuts.filterIsInstance<KeyboardShortcut>().firstOrNull()?.firstKeyStroke

    private fun editIcon(item: ActionItemConfig, index: Int) {
        val action = ActionManager.getInstance().getAction(item.actionId)
        val defaultIcon = action?.templatePresentation?.icon
        val dialog = ChooseActionIconDialog(project, item.customIconPath, defaultIcon)
        if (dialog.showAndGet()) {
            item.customIconPath = dialog.getSelectedIconPath()
            listModel.set(index, item)
            itemList.repaint()
        }
    }

    private fun editLabel(item: ActionItemConfig, index: Int) {
        val currentText = item.customText ?: ActionManager.getInstance().getAction(item.actionId)?.templatePresentation?.text ?: item.actionId
        val newText = Messages.showInputDialog(
            project,
            "Enter custom label for action:",
            "Edit Action Label",
            null,
            currentText,
            null
        )
        if (newText != null) {
            item.customText = newText.ifBlank { null }
            listModel.set(index, item)
            itemList.repaint()
        }
    }

    private fun canEditShortcut(item: ActionItemConfig): Boolean =
        !item.isSeparator && ActionManager.getInstance().getAction(item.actionId) != null

    private fun editShortcut(item: ActionItemConfig) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KeymapPanel::class.java) { panel ->
            panel.selectAction(item.actionId)
        }
        itemList.repaint()
    }

    private fun setupContextMenu() {
        itemList.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val index = itemList.locationToIndex(java.awt.Point(x, y))
                if (index >= 0) {
                    itemList.selectedIndex = index
                    val item = listModel.get(index)
                    val popup = JPopupMenu()

                    if (!item.isSeparator) {
                        val editIconItem = JMenuItem("Edit Icon...").apply {
                            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0)
                            addActionListener {
                                editIcon(item, index)
                            }
                        }
                        popup.add(editIconItem)

                        val editLabelItem = JMenuItem("Edit Label...").apply {
                            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)
                            addActionListener {
                                editLabel(item, index)
                            }
                        }
                        popup.add(editLabelItem)
                        val editShortcutItem = JMenuItem("Edit Shortcut...").apply {
                            isEnabled = canEditShortcut(item)
                            addActionListener { editShortcut(item) }
                        }
                        popup.add(editShortcutItem)
                        popup.addSeparator()
                    }

                    val addBelowItem = JMenuItem("Add Action Below...").apply {
                        accelerator = commonShortcut(CommonActionsPanel.Buttons.ADD)
                        addActionListener {
                            showAddActionDialog()
                        }
                    }
                    popup.add(addBelowItem)

                    val addSeparatorBelowItem = JMenuItem("Add Separator Below").apply {
                        addActionListener {
                            addSeparator()
                        }
                    }
                    popup.add(addSeparatorBelowItem)

                    popup.addSeparator()

                    val removeItem = JMenuItem("Remove").apply {
                        accelerator = commonShortcut(CommonActionsPanel.Buttons.REMOVE)
                        addActionListener {
                            removeSelectedItem()
                        }
                    }
                    popup.add(removeItem)

                    val moveUpItem = JMenuItem("Move Up").apply {
                        accelerator = commonShortcut(CommonActionsPanel.Buttons.UP)
                        isEnabled = index > 0
                        addActionListener { moveSelectedItem(-1) }
                    }
                    popup.add(moveUpItem)

                    val moveDownItem = JMenuItem("Move Down").apply {
                        accelerator = commonShortcut(CommonActionsPanel.Buttons.DOWN)
                        isEnabled = index < listModel.size() - 1
                        addActionListener { moveSelectedItem(1) }
                    }
                    popup.add(moveDownItem)

                    popup.addSeparator()

                    val exportItem = JMenuItem("Export Configuration...").apply {
                        addActionListener {
                            exportConfiguration()
                        }
                    }
                    popup.add(exportItem)

                    val importItem = JMenuItem("Import Configuration...").apply {
                        addActionListener {
                            importConfiguration()
                        }
                    }
                    popup.add(importItem)

                    popup.show(comp, x, y)
                }
            }
        })
    }

    override fun createLeftSideActions(): Array<Action> {
        val exportAction = object : AbstractAction("Export...") {
            override fun actionPerformed(e: ActionEvent?) {
                exportConfiguration()
            }
        }
        val importAction = object : AbstractAction("Import...") {
            override fun actionPerformed(e: ActionEvent?) {
                importConfiguration()
            }
        }
        return arrayOf(exportAction, importAction)
    }

    private fun exportConfiguration() {
        val items = mutableListOf<ActionItemConfig>()
        for (i in 0 until listModel.size()) {
            items.add(listModel.get(i))
        }
        val options = arrayOf("Menu Only", "Include Shortcuts", "Cancel")
        val choice = Messages.showDialog(
            project,
            "Include keyboard shortcuts from the active keymap? Shortcut restoration is not yet supported; imports will not change the keymap.",
            "Export Configuration",
            options,
            0,
            Messages.getQuestionIcon()
        )
        if (choice != 0 && choice != 1) return
        val shortcuts = if (choice == 1) {
            val keymap = KeymapManager.getInstance()?.activeKeymap
            if (keymap == null) {
                Messages.showErrorDialog(project, "The active keymap is unavailable.", "Export Error")
                return
            }
            items.filterNot { it.isSeparator }.map { it.actionId }.distinct().associateWith { actionId ->
                keymap.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>().map {
                    KeyboardShortcutExportModel(it.firstKeyStroke.toString(), it.secondKeyStroke?.toString())
                }
            }
        } else emptyMap()
        val title = titleField.text.trim().ifEmpty { "myActions" }
        MenuImportExportHelper.exportToFile(project, title, currentMenuIconPath, items, shortcuts)
    }

    private fun importConfiguration() {
        val imported = MenuImportExportHelper.importFromFile(project) ?: return
        if (imported.items.any { it.shortcuts != null }) {
            Messages.showInfoMessage(project, "This file contains keyboard shortcuts, but shortcut restoration is not yet supported. The active keymap will not be changed.", "Shortcuts Not Imported")
        }
        if (listModel.isEmpty) {
            titleField.text = imported.menuTitle
            currentMenuIconPath = imported.menuIconPath
            updateMenuIconButton()
            for (item in imported.items) {
                listModel.addElement(item.toActionItemConfig())
            }
            if (!listModel.isEmpty) {
                itemList.selectedIndex = 0
            }
            return
        }

        val options = arrayOf("Replace", "Append", "Cancel")
        @Suppress("DialogTitleCapitalization") val result = Messages.showDialog(
            project,
            "Do you want to replace current menu items or append imported items?",
            "Import myActions",
            options,
            0,
            Messages.getQuestionIcon()
        )

        when (result) {
            0 -> { // Replace
                titleField.text = imported.menuTitle
                currentMenuIconPath = imported.menuIconPath
                updateMenuIconButton()
                listModel.clear()
                for (item in imported.items) {
                    listModel.addElement(item.toActionItemConfig())
                }
                if (!listModel.isEmpty) {
                    itemList.selectedIndex = 0
                }
            }
            1 -> { // Append
                val startIndex = listModel.size()
                for (item in imported.items) {
                    listModel.addElement(item.toActionItemConfig())
                }
                if (listModel.size() > startIndex) {
                    itemList.selectedIndex = startIndex
                }
            }
        }
    }

    override fun doOKAction() {
        val service = MyActionsService.getInstance()
        val items = mutableListOf<ActionItemConfig>()
        for (i in 0 until listModel.size()) {
            items.add(listModel.get(i))
        }
        service.setItems(items)
        service.setMenuTitle(titleField.text.trim().ifEmpty { "myActions" })
        service.setMenuIconPath(currentMenuIconPath)
        super.doOKAction()
    }
}
