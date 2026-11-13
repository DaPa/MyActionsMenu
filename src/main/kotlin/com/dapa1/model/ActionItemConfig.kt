package com.dapa1.model

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import java.util.UUID

@Tag("item")
data class ActionItemConfig(
    @Attribute("id")
    var id: String = UUID.randomUUID().toString(),

    @Attribute("actionId")
    var actionId: String = "",

    @Attribute("isSeparator")
    var isSeparator: Boolean = false,

    @Attribute("customText")
    var customText: String? = null,

    @Attribute("customIconPath")
    var customIconPath: String? = null
) {
    fun copyItem(): ActionItemConfig {
        return ActionItemConfig(
            id = UUID.randomUUID().toString(),
            actionId = actionId,
            isSeparator = isSeparator,
            customText = customText,
            customIconPath = customIconPath
        )
    }
}
