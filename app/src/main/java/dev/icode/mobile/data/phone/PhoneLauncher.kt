package dev.icode.mobile.data.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.icode.mobile.domain.phone.PhoneException
import dev.icode.mobile.domain.phone.PhonePlaces

/**
 * Straight there, by intent (docs/phone-agent-plan.md §5.4): an app by its name, a link, a Settings
 * page. iCode may start them from the background while its accessibility service is bound — one of
 * Android's own exemptions for background activity starts.
 */
internal class PhoneLauncher(private val context: Context) {
    /** The apps the launcher lists, by their names. */
    fun apps(): List<PhonePlaces.App> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val manager = context.packageManager
        return manager.queryIntentActivities(launcher, 0).map { info ->
            PhonePlaces.App(info.loadLabel(manager).toString(), info.activityInfo.packageName, info.activityInfo.name)
        }
    }

    /** [packageName]'s name as the user sees it; the package itself when it has none. */
    fun label(packageName: String): String = runCatching {
        context.packageManager.run { getApplicationLabel(getApplicationInfo(packageName, 0)).toString() }
    }.getOrDefault(packageName)

    fun openApp(name: String): String = when (val found = PhonePlaces.app(name, apps())) {
        is PhonePlaces.Found.One -> {
            val app = found.value
            start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.packageName, app.activity))
            "Opened ${app.label}"
        }
        is PhonePlaces.Found.Several -> throw PhoneException("Several apps match “$name”: ${found.names.joinToString()}. Name one.")
        PhonePlaces.Found.None -> throw PhoneException("No app called “$name” is installed")
    }

    fun openLink(link: String): String {
        if (!PhonePlaces.linkAllowed(link)) throw PhoneException("Only web, phone, message, mail, map and store links open: not “${link.substringBefore(':')}”")
        start(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        return "Opened $link"
    }

    fun openSettings(name: String, app: String?): String {
        if (app != null) {
            val target = (PhonePlaces.app(app, apps()) as? PhonePlaces.Found.One)?.value
                ?: throw PhoneException("No single app called “$app” is installed")
            start(Intent(PhonePlaces.APP_INFO, Uri.fromParts("package", target.packageName, null)))
            return "Opened App info of ${target.label}"
        }
        val page = PhonePlaces.settings(name)
            ?: throw PhoneException("No Settings page called “$name”. Pages: ${PhonePlaces.SETTINGS.joinToString { it.name }}")
        start(Intent(page.action))
        return "Opened ${page.name}"
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (missing: ActivityNotFoundException) {
            throw PhoneException("Nothing on this phone opens that")
        } catch (refused: SecurityException) {
            throw PhoneException("Android refused to open that")
        }
    }
}
