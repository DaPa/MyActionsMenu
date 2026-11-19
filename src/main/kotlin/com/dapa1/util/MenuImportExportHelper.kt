package com.dapa1.util

import com.dapa1.model.ActionItemConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper

data class KeyboardShortcutExportModel(
    var firstKeyStroke: String,
    var secondKeyStroke: String? = null
)

data class MenuItemExportModel(
    var actionId: String? = null,
    var isSeparator: Boolean = false,
    var customText: String? = null,
    var customIconPath: String? = null,
    var shortcuts: List<KeyboardShortcutExportModel>? = null
) {
    fun toActionItemConfig(): ActionItemConfig {
        return ActionItemConfig(
            actionId = actionId ?: "",
            isSeparator = isSeparator,
            customText = customText?.ifBlank { null },
            customIconPath = customIconPath?.ifBlank { null }
        )
    }

    companion object {
        fun fromActionItemConfig(config: ActionItemConfig): MenuItemExportModel {
            return if (config.isSeparator) {
                MenuItemExportModel(isSeparator = true)
            } else {
                MenuItemExportModel(
                    actionId = config.actionId,
                    isSeparator = false,
                    customText = config.customText,
                    customIconPath = config.customIconPath
                )
            }
        }
    }
}

data class MenuExportModel(
    var version: Int = 1,
    var menuTitle: String = "myActions",
    var menuIconPath: String? = null,
    var items: List<MenuItemExportModel> = emptyList()
)

object MenuImportExportHelper {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToJson(
        menuTitle: String,
        menuIconPath: String? = null,
        items: List<ActionItemConfig>,
        shortcuts: Map<String, List<KeyboardShortcutExportModel>> = emptyMap()
    ): String {
        val exportModel = MenuExportModel(
            version = 1,
            menuTitle = menuTitle,
            menuIconPath = menuIconPath?.ifBlank { null },
            items = items.map { item ->
                MenuItemExportModel.fromActionItemConfig(item).apply {
                    if (!item.isSeparator) this.shortcuts = shortcuts[item.actionId]
                }
            }
        )
        return gson.toJson(exportModel)
    }

    fun importFromJson(jsonString: String): MenuExportModel {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Configuration file is empty")
        }

        val jsonElement: JsonElement = JsonParser.parseString(trimmed)
        if (jsonElement.isJsonArray) {
            // Direct array of items
            val itemArray = gson.fromJson(jsonElement, Array<MenuItemExportModel>::class.java)
            return MenuExportModel(
                version = 1,
                menuTitle = "myActions",
                menuIconPath = null,
                items = itemArray.toList()
            )
        } else if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject
            val title = if (obj.has("menuTitle") && !obj.get("menuTitle").isJsonNull) {
                obj.get("menuTitle").asString
            } else {
                "myActions"
            }
            val iconPath = if (obj.has("menuIconPath") && !obj.get("menuIconPath").isJsonNull) {
                obj.get("menuIconPath").asString.ifBlank { null }
            } else {
                null
            }
            val version = if (obj.has("version") && !obj.get("version").isJsonNull) {
                obj.get("version").asInt
            } else {
                1
            }
            val items = if (obj.has("items") && obj.get("items").isJsonArray) {
                gson.fromJson(obj.get("items"), Array<MenuItemExportModel>::class.java).toList()
            } else {
                emptyList()
            }
            return MenuExportModel(
                version = version,
                menuTitle = title,
                menuIconPath = iconPath,
                items = items
            )
        } else {
            throw IllegalArgumentException("Unrecognized JSON format")
        }
    }

    fun exportToFile(project: Project?, menuTitle: String, menuIconPath: String?, items: List<ActionItemConfig>, shortcuts: Map<String, List<KeyboardShortcutExportModel>> = emptyMap()): Boolean {
        @Suppress("DialogTitleCapitalization") val descriptor = FileSaverDescriptor("Export myActions Configuration", "Save menu configuration to a JSON file", "json")
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val fileWrapper: VirtualFileWrapper =
            saveDialog.save(null as VirtualFile?, "quick-actions-menu.json") ?: return false

        return try {
            val json = exportToJson(menuTitle, menuIconPath, items, shortcuts)
            val targetFile = fileWrapper.file
            targetFile.writeText(json, Charsets.UTF_8)
            Messages.showInfoMessage(
                project,
                "myActions menu configuration successfully exported to:\n${targetFile.absolutePath}",
                "Export Successful"
            )
            true
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to export configuration: ${e.message}",
                "Export Error"
            )
            false
        }
    }

    fun importFromFile(project: Project?): MenuExportModel? {
        @Suppress("DialogTitleCapitalization") val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            .withTitle("Import myActions Configuration")
            .withDescription("Select a myActions JSON configuration file to import")

        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return null

        return try {
            val content = VfsUtil.loadText(virtualFile)
            val model = importFromJson(content)
            model
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to import configuration: ${e.message}",
                "Import Error"
            )
            null
        }
    }
}
