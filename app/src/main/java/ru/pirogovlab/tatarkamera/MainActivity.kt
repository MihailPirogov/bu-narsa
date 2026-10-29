package ru.pirogovlab.tatarkamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CrashLog.install(this)
        try { Ads.init(this) } catch (_: Throwable) {}
        val crash = CrashLog.take(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = RED, onPrimary = LINEN, surface = LINEN, onSurface = INK, background = LINEN, onBackground = INK, surfaceContainerLow = LINEN)) { CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = GOLOS)) {
            var report by remember { mutableStateOf(crash) }
            val r = report
            if (r != null) CrashScreen(r) { report = null } else Loader()
        } } }
    }
}

@Composable
fun Loader() {
    val ctx = LocalContext.current
    var dict by remember { mutableStateOf<Dictionary?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        thread { try { val d = Dictionary.load(ctx); ContextCompat.getMainExecutor(ctx).execute { dict = d } }
                 catch (t: Throwable) { CrashLog.write(ctx, "Словарь не загрузился", t); ContextCompat.getMainExecutor(ctx).execute { err = t.toString() } } }
    }
    val d = dict
    when {
        d != null -> App(d)
        err != null -> CrashScreen(err!!) {}
        else -> Box(Modifier.fillMaxSize().background(DEEP), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AMBER) }
    }
}

@Composable
fun CrashScreen(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(DEEP).padding(20.dp).padding(top = 30.dp)) {
        Text("Прошлый запуск завершился сбоем", color = RED, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Сделайте скриншот или скопируйте текст и пришлите разработчику.", color = INK, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, color = INK, fontSize = 11.sp, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = AMBER)) { Text("Скопировать", color = LINEN) }
            OutlinedButton(onClick = onClose) { Text("Продолжить", color = INK) }
        }
    }
}

// Палитра по образцам вышивки: красный по льняному холсту (см. Embroidery.kt)
private val GREEN = LINEN               // кружок (не выбран): холст + красный обод
private val AMBER = RED                 // акцент
private val DEEP = LINEN                // фон
private val CARD = Color(0xFFFBF2E0)
private val CREAM = LINEN
private val SUB = RED
private val SHADE = Color(0x99000000)
val GOLOS = FontFamily(Font(R.font.golos_400, FontWeight.Normal), Font(R.font.golos_500, FontWeight.Medium), Font(R.font.golos_700, FontWeight.Bold))

/** Полоса орнамента: вышитая лента с крючками (по образцу). */
@Composable
fun OrnamentStrip(modifier: Modifier = Modifier.fillMaxWidth().height(12.dp)) { BandRow(modifier) }
/** Текст в одну строку, шрифт уменьшается, пока не влезет по ширине (ничего не обрезается по высоте). */
@Composable
fun AutoText(text: String, color: Color, maxSp: Float, minSp: Float = 9f, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val base = LocalTextStyle.current.merge(TextStyle(color = color, fontWeight = weight, fontFamily = GOLOS))
        var sp = maxSp
        if (maxW != androidx.compose.ui.unit.Constraints.Infinity)
            while (sp > minSp && m.measure(text, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width > maxW * 0.9f) sp *= 0.92f
        Text(text, style = base.copy(fontSize = sp.coerceAtLeast(minSp).sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppLogo(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp)).background(RED), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer(scaleX = 1.45f, scaleY = 1.45f))
    }
}

const val WARN = "Распознавание может ошибаться. Проверяйте перевод."

enum class Screen { HOME, LIVE, SHOOT, RESULT, SETTINGS }

/** Новая главная метка показывается, только если продержалась holdMs. */
class Smoother(private val holdMs: Long = 700) {
    private var cand: String? = null; private var since = 0L
    fun push(e: Entry?, now: Long): Entry? {
        if (e?.label != cand) { cand = e?.label; since = now; return null }
        return if (e != null && now - since >= holdMs) e else null
    }
}

data class Frame(val w: Int, val h: Int, val items: List<Found>)

@Composable
fun App(dict: Dictionary) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var result by remember { mutableStateOf<Pair<Bitmap, List<Found>>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var stillRec by remember { mutableStateOf<Recognizer?>(null) }
    LaunchedEffect(Unit) { thread { val r = try { Recognizer(ctx, dict, stream = false) } catch (t: Throwable) { CrashLog.write(ctx, "Recognizer(photo) init failed", t); null }
        ContextCompat.getMainExecutor(ctx).execute { stillRec = r } } }
    DisposableEffect(Unit) { onDispose { stillRec?.close() } }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var pending by remember { mutableStateOf<Screen?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) pending?.let { s -> screen = s } }
    fun go(s: Screen) { if (granted) screen = s else { pending = s; perm.launch(Manifest.permission.CAMERA) } }
    fun analyze(bmp: Bitmap) {
        busy = true
        thread {
            val items = runCatching { stillRec?.detect(bmp) ?: emptyList() }.getOrDefault(emptyList())
            ContextCompat.getMainExecutor(ctx).execute { result = bmp to items; busy = false; screen = Screen.RESULT; Ads.recognitions++ }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadBitmap(ctx, uri)?.let { analyze(it) }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    BackHandler(screen != Screen.HOME) { screen = if (screen == Screen.RESULT) Screen.SHOOT else Screen.HOME }

    val prefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var warn by remember { mutableStateOf(!prefs.getBoolean("warn_shown", false)) }
    if (warn) AlertDialog(onDismissRequest = {}, containerColor = LINEN, icon = { AppLogo(52) },
        title = { Text(APP_TITLE, color = RED, fontWeight = FontWeight.Bold) }, text = { Column { Text(WARN, color = INK, fontSize = 16.sp) } },
        confirmButton = { Button(onClick = { prefs.edit().putBoolean("warn_shown", true).apply(); warn = false }, colors = ButtonDefaults.buttonColors(containerColor = RED)) { Text("Понятно", color = LINEN, fontWeight = FontWeight.Bold) } })
    Box(Modifier.fillMaxSize().background(DEEP)) {
        when (screen) {
            Screen.HOME -> Home(onLive = { go(Screen.LIVE) }, onShoot = { go(Screen.SHOOT) }, onGallery = pick, onSettings = { screen = Screen.SETTINGS })
            Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.HOME })
            Screen.LIVE, Screen.SHOOT -> LiveCamera(ctx, dict, live = screen == Screen.LIVE,
                onShot = { analyze(it) }, onGallery = pick, onBack = { screen = Screen.HOME },
                onMode = { screen = if (screen == Screen.LIVE) Screen.SHOOT else Screen.LIVE })
            Screen.RESULT -> result?.let { (b, items) -> ResultView(b, items, onAgain = { Ads.maybeInterstitial(ctx as? android.app.Activity) { screen = Screen.SHOOT } }, onBack = { screen = Screen.HOME }) }
        }
        if (busy) Box(Modifier.fillMaxSize().background(SHADE), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AMBER) }
    }
}

@Composable
fun Home(onLive: () -> Unit, onShoot: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(LINEN)) {
        Kaima(Modifier.fillMaxWidth().statusBarsPadding())
        Box(Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_settings), "Настройки", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).clip(CircleShape).clickable { onSettings() }.padding(10.dp).size(24.dp), tint = INK) }
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val compact = cfg.screenHeightDp < 720 || cfg.fontScale > 1.15f
        val landH = cfg.screenWidthDp > cfg.screenHeightDp
        Column(Modifier.weight(1f).fillMaxWidth().then(if (landH) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = if (compact) 20.dp else 32.dp, vertical = if (compact) 4.dp else 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            AppLogo(if (compact) 64 else 112)
            Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
            AutoText(APP_TITLE, INK, if (compact) 40f else 52f, 26f, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Наведи камеру — узнай, как это по-татарски\nКамераны юнәлт — татарча ничек дип бел", color = MUTE, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(if (compact) 80.dp else 112.dp).background(RED, CircleShape).clickable { onShoot() },
                contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_camera), "Снять фото", Modifier.size(48.dp), tint = LINEN) }
            Spacer(Modifier.height(10.dp))
            Text("Снять фото", color = INK, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Галерея", R.drawable.ic_gallery, Modifier.weight(1f), onGallery)
                PillButton("Живой режим", R.drawable.ic_live, Modifier.weight(1f), onLive)
            }
            Spacer(Modifier.weight(1f))
            Text(WARN, color = RED, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("PirogovLab · татар теле", color = MUTE, fontSize = 12.sp)
        }
        Column(Modifier.navigationBarsPadding().padding(bottom = 6.dp)) { TowelEnd(); AdBanner() }
    }
}
@Composable
fun PillButton(text: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(CARD).border(1.5.dp, RED, RoundedCornerShape(26.dp))
        .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = RED); Spacer(Modifier.width(8.dp))
        AutoText(text, INK, 15f, 8f, FontWeight.Medium, Modifier.weight(1f, fill = false))
    }
}
@Composable
fun NumDot(n: Int, size: Int = 26, selected: Boolean = false) {
    Box(Modifier.size(size.dp).background(if (selected) LINEN else RED, CircleShape),
        contentAlignment = Alignment.Center) {
        Text("$n", color = if (selected) RED else LINEN, fontWeight = FontWeight.Bold, fontSize = (size * 0.48).sp, lineHeight = (size * 0.5).sp)
    }
}
/** Раскладка номеров: по одному на объект, внутри своей рамки/маски, вне чужих (Placement), в координатах изображения. */
fun layoutDots(items: List<Found>, map: (PointF) -> Offset, r: Float, left: Float, top: Float, right: Float, bottom: Float, imgW: Float = 0f, imgH: Float = 0f, imgR: Float = 0f): List<Pair<Int, Offset>> {
    val pl = placeImage(items, imgW, imgH, imgR)
    return pl.map { (i, x, y) -> val c = map(PointF(x, y))
        i to Offset(c.x.coerceIn(left + r, maxOf(left + r, right - r)), c.y.coerceIn(top + r, maxOf(top + r, bottom - r))) }
}
fun placeImage(items: List<Found>, w: Float, h: Float, r: Float): List<Triple<Int, Float, Float>> =
    Placement.place(items.map { f -> PItem(f.box?.let { floatArrayOf(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }, f.point.x, f.point.y, f.outline) },
        if (w > 0) w else 1e6f, if (h > 0) h else 1e6f, r)

/** Полноэкранная разметка (FILL_CENTER / Crop). Выбранный номер: янтарная рамка, пульсирующий кружок, остальное затемнено.
 *  Нажатие на кружок выбирает слово. */
@Composable
fun Markers(fr: Frame, selected: Int, bottomInsetPx: Float, onSelect: (Int) -> Unit = {},
            fit: Boolean = false, zoom: Float = 1f, pan: Offset = Offset.Zero,
            onTransform: ((Offset, Float, Offset) -> Unit)? = null, onDoubleTap: (() -> Unit)? = null, shapes: Boolean = true, topPx: Float? = null) {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pulse = if (androidx.compose.ui.platform.LocalInspectionMode.current) 1f else androidx.compose.animation.core.rememberInfiniteTransition(label = "p").animateFloat(1f, 1.18f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(650), androidx.compose.animation.core.RepeatMode.Reverse), label = "s").value
    val placed = remember { mutableListOf<Pair<Int, Offset>>() }
    val cache = remember { HashMap<Triple<Frame, Int, Int>, List<Triple<Int, Float, Float>>>() }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val onSel by rememberUpdatedState(onSelect)
    val onTr by rememberUpdatedState(onTransform)
    val onDbl by rememberUpdatedState(onDoubleTap)
    var mod = Modifier.fillMaxSize()
    if (onTransform != null) mod = mod.pointerInput("tr") {
        detectTransformGestures { c, p, z, _ -> onTr?.invoke(c, z, p) }
    }
    Canvas(mod.pointerInput(onDoubleTap != null) {
        detectTapGestures(onDoubleTap = if (onDoubleTap != null) { _ -> onDbl?.invoke() } else null) { t ->
            val r = 24.dp.toPx()
            placed.minByOrNull { (it.second - t).getDistance() }?.takeIf { (it.second - t).getDistance() < r }?.let { onSel(it.first) }
        }
    }) {
        val base = if (fit) minOf(size.width / fr.w, size.height / fr.h) else maxOf(size.width / fr.w, size.height / fr.h)
        val cx = size.width / 2; val cy = size.height / 2
        // экран = (база - центр) * zoom + центр + pan  (совпадает с graphicsLayer изображения)
        val sc = base * zoom
        val dx = (size.width - fr.w * base) / 2 * zoom + cx * (1 - zoom) + pan.x
        val dy = (size.height - fr.h * base) / 2 * zoom + cy * (1 - zoom) + pan.y
        val hasSel = selected in fr.items.indices
        // контур выбранного объекта, без заливки
        (if (shapes) fr.items.getOrNull(selected)?.outline else null)?.let { o ->
            val path = androidx.compose.ui.graphics.Path()
            var i = 0; while (i + 3 < o.size) { path.moveTo(o[i] * sc + dx, o[i + 1] * sc + dy); path.lineTo(o[i + 2] * sc + dx, o[i + 3] * sc + dy); i += 4 }
            val sw = with(density) { 1.2.dp.toPx() }
            drawPath(path, Color.Black.copy(alpha = 0.3f), style = Stroke(sw * 1.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path, Color.White, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        if (shapes) fr.items.forEachIndexed { i, f -> f.box?.let { b ->
            val sel = i == selected
            val sw = with(density) { (if (sel) 1.2.dp else 0.8.dp).toPx() }
            drawRoundRect(if (sel) Color.White else Color.White.copy(alpha = if (hasSel) 0.15f else 0.3f), Offset(b.left * sc + dx, b.top * sc + dy),
                Size(b.width() * sc, b.height() * sc), CornerRadius(with(density) { 14.dp.toPx() }), style = Stroke(sw)) } }
        val r = with(density) { 10.5.dp.toPx() }
        val top = topPx ?: (statusTop + with(density) { 76.dp.toPx() }); val bottom = size.height - bottomInsetPx - with(density) { 6.dp.toPx() }
        val key = Triple(fr, (r * 1.25f / sc).toInt(), 0); val dots0 = cache[key] ?: placeImage(fr.items, fr.w.toFloat(), fr.h.toFloat(), r * 1.25f / sc).also { cache.clear(); cache[key] = it }
        val dots = dots0.map { (i, x, y) -> i to Offset((x * sc + dx).coerceIn(r, size.width - r), (y * sc + dy).coerceIn(top + r, maxOf(top + r, bottom - r))) }
        placed.clear(); placed.addAll(dots)
        dots.sortedBy { it.first == selected }.forEach { (i, c) ->
            val sel = i == selected
            val rr = if (sel) r * 1.2f * pulse else r
            val a = if (hasSel && !sel) 0.55f else 1f
            if (sel) drawCircle(Color.White, rr * 1.55f, c, style = Stroke(with(density) { 1.5.dp.toPx() }))
            drawCircle((if (sel) RED else LINEN).copy(alpha = a), rr, c); drawCircle((if (sel) LINEN else RED).copy(alpha = a), rr, c, style = Stroke(with(density) { 1.5.dp.toPx() }))
            val lay = measurer.measure("${i + 1}", TextStyle(color = (if (sel) LINEN else RED).copy(alpha = a), fontFamily = GOLOS, fontSize = if (sel) 12.sp else 11.sp, fontWeight = FontWeight.Bold))
            drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y - lay.size.height / 2f))
        }
    }
}

/** Лист-«полотенце»: крупное слово + перевод с ударением, лента пронумерованных слов. overlay — поверх камеры. */
@Composable
fun WordPanel(items: List<Found>, selected: Int, main: Entry?, onSelect: (Int) -> Unit, placeholder: String, overlay: Boolean = false) {
    var showDef by remember { mutableStateOf<Entry?>(null) }
    showDef?.let { d -> DefSheet(d) { showDef = null } }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
        val e = main
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.CenterStart) {
            if (e == null) Text(placeholder, color = MUTE, fontSize = 17.sp)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (selected >= 0 && items.size > 1) Text("№ ${selected + 1} · ${items.size} ${plural(items.size)}", color = MUTE, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AutoText(e.title, INK, if (overlay) 46f else 58f, 14f, FontWeight.Bold, Modifier.weight(1f, fill = false))
                        if (e.needsCheck && !e.noMhr) Text(" ⚠", color = RED, fontSize = 15.sp)
                    }
                    if (e.noMhr) Text("нет перевода", color = MUTE, fontSize = 15.sp)
                    else AutoText(e.ruStress, RED, 22f, 11f, FontWeight.Medium)
                }
                Spacer(Modifier.width(10.dp))
                if (e.def != null) Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, INK, CircleShape)
                    .clickable { showDef = e }, contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_info), "Толкование", Modifier.size(22.dp), tint = INK) }
            }
        }
        if (items.isNotEmpty()) {
            LaunchedEffect(selected) { }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE1DAD4)))
            Spacer(Modifier.height(4.dp))
            items.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { col, f ->
                        val i = row * 2 + col; val sel = i == selected
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(i) }.padding(vertical = 3.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = RED, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                AutoText(f.entry.title, if (sel) RED else INK, 17f, 11f, FontWeight.Bold)
                                Text(if (f.entry.noMhr) "нет перевода" else f.entry.ru, color = MUTE, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

fun plural(n: Int): String = when { n % 10 == 1 && n % 100 != 11 -> "предмет"; n % 10 in 2..4 && n % 100 !in 12..14 -> "предмета"; else -> "предметов" }

/** Оформление листа-полотенца: холст, кайма-меандр по краям, лента сверху. */
fun Modifier.towelSheet(): Modifier = this.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(LINEN)
@Composable
fun BigBtn(text: String, icon: Int, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).background(if (primary) RED else Color.Transparent)
        .border(1.5.dp, if (primary) RED else INK, RoundedCornerShape(25.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val c = if (primary) LINEN else INK
        Icon(painterResource(icon), text.ifEmpty { null }, Modifier.size(18.dp), tint = c); if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
        if (text.isNotEmpty()) AutoText(text, c, 14f, 8f, if (primary) FontWeight.Bold else FontWeight.Medium, Modifier.weight(1f, fill = false))
    }
}
@Composable
fun TopBar(onBack: () -> Unit, modeLabel: String?, onMode: (() -> Unit)?, title: Boolean = false, light: Boolean = false) {
    Column(Modifier.fillMaxWidth().then(if (light) Modifier else Modifier.statusBarsPadding())) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 8.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (light) Color.Transparent else SHADE).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_back), "Назад", Modifier.size(24.dp), tint = if (light) INK else Color.White) }
            if (title) { Spacer(Modifier.width(12.dp)); Text(APP_TITLE, color = if (light) INK else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            if (modeLabel != null && onMode != null) Row(Modifier.clip(RoundedCornerShape(20.dp)).background(SHADE).border(1.dp, LINEN.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .clickable { onMode() }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (modeLabel == "Фото") R.drawable.ic_camera else R.drawable.ic_live), null, Modifier.size(18.dp), tint = LINEN)
                Spacer(Modifier.width(6.dp)); Text(modeLabel.replace("📷 ", "").replace("◉ ", ""), color = Color.White, fontSize = 15.sp) }
        }
    }
}
private fun upright(proxy: ImageProxy): Bitmap {
    val raw = proxy.toBitmap(); val rot = proxy.imageInfo.rotationDegrees
    return if (rot == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
}

@Composable
fun LiveCamera(ctx: Context, dict: Dictionary, live: Boolean, onShot: (Bitmap) -> Unit, onGallery: () -> Unit, onBack: () -> Unit, onMode: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    var frame by remember { mutableStateOf<Frame?>(null) }
    var main by remember { mutableStateOf<Entry?>(null) }
    var userWord by remember { mutableStateOf<String?>(null) }
    var panelH by remember { mutableFloatStateOf(0f) }
    val liveState = rememberUpdatedState(live)
    val lastFrame = remember { arrayOfNulls<Bitmap>(1) }
    val recHolder = remember { arrayOfNulls<Recognizer>(1) }
    val smoother = remember { Smoother() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.execute { recHolder[0]?.close(); recHolder[0] = null }; executor.shutdown() } }
    LaunchedEffect(live) { if (!live) { frame = null; main = null } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            val view = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerF = ProcessCameraProvider.getInstance(c)
            providerF.addListener({
                val provider = providerF.get()
                val rs = androidx.camera.core.resolutionselector.ResolutionSelector.Builder().setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                val preview = Preview.Builder().setResolutionSelector(rs).build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setResolutionSelector(rs).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val bmp = try { upright(proxy) } finally { proxy.close() }
                    lastFrame[0] = bmp
                    if (!liveState.value) return@setAnalyzer
                    if (recHolder[0] == null) recHolder[0] = try { Recognizer(c, dict, stream = true) } catch (t: Throwable) { CrashLog.write(c, "Recognizer(live) init failed", t); null }
                    val items = runCatching { recHolder[0]?.detect(bmp) ?: emptyList() }.getOrDefault(emptyList())
                    val shown = smoother.push(items.firstOrNull()?.entry, System.currentTimeMillis())
                    ContextCompat.getMainExecutor(c).execute {
                        frame = Frame(bmp.width, bmp.height, items)
                        if (shown != null) main = shown
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(c))
            view
        })
        if (live) {
            val fr = frame
            val userIdx = fr?.items?.indexOfFirst { it.entry.title == userWord } ?: -1
            val sel = if (userIdx >= 0) userIdx else fr?.items?.indexOfFirst { it.entry.label == main?.label } ?: -1
            val shown = if (userIdx >= 0) fr!!.items[userIdx].entry else main
            val pick: (Int) -> Unit = { i -> userWord = fr?.items?.getOrNull(i)?.entry?.title }
            if (fr != null) Markers(fr, sel, panelH, pick, shapes = false)
            Box(Modifier.align(Alignment.BottomCenter).onGloballyPositioned { panelH = it.size.height.toFloat() }) {
                Column(Modifier.fillMaxWidth().towelSheet().padding(top = 10.dp).navigationBarsPadding()) { WordPanel(fr?.items.orEmpty(), sel, shown, onSelect = pick, placeholder = "Наведите камеру на предмет", overlay = true); Spacer(Modifier.height(8.dp)) }
            }
        } else {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Box(Modifier.size(56.dp).background(SHADE, CircleShape).border(1.dp, LINEN.copy(alpha = 0.6f), CircleShape).clickable { onGallery() }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_gallery), "Галерея", Modifier.size(26.dp), tint = LINEN) }
                    Box(Modifier.size(84.dp).background(AMBER, CircleShape).border(5.dp, CREAM, CircleShape)
                        .clickable { lastFrame[0]?.let(onShot) }, contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_camera), "Снять", Modifier.size(34.dp), tint = LINEN) }
                    Spacer(Modifier.size(56.dp))
                }
                Spacer(Modifier.height(24.dp))
                AdBanner()
            }
        }
        TopBar(onBack, if (live) "Фото" else "Живой", onMode)
    }
}

@Composable
fun ResultView(bmp: Bitmap, items: List<Found>, onAgain: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableIntStateOf(if (items.isEmpty()) -1 else 0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Column(Modifier.fillMaxSize().background(LINEN)) {
        Kaima(Modifier.fillMaxWidth().statusBarsPadding())
        TopBar(onBack, null, null, title = true, light = true)
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val land = cfg.screenWidthDp > cfg.screenHeightDp
        val compactBtns = cfg.screenWidthDp / cfg.fontScale < 340f || cfg.fontScale > 1.15f && cfg.screenWidthDp < 420
        val photoBox: @Composable (Modifier) -> Unit = { pm ->
        // фото вписано целиком и по центру свободной области; слова — ниже, не на фото
        Box(pm.padding(horizontal = 16.dp).padding(top = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEDE9E4)).onGloballyPositioned { boxSize = it.size }) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y),
                contentScale = ContentScale.Fit)
            Markers(Frame(bmp.width, bmp.height, items), sel, 0f, { sel = it }, fit = true, zoom = zoom, pan = pan,
                onTransform = { c, z, p ->
                    val nz = (zoom * z).coerceIn(1f, 6f); val k = nz / zoom
                    val ctr = Offset(boxSize.width / 2f, boxSize.height / 2f)
                    var np = (pan - (c - ctr)) * k + (c - ctr) + p
                    val w = boxSize.width.toFloat(); val h = boxSize.height.toFloat()
                    val base = if (bmp.width > 0) minOf(w / bmp.width, h / bmp.height) else 1f
                    val mx = maxOf(0f, (bmp.width * base * nz - w) / 2); val my = maxOf(0f, (bmp.height * base * nz - h) / 2)
                    np = Offset(np.x.coerceIn(-mx, mx), np.y.coerceIn(-my, my))
                    zoom = nz; pan = if (nz <= 1.001f) Offset.Zero else np
                }, onDoubleTap = { zoom = 1f; pan = Offset.Zero }, topPx = 0f)
        }
        }
        val panelBody: @Composable () -> Unit = {
        WordPanel(items, sel, items.getOrNull(sel)?.entry, onSelect = { sel = it }, placeholder = "Ничего знакомого не найдено")
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigBtn(if (compactBtns) "" else "Сохранить", R.drawable.ic_save, Modifier.weight(if (compactBtns) 0.6f else 1f), false) { val ok = saveToGallery(ctx, annotate(bmp, items)); Toast.makeText(ctx, if (ok) "Сохранено" else "Не удалось сохранить", Toast.LENGTH_SHORT).show() }
            BigBtn(if (compactBtns) "" else "Поделиться", R.drawable.ic_share, Modifier.weight(if (compactBtns) 0.6f else 1f), false) { share(ctx, annotate(bmp, items), items.mapIndexed { i, f -> "${i + 1}. ${f.entry.title} — ${if (f.entry.noMhr) "нет перевода" else f.entry.ru}" }.joinToString("\n") + "\n\n" + SHARE_TAIL) }
            BigBtn("Ещё раз", R.drawable.ic_refresh, Modifier.weight(1f), true, onAgain)
        }
        }
        if (land) Row(Modifier.weight(1f).fillMaxWidth()) {
            photoBox(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 4.dp)) { panelBody() }
        } else {
            photoBox(Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.fillMaxWidth().heightIn(max = (cfg.screenHeightDp * 0.55f).dp).verticalScroll(rememberScrollState()).padding(top = 10.dp)) { panelBody() }
        }
        Column(Modifier.navigationBarsPadding().padding(bottom = 4.dp)) { TowelEnd(); AdBanner() }
    }
}
const val APP_LINK = "https://play.google.com/store/apps/details?id=ru.pirogovlab.tatarkamera"
const val APP_TITLE = "Бу нәрсә?"
const val SHARE_TAIL = "Бу нәрсә? — татарская камера-словарь. Скачать: $APP_LINK"

/** Сохраняемая картинка: номера (Placement, координаты исходника), подписи-лейблы, легенда в стиле полотенца. */
fun annotate(src: Bitmap, found: List<Found>): Bitmap {
    val k = maxOf(src.width, src.height) / 1000f
    val rowH = 34 * k; val cols = if (found.size > 4) 2 else 1
    val legendH = if (found.isEmpty()) 40 * k else 70 * k + ((found.size + cols - 1) / cols) * rowH
    val out = Bitmap.createBitmap(src.width, src.height + legendH.toInt(), Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(out)
    c.drawBitmap(src, 0f, 0f, null)
    val red = 0xFF9D0F10.toInt(); val linen = 0xFFFAF8F5.toInt(); val ink = 0xFF1A1414.toInt(); val pink = 0xFFDC5A6D.toInt()
    val r = 13 * k
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * k; color = linen }
    val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = linen; textSize = 15 * k; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 17 * k; typeface = Typeface.DEFAULT_BOLD }
    val dots = placeImage(found, src.width.toFloat(), src.height.toFloat(), r * 1.15f)
    val labels = ArrayList<RectF>(); dots.forEach { (_, x, y) -> labels += RectF(x - r, y - r, x + r, y + r) }
    dots.forEach { (i, x, y) ->
        // подпись справа или слева от номера, не перекрывая другие номера и подписи
        val t = found[i].entry.title; val tw = lbl.measureText(t) + 14 * k; val th = 24 * k
        val opts = listOf(RectF(x + r + 3 * k, y - th / 2, x + r + 3 * k + tw, y + th / 2), RectF(x - r - 3 * k - tw, y - th / 2, x - r - 3 * k, y + th / 2),
            RectF(x - tw / 2, y + r + 3 * k, x + tw / 2, y + r + 3 * k + th), RectF(x - tw / 2, y - r - 3 * k - th, x + tw / 2, y - r - 3 * k))
        val ok = opts.firstOrNull { o -> o.left >= 0 && o.right <= src.width && o.top >= 0 && o.bottom <= src.height && labels.none { RectF.intersects(it, o) } }
        if (ok != null) {
            labels += ok
            fill.color = linen and 0xEEFFFFFF.toInt(); c.drawRoundRect(ok, th / 2, th / 2, fill)
            c.drawText(t, ok.left + 7 * k, ok.centerY() + 6 * k, lbl)
        }
    }
    dots.forEach { (i, x, y) -> fill.color = red; c.drawCircle(x, y, r, fill); c.drawCircle(x, y, r, ring); c.drawText("${i + 1}", x, y + 5.5f * k, num) }
    // легенда: холст, вышитые линии, номер → слово — перевод
    val top = src.height.toFloat()
    fill.color = linen; c.drawRect(0f, top, out.width.toFloat(), out.height.toFloat(), fill)
    fill.color = red; c.drawRect(0f, top, out.width.toFloat(), top + 3 * k, fill)
    val word = Paint(lbl).apply { textSize = 20 * k }; val tr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red; textSize = 17 * k }
    val colW = out.width / cols.toFloat()
    found.forEachIndexed { i, f ->
        val cx = 20 * k + (i % cols) * colW; val cy = top + 40 * k + (i / cols) * rowH
        fill.color = red; c.drawCircle(cx + 11 * k, cy, 11 * k, fill); c.drawText("${i + 1}", cx + 11 * k, cy + 5 * k, Paint(num).apply { textSize = 13 * k })
        c.drawText(f.entry.title, cx + 30 * k, cy + 7 * k, word)
        c.drawText("— " + (if (f.entry.noMhr) "нет перевода" else f.entry.ru), cx + 36 * k + word.measureText(f.entry.title), cy + 7 * k, tr)
    }
    val sig = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pink; textSize = 14 * k; textAlign = Paint.Align.RIGHT }
    c.drawText("Бу нәрсә? · pirogovlab.ru", out.width - 14 * k, out.height - 12 * k, sig)
    return out
}

fun loadBitmap(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val b = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; d.setTargetSampleSize(2) }
    else {
        val raw = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        val deg = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 } } }.getOrDefault(0)
        if (deg == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
    }
    val s = 1600f / maxOf(b.width, b.height)
    if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
}.getOrNull()

fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean = runCatching {
    val cv = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "mut-kamera-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Бу нәрсә")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    true
}.getOrDefault(false)

fun share(ctx: Context, bmp: Bitmap, text: String) {
    val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
    val f = File(dir, "mut-kamera.jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    val uri = FileProvider.getUriForFile(ctx, "ru.pirogovlab.tatarkamera.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Поделиться"))
}

/** Нижний лист с толкованием слова из словаря. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefSheet(e: Entry, onClose: () -> Unit) {
    val d = e.def ?: return
    ModalBottomSheet(onDismissRequest = onClose, containerColor = LINEN, contentColor = INK) {
        
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(d.stress.ifBlank { e.word }, color = INK, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(e.ruStress, color = RED, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            d.senses.forEachIndexed { i, s ->
                Row { Text("${i + 1}. ", color = AMBER, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text((if (s.lab.isNotBlank()) s.lab + " " else "") + s.d, color = INK, fontSize = 16.sp) }
                s.ex.forEach { x -> Text("• ${x.src} — ${x.ru}", color = MUTE, fontSize = 14.sp, modifier = Modifier.padding(start = 18.dp, top = 4.dp)) }
                Spacer(Modifier.height(10.dp))
            }
            Text("Источник: ${d.source}", color = MUTE, fontSize = 12.sp)
        }
    }
}


/** Кеш = временные снимки и файлы (cacheDir, externalCacheDir, *.tmp моделей); распакованные модели не трогаем. */
object CacheTool {
    private fun targets(ctx: android.content.Context): List<File> =
        listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).flatMap { it.listFiles()?.toList() ?: emptyList() } +
        (File(ctx.filesDir, "models").listFiles { f -> f.name.endsWith(".tmp") }?.toList() ?: emptyList())
    fun size(ctx: android.content.Context): Long = targets(ctx).sumOf { f -> f.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
    fun clear(ctx: android.content.Context): Long { val n = size(ctx); targets(ctx).forEach { runCatching { it.deleteRecursively() } }; return n - size(ctx) }
    fun fmt(b: Long) = if (b >= 1 shl 20) String.format("%.1f МБ", b / 1048576.0) else String.format("%.0f КБ", b / 1024.0)
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var size by remember { mutableStateOf(CacheTool.size(ctx)) }
    var ask by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    Column(Modifier.fillMaxSize().background(LINEN)) {
        Kaima(Modifier.fillMaxWidth().statusBarsPadding())
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_back), "Назад", Modifier.clip(CircleShape).clickable { onBack() }.padding(12.dp).size(24.dp), tint = INK)
            Text("Настройки", color = INK, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Кеш", color = INK, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Временные снимки и файлы: ${CacheTool.fmt(size)}. Модели распознавания не удаляются.", color = MUTE, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            BigBtn("Очистить кеш", R.drawable.ic_trash, Modifier.fillMaxWidth(), true) { ask = true }
            msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = RED, fontSize = 14.sp) }
            Spacer(Modifier.height(28.dp))
            Text("О приложении", color = INK, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("«Бу нәрсә?» $ver — узнай, как называется предмет по-татарски. Работает без интернета.", color = INK, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(WARN, color = RED, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("Слова: Викисловарь (ru.wiktionary.org, en.wiktionary.org) и Википедия/Викиданные, CC BY-SA 4.0; слова, найденные только в одном источнике, помечены ⚠ «проверить».", color = MUTE, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("Снимки сохраняются в галерею: Pictures/Бу нәрсә", color = MUTE, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("PirogovLab", color = MUTE, fontSize = 13.sp)
        }
        Column(Modifier.navigationBarsPadding()) { TowelEnd() }
    }
    if (ask) androidx.compose.material3.AlertDialog(onDismissRequest = { ask = false },
        title = { Text("Очистить кеш?") }, text = { Text("Будут удалены временные снимки (${CacheTool.fmt(size)}). Сохранённые в галерею фото и модели останутся.") },
        confirmButton = { androidx.compose.material3.TextButton({ ask = false; val f = CacheTool.clear(ctx); size = CacheTool.size(ctx); msg = "Освобождено: ${CacheTool.fmt(f)}" }) { Text("Очистить", color = RED) } },
        dismissButton = { androidx.compose.material3.TextButton({ ask = false }) { Text("Отмена", color = INK) } },
        containerColor = LINEN)
}
