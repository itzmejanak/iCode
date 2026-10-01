package dev.icode.mobile.data.assistant

import android.content.SharedPreferences

/** [SharedPreferences] in memory, committed at once: what the assistant's stores are tested over. */
internal class InMemoryPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = synchronized(values) { values.toMap() }
    override fun getString(key: String, defValue: String?): String? = synchronized(values) { values[key] as String? ?: defValue }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = synchronized(values) { values[key] as Set<String>? ?: defValues }
    override fun getInt(key: String, defValue: Int): Int = synchronized(values) { values[key] as Int? ?: defValue }
    override fun getLong(key: String, defValue: Long): Long = synchronized(values) { values[key] as Long? ?: defValue }
    override fun getFloat(key: String, defValue: Float): Float = synchronized(values) { values[key] as Float? ?: defValue }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(values) { values[key] as Boolean? ?: defValue }
    override fun contains(key: String): Boolean = synchronized(values) { key in values }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableListOf<(MutableMap<String, Any?>) -> Unit>()
        private fun change(apply: (MutableMap<String, Any?>) -> Unit) = apply { changes += apply }
        override fun putString(key: String, value: String?) = change { it[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = change { it[key] = values }
        override fun putInt(key: String, value: Int) = change { it[key] = value }
        override fun putLong(key: String, value: Long) = change { it[key] = value }
        override fun putFloat(key: String, value: Float) = change { it[key] = value }
        override fun putBoolean(key: String, value: Boolean) = change { it[key] = value }
        override fun remove(key: String) = change { it.remove(key) }
        override fun clear() = change { it.clear() }
        override fun commit(): Boolean {
            synchronized(values) { changes.forEach { it(values) } }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
