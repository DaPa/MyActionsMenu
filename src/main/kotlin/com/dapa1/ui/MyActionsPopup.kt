package com.dapa1.ui

import com.dapa1.model.ActionItemConfig
import com.dapa1.settings.MyActionsService
import com.dapa1.util.IconHelper
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class MyActionsPopup(
    private val project: Project?,
    private val dataContext: DataContext
) {
    data class PopupItem(
        val itemConfig: ActionItemConfig,
        val action: AnAction?,
        val text: String,
        val icon: Icon?,
        val shortcutText: String,
        val isSeparator: Boolean
    )

    private val allItems = mutableListOf<PopupItem>()
    private val listModel = DefaultListModel<PopupItem>()
    private val actionList = JBList(listModel)
    private val searchField = SearchTextField()
    private var popup: JBPopup? = null

    init {
        loadItems()
        filterList("")
    }

    companion object {
        fun matchKeyStrokes(k1: KeyStroke?, k2: KeyStroke?): Boolean {
            if (k1 == null || k2 == null) return false
            if (k1 == k2) return true
            if (k1.keyCode != k2.keyCode) return false
            if (k1.keyCode == KeyEvent.VK_UNDEFINED && k1.keyChar != k2.keyChar) return false
            val mask1 = normalizeModifiers(k1.modifiers)
            val mask2 = normalizeModifiers(k2.modifiers)
            return mask1 == mask2
        }

        fun normalizeModifiers(modifiers: Int): Int {
            var res = 0
            val ctrlMask = InputEvent.CTRL_DOWN_MASK or @Suppress("DEPRECATION") InputEvent.CTRL_MASK
            val shiftMask = InputEvent.SHIFT_DOWN_MASK or @Suppress("DEPRECATION") InputEvent.SHIFT_MASK
            val altMask = InputEvent.ALT_DOWN_MASK or @Suppress("DEPRECATION") InputEvent.ALT_MASK
            val metaMask = InputEvent.META_DOWN_MASK or @Suppress("DEPRECATION") InputEvent.META_MASK

            if ((modifiers and ctrlMask) != 0) res = res or InputEvent.CTRL_DOWN_MASK
            if ((modifiers and shiftMask) != 0) res = res or InputEvent.SHIFT_DOWN_MASK
            if ((modifiers and altMask) != 0) res = res or InputEvent.ALT_DOWN_MASK
            if ((modifiers and metaMask) != 0) res = res or InputEvent.META_DOWN_MASK
            return res
        }

        fun isTextEditingShortcut(e: KeyEvent): Boolean {
            val isCtrlOrMeta = (e.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK)) != 0
            if (isCtrlOrMeta) {
                when (e.keyCode) {
                    KeyEvent.VK_A, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X,
                    KeyEvent.VK_Z, KeyEvent.VK_Y, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                    KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE -> return true
                }
            }
            return false
        }
    }

    private fun loadItems() {
        val service = MyActionsService.getInstance()
        val actionManager = ActionManager.getInstance()
        val activeKeymap = try { KeymapManager.getInstance()?.activeKeymap } catch (_: Throwable) { null }

        for (config in service.getItems()) {
            if (config.isSeparator) {
                allItems.add(
                    PopupItem(
                        itemConfig = config,
                        action = null,
                        text = "",
                        icon = null,
                        shortcutText = "",
                        isSeparator = true
                    )
                )
                continue
            }

            val action = actionManager.getAction(config.actionId)
            val defaultText = action?.templatePresentation?.text ?: config.actionId
            val text = config.customText ?: defaultText
            val defaultIcon = action?.templatePresentation?.icon ?: AllIcons.Nodes.EmptyNode
            val customIcon = IconHelper.loadCustomIcon(config.customIconPath)
            val icon = customIcon ?: defaultIcon

            val keymapShortcuts = activeKeymap?.getShortcuts(config.actionId)
            val shortcuts = keymapShortcuts ?: action?.shortcutSet?.shortcuts
            val shortcutText = shortcuts?.firstOrNull { it is KeyboardShortcut }?.let {
                KeymapUtil.getShortcutText(it)
            } ?: ""

            allItems.add(
                PopupItem(
                    itemConfig = config,
                    action = action,
                    text = text,
                    icon = icon,
                    shortcutText = shortcutText,
                    isSeparator = false
                )
            )
        }
    }

    private fun filterList(query: String) {
        val trimmed = query.trim().lowercase()
        listModel.clear()

        val filtered = if (trimmed.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                !it.isSeparator && (
                        it.text.lowercase().contains(trimmed) ||
                                it.itemConfig.actionId.lowercase().contains(trimmed)
                        )
            }
        }

        for (item in filtered) {
            listModel.addElement(item)
        }

        if (!listModel.isEmpty) {
            val firstActionIndex = (0 until listModel.size()).firstOrNull { !listModel.get(it).isSeparator } ?: 0
            actionList.selectedIndex = firstActionIndex
        }
    }

    fun show(targetComponent: JComponent? = null) {
        val dimensionKey = "com.dapa1.ui.MyActionsPopup"
        val dimensionService = DimensionService.getInstance()
        val service = MyActionsService.getInstance()

        val ideWindow: Window? = (targetComponent?.let { SwingUtilities.getWindowAncestor(it) })
            ?: (project?.let { WindowManager.getInstance().getFrame(it) })
            ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

        val screenRect: Rectangle = if (targetComponent != null && targetComponent.isShowing) {
            ScreenUtil.getScreenRectangle(targetComponent)
        } else if (ideWindow != null && ideWindow.isShowing) {
            ScreenUtil.getScreenRectangle(ideWindow)
        } else {
            ScreenUtil.getMainScreenBounds()
        }

        val ideHeight = if (ideWindow != null && ideWindow.height > 0) {
            ideWindow.height
        } else {
            screenRect.height
        }
        val ideWidth = if (ideWindow != null && ideWindow.width > 0) {
            ideWindow.width
        } else {
            screenRect.width
        }

        val maxAllowedHeight = ((ideHeight * 2) / 3).coerceAtLeast(JBUI.scale(200))
        val minAllowedHeight = JBUI.scale(150)
        val maxAllowedWidth = ((ideWidth * 2) / 3).coerceAtLeast(JBUI.scale(300))
        val minAllowedWidth = JBUI.scale(250)

        val storedDimension = dimensionService?.getSize(dimensionKey, project)
        val initialWidth = (storedDimension?.width ?: JBUI.scale(service.getPopupWidth())).coerceIn(minAllowedWidth, maxAllowedWidth)
        val initialHeight = (storedDimension?.height ?: JBUI.scale(service.getPopupHeight())).coerceIn(minAllowedHeight, maxAllowedHeight)

        val initialSize = Dimension(initialWidth, initialHeight)
        dimensionService?.setSize(dimensionKey, initialSize, project)
        dimensionService?.setLocation(dimensionKey, null, project)

        val contentPanel = JPanel(BorderLayout(0, JBUI.scale(4)))
        contentPanel.border = JBUI.Borders.empty(6)
        contentPanel.preferredSize = initialSize

        // Search field
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                filterList(searchField.text)
            }
        })

        // Key listener handling navigation and action shortcuts
        val keyListener = object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                handleKeyEvent(e)
            }
        }

        searchField.textEditor.addKeyListener(keyListener)
        actionList.addKeyListener(keyListener)
        contentPanel.addKeyListener(keyListener)

        // Action list
        actionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        actionList.cellRenderer = object : ColoredListCellRenderer<PopupItem>() {
            override fun customizeCellRenderer(
                list: JList<out PopupItem>,
                value: PopupItem?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return
                if (value.isSeparator) {
                    icon = null
                    append("────────────────────────", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    return
                }

                icon = value.icon
                append(value.text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (value.shortcutText.isNotEmpty()) {
                    append("  ${value.shortcutText}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
        }

        actionList.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = actionList.locationToIndex(e.point)
                if (index in 0 until listModel.size()) {
                    val cellBounds = actionList.getCellBounds(index, index)
                    if (cellBounds != null && cellBounds.contains(e.point)) {
                        val item = listModel.get(index)
                        if (!item.isSeparator && actionList.selectedIndex != index) {
                            actionList.selectedIndex = index
                        }
                    }
                }
            }
        })

        actionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = actionList.locationToIndex(e.point)
                if (index in 0 until listModel.size()) {
                    val cellBounds = actionList.getCellBounds(index, index)
                    if (cellBounds != null && cellBounds.contains(e.point)) {
                        val item = listModel.get(index)
                        if (!item.isSeparator) {
                            actionList.selectedIndex = index
                            executeCurrentSelected()
                        }
                    }
                }
            }
        })

        // Footer / Customize option
        val footerPanel = JPanel(BorderLayout())
        footerPanel.border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1, 0, 0, 0)
        val customizeLabel = JBLabel("Customize myActions...", AllIcons.General.GearPlain, JBLabel.LEFT).apply {
            border = JBUI.Borders.empty(6, 4, 2, 4)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    popup?.cancel()
                    MyActionsCustomizationDialog(project).show()
                }
            })
        }
        footerPanel.add(customizeLabel, BorderLayout.CENTER)

        contentPanel.add(searchField, BorderLayout.NORTH)
        contentPanel.add(JBScrollPane(actionList), BorderLayout.CENTER)
        contentPanel.add(footerPanel, BorderLayout.SOUTH)

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(contentPanel, searchField.textEditor)
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(true)
            .setDimensionServiceKey(project, dimensionKey, false)
            .addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    val currentSize = if (contentPanel.width > 0 && contentPanel.height > 0) {
                        contentPanel.size
                    } else {
                        event.asPopup().size
                    }
                    if (currentSize != null && currentSize.width > 0 && currentSize.height > 0) {
                        val clampedW = currentSize.width.coerceIn(minAllowedWidth, maxAllowedWidth)
                        val clampedH = currentSize.height.coerceIn(minAllowedHeight, maxAllowedHeight)
                        val clampedSize = Dimension(clampedW, clampedH)
                        dimensionService?.setSize(dimensionKey, clampedSize, project)
                        service.setPopupWidth(clampedW)
                        service.setPopupHeight(clampedH)
                    }
                }
            })
            .createPopup()

        val createdPopup = popup ?: return
        if (targetComponent != null && targetComponent.isShowing) {
            createdPopup.showUnderneathOf(targetComponent)
        } else {
            showAnchoredToTopCenter(createdPopup, ideWindow, initialWidth, initialHeight, screenRect)
        }
    }

    private fun showAnchoredToTopCenter(
        createdPopup: JBPopup,
        ideWindow: Window?,
        popupWidth: Int,
        popupHeight: Int,
        screenRect: Rectangle
    ) {
        if (ideWindow != null && ideWindow.isShowing) {
            val topToolbar = findTopToolbar(ideWindow)
            if (topToolbar != null && topToolbar.isShowing) {
                val tbLoc = try { topToolbar.locationOnScreen } catch (_: Exception) { null }
                if (tbLoc != null) {
                    val x = tbLoc.x + (topToolbar.width - popupWidth) / 2
                    val y = tbLoc.y + topToolbar.height
                    val clampedX = x.coerceIn(screenRect.x, (screenRect.x + screenRect.width - popupWidth).coerceAtLeast(screenRect.x))
                    val clampedY = y.coerceIn(screenRect.y, (screenRect.y + screenRect.height - popupHeight).coerceAtLeast(screenRect.y))
                    createdPopup.showInScreenCoordinates(topToolbar, Point(clampedX, clampedY))
                    return
                }
            }

            val winLoc = try { ideWindow.locationOnScreen } catch (_: Exception) { null }
            if (winLoc != null) {
                val x = winLoc.x + (ideWindow.width - popupWidth) / 2
                val y = winLoc.y + JBUI.scale(60)
                val clampedX = x.coerceIn(screenRect.x, (screenRect.x + screenRect.width - popupWidth).coerceAtLeast(screenRect.x))
                val clampedY = y.coerceIn(screenRect.y, (screenRect.y + screenRect.height - popupHeight).coerceAtLeast(screenRect.y))
                createdPopup.showInScreenCoordinates(ideWindow, Point(clampedX, clampedY))
                return
            }
        }

        if (project != null) {
            createdPopup.showCenteredInCurrentWindow(project)
        } else {
            createdPopup.showInFocusCenter()
        }
    }

    private fun findTopToolbar(container: Container): JComponent? {
        fun search(comp: Component): JComponent? {
            if (!comp.isShowing) return null
            val className = comp.javaClass.name
            if (className.contains("Toolbar") || className.contains("NavBar") || className.contains("Header")) {
                if (comp is JComponent && comp.width > 100 && comp.height in 20..120) {
                    return comp
                }
            }
            if (comp is Container) {
                for (child in comp.components) {
                    val found = search(child)
                    if (found != null) return found
                }
            }
            return null
        }
        return search(container)
    }

    private fun moveSelection(step: Int) {
        val size = listModel.size()
        if (size == 0) return
        val currentIndex = actionList.selectedIndex
        var nextIndex = (currentIndex + step + size) % size
        var loopCount = 0

        while (listModel.get(nextIndex).isSeparator && loopCount < size) {
            nextIndex = (nextIndex + step + size) % size
            loopCount++
        }

        actionList.selectedIndex = nextIndex
        actionList.ensureIndexIsVisible(nextIndex)
    }

    private fun handleKeyEvent(e: KeyEvent): Boolean {
        when (e.keyCode) {
            KeyEvent.VK_DOWN -> {
                moveSelection(1)
                e.consume()
                return true
            }
            KeyEvent.VK_UP -> {
                moveSelection(-1)
                e.consume()
                return true
            }
            KeyEvent.VK_PAGE_DOWN -> {
                moveSelection(5)
                e.consume()
                return true
            }
            KeyEvent.VK_PAGE_UP -> {
                moveSelection(-5)
                e.consume()
                return true
            }
            KeyEvent.VK_ENTER -> {
                executeCurrentSelected()
                e.consume()
                return true
            }
            KeyEvent.VK_ESCAPE -> {
                popup?.cancel()
                e.consume()
                return true
            }
        }

        // Ignore standalone modifier keys
        if (e.keyCode in listOf(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_META)) {
            return false
        }

        val isFunctionKey = e.keyCode in KeyEvent.VK_F1..KeyEvent.VK_F24
        val hasModifiers = (e.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.META_DOWN_MASK)) != 0
        val isShiftFunctionKey = e.isShiftDown && (isFunctionKey || e.isControlDown || e.isAltDown)

        if (!isFunctionKey && !hasModifiers && !isShiftFunctionKey) {
            return false
        }

        val action = findActionForKeyEvent(e)
        if (action != null) {
            executeAction(action)
            e.consume()
            return true
        }

        return false
    }

    fun findActionForKeyEvent(e: KeyEvent): AnAction? {
        val eventStroke = KeyStroke.getKeyStrokeForEvent(e) ?: return null
        val activeKeymap = try { KeymapManager.getInstance()?.activeKeymap } catch (_: Throwable) { null }

        // 1. First check actions configured in allItems (in this menu)
        for (item in allItems) {
            if (item.isSeparator || item.action == null) continue

            val keymapShortcuts = activeKeymap?.getShortcuts(item.itemConfig.actionId)
            val shortcuts = keymapShortcuts ?: item.action.shortcutSet.shortcuts
            for (shortcut in shortcuts) {
                if (shortcut is KeyboardShortcut) {
                    if (matchKeyStrokes(eventStroke, shortcut.firstKeyStroke)) {
                        return item.action
                    }
                }
            }
        }

        // If focus is in search field and the event is a standard text editing shortcut,
        // let the text editor handle it without triggering global actions
        if (searchField.textEditor.isFocusOwner && isTextEditingShortcut(e)) {
            return null
        }

        // 2. Check active keymap for any registered action matching this KeyStroke
        if (activeKeymap != null) {
            val actionIds = activeKeymap.getActionIds(eventStroke)
            if (actionIds.isNotEmpty()) {
                val actionManager = ActionManager.getInstance()
                for (actionId in actionIds) {
                    val action = actionManager.getAction(actionId)
                    if (action != null) {
                        return action
                    }
                }
            }
        }

        return null
    }

    private fun executeCurrentSelected() {
        val selected = actionList.selectedValue ?: return
        if (selected.isSeparator || selected.action == null) return
        executeAction(selected.action)
    }

    fun executeAction(action: AnAction) {
        popup?.cancel()

        val actionEvent = AnActionEvent.createEvent(
            action,
            dataContext,
            action.templatePresentation.clone(),
            ActionPlaces.MAIN_TOOLBAR,
            ActionUiKind.TOOLBAR,
            null
        )

        ActionUtil.performAction(action, actionEvent)
    }
}
