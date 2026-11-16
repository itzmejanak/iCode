package dev.icode.mobile.data.phone

import dev.icode.mobile.domain.phone.PhoneAct
import dev.icode.mobile.domain.phone.PhoneDirection
import dev.icode.mobile.domain.phone.PhoneKey
import dev.icode.mobile.domain.phone.PhoneScreen
import dev.icode.mobile.domain.phone.PhoneShot

/**
 * The phone as the assistant's tools reach it (docs/voice-and-phone-control.md): read, act, go somewhere,
 * look. Every act refuses what is the user's alone ([dev.icode.mobile.domain.phone.PhoneRefusals]) and
 * says what stopped it as a [dev.icode.mobile.domain.phone.PhoneException].
 */
internal interface PhoneControl {
    /** Phone control is on: iCode's accessibility service is connected. */
    val on: Boolean

    /** The latest screen read; null before the first. */
    val latest: PhoneScreen?

    /** An app's name as the user sees it, by its package. */
    fun label(packageName: String): String

    /** The app called [name], its name and package; null when none or several are. */
    fun appNamed(name: String): Pair<String, String>?

    /** The screen once it has settled; its element numbers are good until the next read. */
    suspend fun screen(): PhoneScreen

    /** Element [number] of the latest screen read, acted on; then the screen settles. What happened, in words. */
    suspend fun act(number: Int, act: PhoneAct): String

    /** A point of the screen, in its pixels: tapped, or held. */
    suspend fun tapAt(x: Int, y: Int, hold: Boolean): String

    /** The screen itself scrolled, by a swipe across its middle. */
    suspend fun swipe(direction: PhoneDirection): String

    suspend fun key(key: PhoneKey): String

    /** An app by its name, by its launch intent. */
    suspend fun openApp(name: String): String

    /** A link, in the app that opens it. */
    suspend fun openLink(link: String): String

    /** A Settings page by its name; [app]'s own App info page when it is named. */
    suspend fun openSettings(name: String, app: String?): String

    /** A picture of the screen for the model; null when Android refuses one (a secure window). */
    suspend fun picture(): PhoneShot?
}
