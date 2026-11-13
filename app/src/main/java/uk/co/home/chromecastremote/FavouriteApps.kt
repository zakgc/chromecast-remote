package uk.co.home.chromecastremote

import android.net.Uri

/** User-defined buttons. The defaults below are used only to migrate the 0.2.0 installation. */
data class FavouriteApp(val id: String, val name: String, val link: String)

object FavouriteApps {
    val all = listOf(
        FavouriteApp("nuvio", "Nuvio", "nuvio://home"),
        FavouriteApp("smarttube", "SmartTube", "vnd.youtube.launch://youtube.com")
    )
    fun find(id: String) = all.firstOrNull { it.id == id }
    fun validLink(link: String): Boolean {
        if (link.length !in 1..2048 || link.any { it.isWhitespace() || it.isISOControl() }) return false
        val scheme = Uri.parse(link).scheme?.lowercase() ?: return false
        return scheme.matches(Regex("[a-z][a-z0-9+.-]*")) && scheme !in setOf("file", "content", "data", "javascript", "intent")
    }
}
