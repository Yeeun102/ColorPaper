package com.example.colorpaper.reminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReminderTemplate(
    val id: Long,
    val name: String,
    val days: List<Int>,
    val repeatLast: Boolean
)

object ReminderPreferences {
    private const val PREFS = "reminder_preferences"
    private const val KEY_TEMPLATES = "named_cycle_templates_v2"
    private const val KEY_CYCLES = "cycle_templates"
    private const val KEY_HOUR = "default_hour"
    private const val KEY_MINUTE = "default_minute"

    fun templates(context: Context): List<ReminderTemplate> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TEMPLATES, null)
        if (!json.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(json)
                return List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val dayArray = item.getJSONArray("days")
                    ReminderTemplate(
                        id = item.getLong("id"),
                        name = item.getString("name").let { if (it == "기본 복습") "기본" else it },
                        days = List(dayArray.length()) { dayArray.getInt(it) },
                        repeatLast = item.optBoolean("repeatLast", false)
                    )
                }.filter { it.days.isNotEmpty() }
            }
        }
        val legacy = prefs.getString(KEY_CYCLES, null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }
            ?.filter { it > 0 }?.distinct()?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(1)
        return listOf(ReminderTemplate(1L, "기본", legacy, false))
    }

    fun saveTemplates(context: Context, templates: List<ReminderTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            array.put(JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("repeatLast", template.repeatLast)
                put("days", JSONArray(template.days))
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TEMPLATES, array.toString()).apply()
    }

    fun defaultHour(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOUR, 20)

    fun defaultMinute(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTE, 0)

    fun saveDefaultTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59)).apply()
    }
}
