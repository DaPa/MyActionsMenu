package com.dapa1.ui

import com.dapa1.util.IconHelper
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

class ChooseActionIconDialog(
    private val project: Project?,
    initialIconPath: String?,
    private val defaultIcon: Icon? = null
) : DialogWrapper(project, true) {

    sealed class IconChoice {
        abstract val displayText: String
        abstract val icon: Icon?
        abstract val value: String?

        data class ActionChoice(
            val actionId: String,
            override val displayText: String,
            override val icon: Icon
        ) : IconChoice() {
            override val value: String = "action:$actionId"
            override fun toString(): String = displayText
        }

        data class FileChoice(
            val filePath: String,
            override val displayText: String,
            override val icon: Icon?
        ) : IconChoice() {
            override val value: String = filePath
            override fun toString(): String = displayText
        }
    }

    private val comboBoxModel = DefaultComboBoxModel<IconChoice>()
    private val comboBox = ComboBox(comboBoxModel)
    private val browseButton = FixedSizeButton(comboBox)
    private val clearButton = JButton("Reset to Default")

    private var selectedValue: String? = initialIconPath

    companion object {
        @Volatile
        private var cachedActionChoices: List<IconChoice.ActionChoice>? = null
        private val isCacheLoading = AtomicBoolean(false)

        fun loadActionIconsInBackground(onLoaded: ((List<IconChoice.ActionChoice>) -> Unit)? = null) {
            val existing = cachedActionChoices
            if (existing != null) {
                onLoaded?.invoke(existing)
                return
            }

            if (isCacheLoading.compareAndSet(false, true)) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    val choices = doCollectActionIcons()
                    cachedActionChoices = choices
                    isCacheLoading.set(false)
                    if (onLoaded != null) {
                        ApplicationManager.getApplication().invokeLater(
                            { onLoaded(choices) },
                            ModalityState.any()
                        )
                    }
                }
            }
        }

        private fun doCollectActionIcons(): List<IconChoice.ActionChoice> {
            val actionManager = ActionManager.getInstance()
            val actionIds = actionManager.getActionIdList("")
            val list = ArrayList<IconChoice.ActionChoice>()
            val seenActionIds = HashSet<String>()

            var count = 0
            for (id in actionIds) {
                count++
                if (count % 500 == 0) {
                    try {
                        Thread.sleep(1)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }

                if (!seenActionIds.add(id)) continue
                val action = actionManager.getAction(id) ?: continue
                val icon = action.templatePresentation.icon ?: continue
                if (icon.iconWidth <= 0 || icon.iconHeight <= 0) continue

                val text = action.templatePresentation.text?.trim()
                val displayText = if (!text.isNullOrBlank()) text else id

                list.add(IconChoice.ActionChoice(id, displayText, icon))
            }

            list.sortBy { it.displayText.lowercase() }
            return list
        }
    }

    init {
        title = "Choose Action Icon"
        isResizable = false
        init()
        setupUI()
    }

    private fun setupUI() {
        browseButton.icon = AllIcons.Nodes.Folder
        browseButton.setToolTipText(HtmlChunk.text("Choose icon file from disk..."))
        browseButton.addActionListener {
            chooseFileIcon()
        }

        clearButton.addActionListener {
            selectedValue = null
            comboBox.selectedItem = null
        }

        comboBox.renderer = object : ColoredListCellRenderer<IconChoice>() {
            override fun customizeCellRenderer(
                list: JList<out IconChoice>,
                value: IconChoice?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) {
                    icon = defaultIcon ?: AllIcons.Nodes.EmptyNode
                    append("Default Icon", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    return
                }

                icon = value.icon ?: AllIcons.Nodes.EmptyNode
                append(value.displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                if (value is IconChoice.ActionChoice) {
                    append("  (${value.actionId})", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                } else if (value is IconChoice.FileChoice) {
                    append("  (File)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
        }

        ComboboxSpeedSearch.installOn(comboBox)

        val cached = cachedActionChoices
        if (cached != null) {
            populateChoices(cached)
        } else {
            loadActionIconsInBackground { choices ->
                populateChoices(choices)
            }
        }
    }

    private fun populateChoices(actionChoices: List<IconChoice.ActionChoice>) {
        comboBoxModel.removeAllElements()
        var itemToSelect: IconChoice? = null

        val currentPath = selectedValue
        if (!currentPath.isNullOrBlank()) {
            if (currentPath.startsWith("action:")) {
                val actionId = currentPath.removePrefix("action:")
                itemToSelect = actionChoices.firstOrNull { it.actionId == actionId }
            } else {
                val matchingAction = actionChoices.firstOrNull { it.actionId == currentPath }
                if (matchingAction != null) {
                    itemToSelect = matchingAction
                } else {
                    val file = File(currentPath)
                    val icon = IconHelper.loadCustomIcon(currentPath)
                    val fileChoice = IconChoice.FileChoice(currentPath, file.name.ifBlank { currentPath }, icon)
                    comboBoxModel.addElement(fileChoice)
                    itemToSelect = fileChoice
                }
            }
        }

        for (choice in actionChoices) {
            comboBoxModel.addElement(choice)
        }

        if (itemToSelect != null) {
            comboBox.selectedItem = itemToSelect
        } else if (currentPath == null) {
            comboBox.selectedItem = null
        }

        comboBox.addActionListener {
            val selected = comboBox.selectedItem as? IconChoice
            selectedValue = selected?.value
        }
    }

    private fun chooseFileIcon() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Choose Action Icon")
            .withDescription("Select an icon file (SVG, PNG, ICO, JPG, GIF)")
            .withExtensionFilter("Icon files", "svg", "png", "ico", "jpg", "jpeg", "gif")

        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        val path = virtualFile.path
        val icon = IconHelper.loadCustomIcon(path)
        val fileChoice = IconChoice.FileChoice(path, virtualFile.name, icon)

        comboBoxModel.insertElementAt(fileChoice, 0)
        comboBox.selectedItem = fileChoice
        selectedValue = path
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(10)))
        panel.preferredSize = Dimension(JBUI.scale(480), JBUI.scale(80))
        panel.border = JBUI.Borders.empty(10)

        comboBox.preferredSize = Dimension(JBUI.scale(420), JBUI.scale(30))

        val comboPanel = JPanel(BorderLayout(JBUI.scale(4), 0))
        comboPanel.add(comboBox, BorderLayout.CENTER)
        comboPanel.add(browseButton, BorderLayout.EAST)

        val actionsPanel = JPanel(BorderLayout())
        actionsPanel.add(clearButton, BorderLayout.EAST)

        panel.add(comboPanel, BorderLayout.CENTER)
        panel.add(actionsPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return comboBox
    }

    fun getSelectedIconPath(): String? {
        val selected = comboBox.selectedItem as? IconChoice
        return selected?.value ?: selectedValue
    }
}
