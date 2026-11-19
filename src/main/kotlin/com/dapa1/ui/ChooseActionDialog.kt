package com.dapa1.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class ChooseActionDialog(project: Project?) : DialogWrapper(project, true) {
    data class ActionEntry(
        val actionId: String,
        val text: String,
        val description: String?,
        val action: AnAction,
        val lowerText: String = text.lowercase(),
        val lowerActionId: String = actionId.lowercase(),
        val lowerDescription: String = description?.lowercase() ?: ""
    ) {
        val shortcutText: String by lazy {
            val activeKeymap = try { KeymapManager.getInstance()?.activeKeymap } catch (_: Throwable) { null }
            val keymapShortcuts = activeKeymap?.getShortcuts(actionId)
            val shortcuts = keymapShortcuts ?: action.shortcutSet.shortcuts
            shortcuts.firstOrNull { it is KeyboardShortcut }?.let {
                KeymapUtil.getShortcutText(it)
            } ?: ""
        }
    }

    companion object {
        @Volatile
        private var cachedActions: List<ActionEntry>? = null
        private val isCacheLoading = AtomicBoolean(false)

        fun loadActionsInBackground(onLoaded: ((List<ActionEntry>) -> Unit)? = null) {
            val existing = cachedActions
            if (existing != null) {
                onLoaded?.invoke(existing)
                return
            }

            if (isCacheLoading.compareAndSet(false, true)) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    val entries = doCollectActions()
                    cachedActions = entries
                    isCacheLoading.set(false)
                    if (onLoaded != null) {
                        ApplicationManager.getApplication().invokeLater(
                            { onLoaded(entries) },
                            ModalityState.any()
                        )
                    }
                }
            }
        }

        private fun doCollectActions(): List<ActionEntry> {
            val actionManager = ActionManager.getInstance()
            val actionIds = actionManager.getActionIdList("")
            val result = ArrayList<ActionEntry>(actionIds.size)

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

                val action = actionManager.getAction(id) ?: continue
                val templatePresentation = action.templatePresentation
                val text = templatePresentation.text?.trim() ?: id
                if (text.isBlank() && id.isBlank()) continue

                val description = templatePresentation.description

                result.add(
                    ActionEntry(
                        actionId = id,
                        text = text.ifBlank { id },
                        description = description,
                        action = action
                    )
                )
            }

            result.sortBy { it.lowerText }
            return result
        }
    }

    private val listModel = CollectionListModel<ActionEntry>()
    private val actionList = JBList(listModel)
    private val searchField = SearchTextField()
    private val filterAlarm = Alarm(disposable)

    var selectedActionId: String? = null
        private set

    init {
        title = "Choose Action"
        init()
        initData()
    }

    private fun initData() {
        actionList.emptyText.text = "Loading actions..."
        actionList.setPaintBusy(true)

        val cached = cachedActions
        if (cached != null) {
            actionList.setPaintBusy(false)
            updateFilteredList("", cached)
        } else {
            loadActionsInBackground { loadedList ->
                if (!isDisposed) {
                    actionList.setPaintBusy(false)
                    updateFilteredList(searchField.text, loadedList)
                }
            }
        }
    }

    private fun scheduleFilter() {
        actionList.setPaintBusy(true)
        actionList.emptyText.text = "Searching..."
        filterAlarm.cancelAllRequests()
        filterAlarm.addRequest(Runnable {
            val query = searchField.text
            val actions = cachedActions
            if (actions == null) {
                actionList.setPaintBusy(false)
                return@Runnable
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                val filtered = performFilter(query, actions)
                ApplicationManager.getApplication().invokeLater({
                    if (!isDisposed) {
                        applyFilteredResults(filtered, query)
                    }
                }, ModalityState.stateForComponent(actionList))
            }
        }, 150)
    }

    private fun performFilter(query: String, actions: List<ActionEntry>): List<ActionEntry> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            return actions
        }

        return actions.filter {
            it.lowerText.contains(trimmed) ||
                    it.lowerActionId.contains(trimmed) ||
                    (it.lowerDescription.isNotEmpty() && it.lowerDescription.contains(trimmed))
        }
    }

    private fun applyFilteredResults(results: List<ActionEntry>, query: String) {
        listModel.replaceAll(results)
        actionList.setPaintBusy(false)

        if (results.isEmpty()) {
            actionList.emptyText.text = if (query.trim().isEmpty()) "No actions available" else "No actions found for '$query'"
        } else {
            actionList.selectedIndex = 0
            actionList.ensureIndexIsVisible(0)
        }
    }

    private fun updateFilteredList(query: String, actions: List<ActionEntry>) {
        val filtered = performFilter(query, actions)
        applyFilteredResults(filtered, query)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.preferredSize = Dimension(JBUI.scale(550), JBUI.scale(400))

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                scheduleFilter()
            }
        })

        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        moveSelection(1)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        moveSelection(-1)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        doOKAction()
                        e.consume()
                    }
                }
            }
        })

        actionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        actionList.cellRenderer = object : ColoredListCellRenderer<ActionEntry>() {
            override fun customizeCellRenderer(
                list: JList<out ActionEntry>,
                value: ActionEntry?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return
                icon = value.action.templatePresentation.icon ?: AllIcons.Nodes.EmptyNode
                append(value.text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(" (${value.actionId})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                val shortcut = value.shortcutText
                if (shortcut.isNotEmpty()) {
                    append("  [$shortcut]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
        }

        actionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = actionList.locationToIndex(e.point)
                if (index in 0 until listModel.size) {
                    val cellBounds = actionList.getCellBounds(index, index)
                    if (cellBounds != null && cellBounds.contains(e.point)) {
                        actionList.selectedIndex = index
                        if (e.clickCount == 2 && !e.isConsumed) {
                            e.consume()
                            doOKAction()
                        }
                    }
                }
            }
        })

        panel.add(searchField, BorderLayout.NORTH)
        panel.add(JBScrollPane(actionList), BorderLayout.CENTER)

        return panel
    }

    private fun moveSelection(step: Int) {
        val size = listModel.size
        if (size == 0) return
        val currentIndex = actionList.selectedIndex
        val nextIndex = (currentIndex + step + size) % size
        actionList.selectedIndex = nextIndex
        actionList.ensureIndexIsVisible(nextIndex)
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return searchField.textEditor
    }

    override fun doOKAction() {
        val selected = actionList.selectedValue
        if (selected != null) {
            selectedActionId = selected.actionId
            super.doOKAction()
        }
    }
}
