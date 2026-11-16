package dev.icode.mobile

import android.app.Application
import android.app.NotificationManager
import dev.icode.mobile.data.notify.IcodeChannels
import dev.icode.mobile.data.phone.PhoneService
import dev.icode.mobile.ui.FileIcons
import dev.icode.mobile.ui.phone.PhoneOverlays

/**
 * iCode's process (docs/voice-and-phone-control.md): what every start of it needs before any of its parts
 * runs. Android may start it for the phone service alone, with no screen open: the service's overlays —
 * the UI's, which data code never imports — are registered here, at the composition root. So are the
 * notification channels, before anything can post to one (docs/engineering-rules.md).
 */
class ICodeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        IcodeChannels.register(getSystemService(NotificationManager::class.java))
        PhoneService.overlayFactory = ::PhoneOverlays
        // The file icons' theme, read ahead off the main thread: the first list draws without waiting on it.
        Thread({ FileIcons.warm(this) }, "file-icons").apply { isDaemon = true }.start()
    }
}
