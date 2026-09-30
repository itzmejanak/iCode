package dev.icode.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * App info's "Manage space" (docs/workspace-storage-plan.md §4.7): iCode's Storage settings, in
 * iCode's own task. Android shows this instead of "Clear storage", which would take every
 * workspace with it. MainActivity itself would open a second iCode screen, with a second
 * workbench, inside Settings; this hands the request to the one iCode has.
 */
class ManageSpaceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_MANAGE_SPACE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
