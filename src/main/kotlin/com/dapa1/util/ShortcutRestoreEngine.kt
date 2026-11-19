package com.dapa1.util

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

object ShortcutRestoreEngine {
    data class Binding(val actionId: String, val shortcut: KeyboardShortcut)
    enum class Problem { MISSING_ACTION, INVALID_STROKE, DUPLICATE, ALREADY_ASSIGNED }
    enum class Decision { SKIP, REPLACE }
    data class Entry(
        val index: Int,
        val actionId: String?,
        val imported: KeyboardShortcutExportModel,
        val binding: Binding?,
        val problem: Problem?,
        val conflicts: Set<Binding>
    )
    data class Plan(val entries: List<Entry>, internal val source: Keymap)
    data class Conflict(val entryIndex: Int, val binding: Binding)
    data class Result(val keymap: Keymap, val applied: List<Int>, val skipped: List<Int>)

    fun prepare(
        items: List<MenuItemExportModel>,
        keymap: Keymap,
        actionExists: (String) -> Boolean = { ActionManager.getInstance().getAction(it) != null }
    ): Plan {
        val existing = bindings(keymap)
        val seen = mutableSetOf<Binding>()
        val entries = mutableListOf<Entry>()
        for (item in items) {
            if (item.isSeparator) continue
            for (imported in item.shortcuts.orEmpty()) {
                val id = item.actionId
                val first = parse(imported.firstKeyStroke as String?)
                val secondText = imported.secondKeyStroke
                val second = secondText?.let(::parse)
                val shortcut = if (first != null && (secondText == null || second != null)) KeyboardShortcut(first, second) else null
                val binding = if (!id.isNullOrBlank() && shortcut != null) Binding(id, shortcut) else null
                val problem = when {
                    id.isNullOrBlank() || !actionExists(id) -> Problem.MISSING_ACTION
                    shortcut == null -> Problem.INVALID_STROKE
                    !seen.add(binding!!) -> Problem.DUPLICATE
                    binding in existing -> Problem.ALREADY_ASSIGNED
                    else -> null
                }
                entries.add(Entry(entries.size, id, imported, binding, problem, emptySet()))
            }
        }
        val candidates = entries.filter { it.problem == null }.mapNotNull { it.binding }
        return Plan(entries.map { entry ->
            val binding = entry.binding
            if (entry.problem != null || binding == null) entry else entry.copy(
                conflicts = (existing + candidates).filter { it != binding && conflicts(binding.shortcut, it.shortcut) }.toSet()
            )
        }, keymap)
    }

    fun apply(
        plan: Plan,
        name: String,
        selected: Set<Int> = emptySet(),
        decisions: Map<Conflict, Decision> = emptyMap(),
        bulkDecision: Decision = Decision.SKIP
    ): Result {
        val copy = plan.source.deriveKeymap(name)
        val applied = mutableListOf<Int>()
        val skipped = mutableListOf<Int>()
        for (entry in plan.entries) {
            val binding = entry.binding
            if (entry.index !in selected || entry.problem != null || binding == null) {
                skipped.add(entry.index)
                continue
            }
            val current = bindings(copy)
            if (binding in current) {
                skipped.add(entry.index)
                continue
            }
            val conflicts = current.filter { conflicts(binding.shortcut, it.shortcut) }
            if (conflicts.any { it !in entry.conflicts || (decisions[Conflict(entry.index, it)] ?: bulkDecision) != Decision.REPLACE }) {
                skipped.add(entry.index)
                continue
            }
            conflicts.forEach { copy.removeShortcut(it.actionId, it.shortcut) }
            copy.addShortcut(binding.actionId, binding.shortcut)
            applied.add(entry.index)
        }
        return Result(copy, applied, skipped)
    }

    private fun parse(text: String?): KeyStroke? {
        val stroke = text?.let { KeyStroke.getKeyStroke(it) } ?: return null
        return stroke.takeIf { it.keyCode != KeyEvent.VK_UNDEFINED && !it.isOnKeyRelease }
    }

    private fun conflicts(a: KeyboardShortcut, b: KeyboardShortcut): Boolean =
        a.firstKeyStroke == b.firstKeyStroke &&
            (a.secondKeyStroke == null || b.secondKeyStroke == null || a.secondKeyStroke == b.secondKeyStroke)

    private fun bindings(keymap: Keymap): Set<Binding> = keymap.actionIdList.flatMap { id ->
        keymap.getShortcuts(id).filterIsInstance<KeyboardShortcut>().map { Binding(id, it) }
    }.toSet()
}