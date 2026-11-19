@file:Suppress("UnresolvedPluginConfigReference")

package com.dapa1.util

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class ShortcutRestoreEngineTest : BasePlatformTestCase() {
    private fun shortcut(first: String, second: String? = null) =
        KeyboardShortcut(KeyStroke.getKeyStroke(first), second?.let { KeyStroke.getKeyStroke(it) })

    private fun item(id: String, first: String, second: String? = null) =
        MenuItemExportModel(actionId = id, shortcuts = listOf(KeyboardShortcutExportModel(first, second)))

    fun testPreparationReportsProblemsAndPrefixConflicts() {
        val keymap = KeymapImpl().apply { name = "source" }
        keymap.addShortcut("SaveAll", shortcut("ctrl pressed K", "ctrl pressed A"))
        val items = listOf(
            item("Copy", "ctrl pressed K"),
            item("Copy", "ctrl pressed K"),
            item("missing", "ctrl pressed X"),
            item("Copy", "nonsense"),
            item("SaveAll", "ctrl pressed K", "ctrl pressed A"),
            item("Paste", "ctrl pressed K", "ctrl pressed B"),
            MenuItemExportModel(isSeparator = true, shortcuts = listOf(KeyboardShortcutExportModel("bad")))
        )
        val plan = ShortcutRestoreEngine.prepare(items, keymap) { it != "missing" }
        assertEquals(6, plan.entries.size)
        assertEquals(ShortcutRestoreEngine.Problem.DUPLICATE, plan.entries[1].problem)
        assertEquals(ShortcutRestoreEngine.Problem.MISSING_ACTION, plan.entries[2].problem)
        assertEquals(ShortcutRestoreEngine.Problem.INVALID_STROKE, plan.entries[3].problem)
        assertEquals(ShortcutRestoreEngine.Problem.ALREADY_ASSIGNED, plan.entries[4].problem)
        assertEquals(setOf("SaveAll", "Paste"), plan.entries[0].conflicts.map { it.actionId }.toSet())
        assertEquals(setOf("Copy"), plan.entries[5].conflicts.map { it.actionId }.toSet())
    }

    fun testExplicitDecisionsAndCopy() {
        val keymap = KeymapImpl().apply { name = "source" }
        val original = shortcut("ctrl pressed K", "ctrl pressed A")
        keymap.addShortcut("SaveAll", original)
        val plan = ShortcutRestoreEngine.prepare(listOf(item("Copy", "ctrl pressed K")), keymap) { true }
        val conflict = ShortcutRestoreEngine.Conflict(0, plan.entries[0].conflicts.single())
        assertTrue(ShortcutRestoreEngine.apply(plan, "none").applied.isEmpty())
        assertTrue(ShortcutRestoreEngine.apply(plan, "skip", setOf(0)).applied.isEmpty())
        val result = ShortcutRestoreEngine.apply(plan, "copy", setOf(0), mapOf(conflict to ShortcutRestoreEngine.Decision.REPLACE))
        assertEquals(listOf(0), result.applied)
        assertTrue(result.keymap.getShortcuts("SaveAll").isEmpty())
        assertEquals(1, result.keymap.getShortcuts("Copy").size)
        assertEquals(original, keymap.getShortcuts("SaveAll").single())
        assertTrue(keymap.getShortcuts("Copy").isEmpty())
    }

    fun testImportedConflictsAndNewConflictsFailClosed() {
        val keymap = KeymapImpl().apply { name = "source" }
        val plan = ShortcutRestoreEngine.prepare(listOf(item("Copy", "ctrl pressed K"), item("Paste", "ctrl pressed K", "ctrl pressed B")), keymap) { true }
        val result = ShortcutRestoreEngine.apply(plan, "skip", setOf(0, 1))
        assertEquals(listOf(0), result.applied)
        assertEquals(listOf(1), result.skipped)
        val replace = ShortcutRestoreEngine.apply(plan, "replace", setOf(0, 1), bulkDecision = ShortcutRestoreEngine.Decision.REPLACE)
        assertEquals(listOf(0, 1), replace.applied)
        assertTrue(replace.keymap.getShortcuts("Copy").isEmpty())
        keymap.addShortcut("Cut", shortcut("ctrl pressed K"))
        val stale = ShortcutRestoreEngine.apply(plan, "stale", setOf(0), bulkDecision = ShortcutRestoreEngine.Decision.REPLACE)
        assertTrue(stale.applied.isEmpty())
        assertEquals(1, stale.keymap.getShortcuts("Cut").size)
    }
}