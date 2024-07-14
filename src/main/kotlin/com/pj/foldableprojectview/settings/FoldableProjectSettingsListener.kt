package com.pj.foldableprojectview.settings

import com.intellij.util.messages.Topic
import java.util.*

@FunctionalInterface
fun interface FoldableProjectSettingsListener : EventListener {

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic(FoldableProjectSettingsListener::class.java)
    }

    fun settingsChanged(settings: FoldableProjectSettings)
}
