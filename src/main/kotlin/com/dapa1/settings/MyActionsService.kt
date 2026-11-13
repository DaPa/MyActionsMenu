package com.dapa1.settings

import com.dapa1.model.ActionItemConfig
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil

interface MyActionsChangeListener {
    companion object {
        val TOPIC = Topic.create("MyActionsChanged", MyActionsChangeListener::class.java)
    }
    fun actionsChanged()
}

class MyActionsState {
    var items: MutableList<ActionItemConfig> = mutableListOf(
        ActionItemConfig(actionId = "SaveAll"),
        ActionItemConfig(actionId = "ReformatCode"),
        ActionItemConfig(actionId = "OptimizeImports"),
        ActionItemConfig(isSeparator = true),
        ActionItemConfig(actionId = "CompileDirty"),
        ActionItemConfig(actionId = "Run"),
        ActionItemConfig(actionId = "Debug"),
        ActionItemConfig(isSeparator = true),
        ActionItemConfig(actionId = "CheckinProject"),
        ActionItemConfig(actionId = "Vcs.UpdateProject")
    )
    var menuTitle: String = "myActions"
    var menuIconPath: String? = null
    var popupWidth: Int = 320
    var popupHeight: Int = 360
}

@Service(Service.Level.APP)
@State(
    name = "MyActionsMenuSettings",
    storages = [Storage("MyActionsMenu.xml")]
)
class MyActionsService : PersistentStateComponent<MyActionsState> {
    private var myState = MyActionsState()

    override fun getState(): MyActionsState {
        return myState
    }

    override fun loadState(state: MyActionsState) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getItems(): List<ActionItemConfig> {
        return myState.items.toList()
    }

    fun setItems(newItems: List<ActionItemConfig>) {
        myState.items.clear()
        myState.items.addAll(newItems.map { it.copyItem() })
        notifyChanged()
    }

    fun getMenuTitle(): String {
        return myState.menuTitle
    }

    fun setMenuTitle(title: String) {
        myState.menuTitle = title
        notifyChanged()
    }

    fun getMenuIconPath(): String? {
        return myState.menuIconPath
    }

    fun setMenuIconPath(iconPath: String?) {
        myState.menuIconPath = iconPath?.ifBlank { null }
        notifyChanged()
    }

    fun getPopupWidth(): Int {
        return myState.popupWidth
    }

    fun setPopupWidth(width: Int) {
        myState.popupWidth = width
    }

    fun getPopupHeight(): Int {
        return myState.popupHeight
    }

    fun setPopupHeight(height: Int) {
        myState.popupHeight = height
    }

    private fun notifyChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(MyActionsChangeListener.TOPIC)
            .actionsChanged()
    }

    companion object {
        fun getInstance(): MyActionsService {
            return ApplicationManager.getApplication().getService(MyActionsService::class.java)
        }
    }
}
