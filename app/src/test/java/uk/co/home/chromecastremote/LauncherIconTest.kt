package uk.co.home.chromecastremote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    @Test fun installedIconHasFlatColoursAndAnUnclippedRemote() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(R.mipmap.ic_launcher, app.applicationInfo.icon)
        val icon = requireNotNull(app.getDrawable(app.applicationInfo.icon))
        assertTrue("The installed app must use the adaptive launcher icon", icon is AdaptiveIconDrawable)
        icon.setBounds(0, 0, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.draw(Canvas(bitmap))
        save(bitmap, "launcher-system-mask")

        val charcoal = Color.rgb(25, 28, 34)
        val blue = Color.rgb(121, 169, 245)
        val white = Color.rgb(241, 244, 250)
        val palette = setOf(charcoal, blue, white)
        val seen = mutableSetOf<Int>()
        var bluePixels = 0
        for (y in 2 until 510) for (x in 2 until 510) {
            val pixel = bitmap.getPixel(x, y)
            if (pixel == blue) bluePixels++
            // Ignore antialiased edges, but reject shading or extra colours in solid interiors.
            if (Color.alpha(pixel) == 255 &&
                bitmap.getPixel(x - 1, y) == pixel && bitmap.getPixel(x + 1, y) == pixel &&
                bitmap.getPixel(x, y - 1) == pixel && bitmap.getPixel(x, y + 1) == pixel) {
                assertTrue("Unexpected flat fill: ${Integer.toHexString(pixel)}", pixel in palette)
                seen += pixel
            }
        }
        assertEquals(palette, seen)
        assertTrue("The enlarged remote should be prominent", bluePixels > 35000)
        assertEquals("The top of the remote must be visible", blue, bitmap.getPixel(256, 64))
        assertEquals("The bottom of the remote must be visible", blue, bitmap.getPixel(256, 448))

        val adaptive = icon as AdaptiveIconDrawable
        for (round in listOf(false, true)) {
            val preview = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(preview)
            val mask = Path().apply {
                if (round) addCircle(256f, 256f, 256f, Path.Direction.CW)
                else addRoundRect(RectF(0f, 0f, 512f, 512f), 108f, 108f, Path.Direction.CW)
            }
            canvas.clipPath(mask)
            // Adaptive layers include 18dp overscan on each side of the visible 72dp.
            for (layer in listOf(adaptive.background, adaptive.foreground)) {
                layer.setBounds(-128, -128, 640, 640)
                layer.draw(canvas)
            }
            assertEquals(blue, preview.getPixel(256, 64))
            assertEquals(blue, preview.getPixel(256, 448))
            save(preview, if (round) "launcher-circle" else "launcher-rounded-square")
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/outputs/design/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
