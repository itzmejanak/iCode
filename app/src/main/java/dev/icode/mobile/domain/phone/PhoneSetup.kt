package dev.icode.mobile.domain.phone

/**
 * Settings › Assistant › Phone control (docs/phone-agent-plan.md §5.9, P7): the steps that turn it on,
 * each with whether it is done, read from [PhoneAccess] alone. Restricted settings cannot be read
 * back from Android, so that step counts as done once the service is on.
 */
enum class PhoneSetupStep(val title: String, val detail: String) {
    Restricted(
        "Allow restricted settings",
        "Android 13 and later hold back accessibility for apps installed from a file. In iCode's App info, open ⋮ and tap Allow restricted settings.",
    ),
    Service(
        "Turn on iCode's service",
        "In Accessibility, open Installed apps › iCode and turn it on. iCode then reads the screen and acts on it while you ask.",
    ),
}

/** What the page shows for one step: done, or the one to do now. */
data class PhoneSetupLine(val step: PhoneSetupStep, val done: Boolean, val current: Boolean)

object PhoneSetup {
    /** Android 13 (API 33) is where restricted settings begin. */
    const val RESTRICTED_FROM_SDK = 33

    /** The steps for this phone, in order; [sdk] is Android's API level. */
    fun lines(access: PhoneAccess, sdk: Int): List<PhoneSetupLine> {
        val steps = buildList {
            if (sdk >= RESTRICTED_FROM_SDK) add(PhoneSetupStep.Restricted)
            add(PhoneSetupStep.Service)
        }
        if (access.service) return steps.map { PhoneSetupLine(it, done = true, current = false) }
        // Not readable: the first step not done is shown as the one to do, the rest after it.
        return steps.mapIndexed { index, step -> PhoneSetupLine(step, done = false, current = index == 0) }
    }

    /** Shizuku ready and phone control off: both steps are one tap. */
    fun oneTap(access: PhoneAccess): Boolean = !access.service && access.shizukuReady

    /** The page's status line. */
    fun status(access: PhoneAccess): String = when {
        access.service && access.bubble -> "On · bubble shown"
        access.service -> "On · bubble put away"
        else -> "Off"
    }
}
