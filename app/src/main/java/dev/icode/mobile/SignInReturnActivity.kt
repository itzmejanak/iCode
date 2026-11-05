package dev.icode.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Where a remote MCP server's sign-in comes back to (docs/mcp-plan.md §8.2): the browser opens iCode's
 * own scheme, and this hands the answer to the iCode screen already open, then goes. Anything may open
 * the scheme: the sign-in checks the answer's `state` before it uses it.
 */
class SignInReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.dataString?.let { answer ->
            startActivity(
                Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SIGNED_IN).putExtra(MainActivity.EXTRA_SIGN_IN, answer)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
