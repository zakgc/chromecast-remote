package uk.co.home.chromecastremote

import uk.co.home.chromecastremote.protocol.TvTextTarget

/** The keyboard's small connection boundary makes editing and lifecycle behaviour testable. */
interface KeyboardRemote {
    val state: RemoteState
    fun keyboard(enabled: Boolean)
    fun replaceText(sessionId: Long, target: TvTextTarget, text: String, selection: Int, done: (Boolean, String) -> Unit)
    fun submitText(sessionId: Long, target: TvTextTarget, done: (Boolean, String) -> Unit)
}
