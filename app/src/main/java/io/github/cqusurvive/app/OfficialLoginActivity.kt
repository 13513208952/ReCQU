package io.github.cqusurvive.app

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.cqusurvive.app.data.official.CquWebClient
import io.github.cqusurvive.app.data.official.WebAuthState
import kotlinx.coroutines.launch

/**
 * A classic View-based browser surface dedicated to official CQU authentication.
 *
 * Keeping the editable official page outside Compose's AndroidView interop avoids
 * vendor IME/InputConnection selection resets. Credentials and tokens remain in
 * WebView; this Activity only observes the coarse authentication state.
 */
class OfficialLoginActivity : ComponentActivity() {
    private lateinit var client: CquWebClient
    private lateinit var statusView: TextView
    private lateinit var useSessionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_official_login)

        statusView = findViewById(R.id.login_status)
        useSessionButton = findViewById(R.id.use_session)
        client = CquWebClient(this)

        findViewById<FrameLayout>(R.id.web_container).addView(
            client.webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        findViewById<Button>(R.id.browser_back).setOnClickListener { navigateBack() }
        findViewById<Button>(R.id.browser_reload).setOnClickListener { client.webView.reload() }
        useSessionButton.setOnClickListener {
            CookieManager.getInstance().flush()
            setResult(Activity.RESULT_OK)
            finish()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateBack()
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                client.authState.collect(::renderAuthState)
            }
        }
        client.startLogin()
    }

    private fun navigateBack() {
        if (client.webView.canGoBack()) {
            client.webView.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun renderAuthState(state: WebAuthState) {
        statusView.text = when (state) {
            WebAuthState.Idle -> getString(R.string.login_preparing)
            WebAuthState.Loading -> getString(R.string.login_instructions)
            WebAuthState.Authenticated -> getString(R.string.login_ready)
            is WebAuthState.Failed -> getString(R.string.login_failed)
        }
        useSessionButton.isEnabled = state is WebAuthState.Authenticated
    }

    override fun onDestroy() {
        (client.webView.parent as? FrameLayout)?.removeView(client.webView)
        client.destroy()
        super.onDestroy()
    }
}
