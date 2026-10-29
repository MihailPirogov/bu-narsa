package ru.pirogovlab.tatarkamera

import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.Rect
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensTest {
    // имя, ширина/высота в px, dpi, fontScale
    data class Cfg(val name: String, val w: Int, val h: Int, val dpi: Int, val font: Float = 1f)
    private val cfgs = listOf(
        Cfg("480x854_240dpi", 480, 854, 240), Cfg("720x1280_320dpi", 720, 1280, 320), Cfg("1080x1920_420dpi", 1080, 1920, 420),
        Cfg("1080x2400_420dpi", 1080, 2400, 420), Cfg("1440x3200_560dpi", 1440, 3200, 560), Cfg("tablet_1600x2560_320dpi", 1600, 2560, 320),
        Cfg("land_2400x1080_420dpi", 2400, 1080, 420), Cfg("land_1280x720_320dpi", 1280, 720, 320),
        Cfg("font1.3_1080x2400_420dpi", 1080, 2400, 420, 1.3f), Cfg("font1.3_720x1280_320dpi", 720, 1280, 320, 1.3f), Cfg("font1.3_480x854_240dpi", 480, 854, 240, 1.3f))
    private val root = File(System.getProperty("user.dir")!!).let { if (File(it, "store").exists()) it else it.parentFile!! }
    private val out = File(root, "tests/screens").apply { mkdirs() }

    private fun setCfg(c: Cfg) {
        val wdp = c.w * 160 / c.dpi; val hdp = c.h * 160 / c.dpi
        RuntimeEnvironment.setQualifiers("w${wdp}dp-h${hdp}dp-${if (c.w > c.h) "land" else "port"}-${c.dpi}dpi")
        RuntimeEnvironment.setFontScale(c.font)
    }
    private fun shot(c: Cfg, screen: String, content: @Composable () -> Unit) {
        setCfg(c); captureRoboImage(File(out, "${c.name}__$screen.png").path) { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { content() } }
    }
    private fun items(scene: String): List<Found> {
        val a = JSONObject(File(root, "tests/oid_scenes.json").readText()).getJSONArray(scene)
        return (0 until a.length()).map { i -> val o = a.getJSONObject(i); val b = o.getJSONArray("box")
            val r = Rect(b.getDouble(0).toInt().coerceAtLeast(0), b.getDouble(1).toInt().coerceAtLeast(0), b.getDouble(2).toInt(), b.getDouble(3).toInt())
            Found(r, PointF(r.exactCenterX(), r.exactCenterY()), Entry(o.getString("label"), o.getString("mhr"), o.getString("ru"), o.getString("ru_stress"), o.getBoolean("check")), o.getString("label"), o.getDouble("score").toFloat()) }
    }
    private fun longItems(): List<Found> = listOf("небоскрёб" to "күктерәү", "стира́льная маши́на" to "кер юу машинасы", "спа́льня" to "йокы бүлмәсе", "холоди́льник" to "суыткыч", "ко́шка" to "мәче", "ча́йник" to "чәйнек", "ско́рая по́мощь" to "", "бу́блик" to "")
        .mapIndexed { i, (ru, m) -> val r = Rect(40 + i * 130, 80 + (i % 3) * 230, 160 + i * 130, 300 + (i % 3) * 230)
            Found(r, PointF(r.exactCenterX(), r.exactCenterY()), Entry(ru, m, ru.replace("\u0301", ""), ru, true), ru, 0.9f) }

    @Test fun dictionaryLoads() {
        val d = Dictionary.load(RuntimeEnvironment.getApplication())
        listOf("cat", "dog", "human face", "ant", "kettle", "snowplow", "car").forEach { assertTrue(it, d.lookup(it) != null) }
        val labels = RuntimeEnvironment.getApplication().assets.open("oid_labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        val miss = labels.filter { d.lookup(it) == null }
        File(root, "tests/oid_labels_without_entry.txt").writeText(miss.joinToString("\n"))
        assertTrue("labels=${labels.size}", labels.size == 601)
    }
    @Test fun screens() {
        val street = BitmapFactory.decodeFile(File(root, "store/scenes/street.jpg").path)
        val pets = BitmapFactory.decodeFile(File(root, "store/scenes/pets.jpg").path)
        val desk = BitmapFactory.decodeFile(File(root, "store/scenes/desk.jpg").path)
        val kitchen = BitmapFactory.decodeFile(File(root, "store/scenes/kitchen.jpg").path)
        val only = System.getProperty("cfg")
        for (c in cfgs.filter { only == null || it.name == only }) {
            shot(c, "1_home") { Home({}, {}, {}, {}) }
            shot(c, "2_settings") { SettingsScreen {} }
            shot(c, "3_result_pets") { ResultView(pets, items("pets"), {}, {}) }
            shot(c, "4_result_desk") { ResultView(desk, items("desk"), {}, {}) }
            shot(c, "5_result_street") { ResultView(street, items("street"), {}, {}) }
            shot(c, "6_result_long") { ResultView(kitchen, longItems(), {}, {}) }
        }
    }
}
