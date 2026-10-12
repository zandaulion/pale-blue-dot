package com.globe.app.kids

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The same resource-based scripts drive offline synthesis and runtime coverage checks. */
object NarrationCatalog {
    private var cached: Pair<String, Map<String, String>>? = null

    @Synchronized fun scripts(context: Context): Map<String, String> {
        val locale = context.resources.configuration.locales.toLanguageTags()
        cached?.takeIf { it.first == locale }?.let { return it.second }
        val entries = context.assets.open("narration/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        fun resourceId(name: String): Int {
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            check(id != 0) { "Missing narration resource: $name" }
            return id
        }
        fun part(value: Any): String {
            if (value is String) return context.getString(resourceId(value))
            val spec = value as JSONObject
            val args = spec.optJSONArray("arguments") ?: JSONArray()
            val arguments = (0 until args.length()).map { i ->
                val argument = args.get(i)
                if (argument is JSONObject) context.getString(resourceId(argument.getString("resource"))) else argument
            }
            return context.getString(resourceId(spec.getString("resource")), *arguments.toTypedArray())
        }
        val scripts = entries.keys().asSequence().associateWith { id ->
            val parts = entries.getJSONArray(id)
            (0 until parts.length()).joinToString("\n") { part(parts.get(it)) }
        }
        cached = locale to scripts
        return scripts
    }

    fun text(context: Context, id: String): String = scripts(context).getValue(id)
}
