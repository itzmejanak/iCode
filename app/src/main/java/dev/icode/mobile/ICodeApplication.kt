package dev.icode.mobile

import android.app.Application
import dev.icode.mobile.data.phone.PhoneService
import dev.icode.mobile.ui.FileIcons
import dev.icode.mobile.ui.phone.PhoneOverlays

/**
 * iCode's process (docs/phone-agent-plan.md §5.7): what every start of it needs before any of its parts
 * runs. Android may start it for the phone service alone, with no screen open: the service's overlays —
 * the UI's, which data code never imports — are registered here, at the composition root.
 */
class ICodeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneService.overlayFactory = ::PhoneOverlays
        // The file icons' theme, read ahead off the main thread: the first list draws without waiting on it.
        Thread({ FileIcons.warm(this) }, "file-icons").apply { isDaemon = true }.start()
    }
}
