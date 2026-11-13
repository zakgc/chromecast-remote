package uk.co.home.chromecastremote.protocol

import org.junit.Assert.*
import org.junit.Test
import uk.co.home.chromecastremote.FavouriteApps
import uk.co.home.chromecastremote.protocol.Wire.nested
import uk.co.home.chromecastremote.protocol.Wire.string

class FavouriteProtocolTest {
    @Test fun customAppsUseTheirOwnSchemesWithoutPlayStoreOrPackageRestrictions() {
        assertEquals("nuvio://home", FavouriteApps.find("nuvio")!!.link)
        val smartTubeLink = FavouriteApps.find("smarttube")!!.link
        assertEquals("vnd.youtube.launch://youtube.com", smartTubeLink)
        // SmartTube IntentExtractor.isRootUrl recognises .com / .com/ / .com/launch=remote suffixes.
        assertTrue(smartTubeLink.endsWith(".com"))
        FavouriteApps.all.forEach { app ->
            val message = Wire.parse(RemoteProtocol.appLink(app.link)).nested(90)
            assertEquals(app.link, message.string(1))
            assertFalse(message.any { it.number == 2 || it.number == 3 })
        }
    }
    @Test fun emptyAppLinksAreRejectedBeforeAnythingIsSent() {
        assertThrows(IllegalArgumentException::class.java) { RemoteProtocol.appLink("") }
    }
}
