package dev.icode.mobile.data.io

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommittedPreferencesTest {
    /** Preferences whose editor records each call and answers [commits] to commit(). */
    private class Recorded(commits: Boolean) {
        val calls = mutableListOf<String>()
        private val editor: SharedPreferences.Editor = proxy { self, name, args ->
            calls += listOfNotNull(name, args?.joinToString()).joinToString(" ")
            if (name == "commit") commits else self
        }
        val preferences: SharedPreferences = proxy { _, name, _ ->
            check(name == "edit") { "unexpected $name" }
            editor
        }

        private inline fun <reified T> proxy(crossinline answer: (self: Any, name: String, args: Array<Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
                answer(self, method.name, args)
            } as T
    }

    @Test fun `the changes are committed, not applied in the background`() {
        val recorded = Recorded(commits = true)

        recorded.preferences.commitOrFail("Unable to save") { putString("key", "value") }

        assertEquals(listOf("putString key, value", "commit"), recorded.calls)
    }

    @Test fun `a failed write is an error with its message`() {
        val recorded = Recorded(commits = false)

        val error = assertThrows(IllegalStateException::class.java) {
            recorded.preferences.commitOrFail("Unable to save recent files") { putString("key", "value") }
        }

        assertEquals("Unable to save recent files", error.message)
    }
}
