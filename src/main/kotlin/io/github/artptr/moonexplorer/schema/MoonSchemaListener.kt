package io.github.artptr.moonexplorer.schema

import com.intellij.util.messages.Topic

/** Gets a message when the mapping of the configuration files to the JSON schemas changes, or when moon wrote the local schema files. */
fun interface MoonSchemaListener {

    /** The JSON schema service of the IDE must find the schemas of the configuration files again. */
    fun schemasChanged()

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<MoonSchemaListener> = Topic(MoonSchemaListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
