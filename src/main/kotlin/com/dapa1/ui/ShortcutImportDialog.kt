package com.dapa1.ui

import com.dapa1.util.ShortcutRestoreEngine
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.DefaultCellEditor
import javax.swing.table.AbstractTableModel

class ShortcutImportDialog(project: Project?, private val plan: ShortcutRestoreEngine.Plan) : DialogWrapper(project, true) {
    private val rows = plan.entries.flatMap { entry ->
        if (entry.conflicts.isEmpty()) listOf(entry to null) else entry.conflicts.map { entry to it }
    }
    private val choices = MutableList(rows.size) { "Keep existing" }
    private val selected = MutableList(plan.entries.size) { plan.entries[it].problem == null }
    private val model = object : AbstractTableModel() {
        private val columns = arrayOf("Import", "Action", "Shortcut", "Conflicts / status", "Resolution")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, column: Int): Boolean =
            rows[row].first.problem == null && (column == 0 || column == 4 && rows[row].second != null)
        override fun getValueAt(row: Int, column: Int): Any {
            val (entry, conflict) = rows[row]
            return when (column) {
                0 -> selected[entry.index]
                1 -> entry.actionId ?: "(missing)"
                2 -> entry.binding?.let { KeymapUtil.getShortcutText(it.shortcut) } ?: (entry.imported.firstKeyStroke as String? ?: "(invalid)")
                3 -> entry.problem?.name?.replace('_', ' ') ?: conflict?.let { "${it.actionId}: ${KeymapUtil.getShortcutText(it.shortcut)}" } ?: ""
                else -> if (conflict == null) "" else choices[row]
            }
        }
        override fun setValueAt(value: Any?, row: Int, column: Int) {
            if (column == 0) selected[rows[row].first.index] = value == true
            if (column == 4 && value is String) choices[row] = value
            fireTableDataChanged()
        }
    }
    private val table = JBTable(model)

    init {
        title = "Import Keyboard Shortcuts"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, JBUI.scale(8)))
        panel.add(JLabel("Changes apply immediately to a new active keymap and affect the entire IDE, even if you cancel menu customization."), BorderLayout.NORTH)
        table.columnModel.getColumn(4).cellEditor = DefaultCellEditor(JComboBox(arrayOf("Keep existing", "Replace existing")))
        val scroll = JBScrollPane(table)
        scroll.preferredSize = Dimension(JBUI.scale(900), JBUI.scale(300))
        panel.add(scroll, BorderLayout.CENTER)
        val buttons = JPanel()
        for (choice in listOf("Keep existing", "Replace existing")) {
            buttons.add(JButton("$choice for all").apply {
                addActionListener {
                    for (i in choices.indices) choices[i] = choice
                    this@ShortcutImportDialog.model.fireTableDataChanged()
                }
            })
        }
        panel.add(buttons, BorderLayout.SOUTH)
        return panel
    }

    fun selectedEntries(): Set<Int> {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        return plan.entries.filter { selected[it.index] }.map { it.index }.toSet()
    }

    fun decisions(): Map<ShortcutRestoreEngine.Conflict, ShortcutRestoreEngine.Decision> =
        rows.mapIndexedNotNull { row, (entry, conflict) ->
            conflict?.let {
                ShortcutRestoreEngine.Conflict(entry.index, it) to
                    if (choices[row] == "Replace existing") ShortcutRestoreEngine.Decision.REPLACE else ShortcutRestoreEngine.Decision.SKIP
            }
        }.toMap()
}