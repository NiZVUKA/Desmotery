package com.example.nlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppItem(val label: String, val pkg: String)
class Ic(val color: ImageBitmap, val mono: ImageBitmap)

class Th(
    val name: String, val c1: Color, val c2: Color, val text: Color, val accent: Color,
    val wall: Boolean = false, val light: Boolean = false
)

val THEMES = listOf(
    Th("Обои", Color.Transparent, Color.Transparent, Color.White, Color(0xFFFFD54F), wall = true),
    Th("Slate", Color(0xFF3A3F3C), Color(0xFF0A2A7A), Color.White, Color(0xFF8FB4FF)),
    Th("Graphite", Color(0xFF0B0F17), Color(0xFF1B2433), Color.White, Color(0xFFE8E2D6)),
    Th("Cobalt", Color(0xFF1A181F), Color(0xFF0D0C11), Color.White, Color(0xFF1E9BFF)),
    Th("Aurora", Color(0xFF0F2027), Color(0xFF2C5364), Color.White, Color(0xFF7CF5D0)),
    Th("AMOLED", Color.Black, Color(0xFF0B0B12), Color.White, Color(0xFFB388FF)),
    Th("Sunset", Color(0xFF1D0B3F), Color(0xFFE2606F), Color.White, Color(0xFFFFC38A)),
    Th("Forest", Color(0xFF0B1F14), Color(0xFF1E4D2B), Color.White, Color(0xFFB5E48C)),
    Th("Ocean", Color(0xFF021B3A), Color(0xFF0A5C8C), Color.White, Color(0xFF8BD3FF)),
    Th("Paper", Color(0xFFF7F3EC), Color(0xFFE3DCD0), Color(0xFF1C1B1A), Color(0xFFE76F51), light = true),
)
val ACCENTS: List<Color?> = listOf(
    null, Color(0xFFFF6B6B), Color(0xFFFFB84D), Color(0xFFFFE66D),
    Color(0xFF7CF5D0), Color(0xFF6CB4FF), Color(0xFFB388FF), Color(0xFFFF8AD8)
)
val COND = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
val LIGHT = FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))

class Cfg(private val p: SharedPreferences) {
    private val m = mutableStateMapOf<String, Float>()
    fun f(k: String, d: Float): Float = m[k] ?: p.getFloat(k, d)
    fun i(k: String, d: Int) = f(k, d.toFloat()).toInt()
    fun b(k: String, d: Boolean) = f(k, if (d) 1f else 0f) > 0.5f
    fun set(k: String, v: Float) { m[k] = v; p.edit().putFloat(k, v).apply() }
    fun set(k: String, v: Boolean) = set(k, if (v) 1f else 0f)
    fun set(k: String, v: Int) = set(k, v.toFloat())
}

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { Launcher(tick) } }
    }
    override fun onResume() { super.onResume(); tick++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); tick++ }
}

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun renderIcon(ctx: Context, pkg: String): Ic? = try {
    val d = ctx.packageManager.getApplicationIcon(pkg)
    val s = 128
    val color = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(color)
    val path = Path().apply { addRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), s * .28f, s * .28f, Path.Direction.CW) }
    c.clipPath(path)
    d.setBounds(0, 0, s, s)
    d.draw(c)

    // монохромная версия (силуэт) для стиля как в Niagara
    val mono = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val mc = Canvas(mono)
    val layer: Drawable?
    val pad: Int
    if (d is AdaptiveIconDrawable) {
        layer = (if (Build.VERSION.SDK_INT >= 33) d.monochrome else null) ?: d.foreground
        pad = (s * 0.25f).toInt()
    } else { layer = d; pad = 0 }
    layer?.setBounds(-pad, -pad, s + pad, s + pad)
    layer?.draw(mc)
    Ic(color.asImageBitmap(), mono.asImageBitmap())
} catch (e: Exception) { null }

private fun letterOf(a: AppItem): Char =
    a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

private fun Context.open(a: AppItem) { packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it) } }

private fun expandShade(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (e: Exception) { }
}

private fun Modifier.railTouch(key: Any, onTouch: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val d = awaitFirstDown()
            d.consume()
            onTouch(d.position.y)
            do {
                val e = awaitPointerEvent()
                e.changes.firstOrNull()?.let { onTouch(it.position.y); it.consume() }
            } while (e.changes.any { it.pressed })
            onEnd()
        }
    }

@Composable
fun Launcher(tick: Int) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val prefs = remember { ctx.getSharedPreferences("dm", Context.MODE_PRIVATE) }
    val cfg = remember { Cfg(prefs) }
    val t = THEMES[cfg.i("theme", 1).coerceIn(0, THEMES.lastIndex)]
    val accent = ACCENTS.getOrNull(cfg.i("accent", 0)) ?: t.accent

    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    val icons = remember { mutableStateMapOf<String, Ic>() }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) {
            val list = loadApps(ctx)
            apps = list
            list.forEach { a -> if (a.pkg !in icons) renderIcon(ctx, a.pkg)?.let { icons[a.pkg] = it } }
        }
    }

    var favs by remember { mutableStateOf(prefs.getString("favs", "")!!.split(",").filter { it.isNotEmpty() }) }
    fun saveFavs(l: List<String>) { favs = l; prefs.edit().putString("favs", l.joinToString(",")).apply() }
    val favApps = favs.mapNotNull { p -> apps.find { it.pkg == p } }

    var showList by remember { mutableStateOf(false) }
    var searchFocus by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppItem?>(null) }
    var pick by remember { mutableStateOf<Char?>(null) }
    LaunchedEffect(tick) { showList = false; pick = null }
    BackHandler(showSettings) { showSettings = false }
    BackHandler(showList && !showSettings) { showList = false }
    BackHandler(pick != null && !showList && !showSettings) { pick = null }

    SideEffect {
        WindowInsetsControllerCompat((ctx as Activity).window, view).apply {
            isAppearanceLightStatusBars = t.light
            isAppearanceLightNavigationBars = t.light
        }
    }

    val rootBg = if (t.wall) Modifier.background(Color.Black.copy(alpha = cfg.f("dim", .35f)))
    else Modifier.background(Brush.verticalGradient(listOf(t.c1, t.c2)))
    val overlayBg = if (t.wall) Modifier.background(Color.Black.copy(alpha = .86f))
    else Modifier.background(Brush.verticalGradient(listOf(t.c1.copy(alpha = .97f), t.c2.copy(alpha = .97f))))

    Box(Modifier.fillMaxSize().then(rootBg)) {
        HomeScreen(t, accent, cfg, apps, favApps, icons, pick, onPick = { pick = it },
            onClick = { ctx.open(it) }, onLong = { menuFor = it },
            onOpen = { searchFocus = false; showList = true },
            onSearch = { searchFocus = true; showList = true },
            onShade = { expandShade(ctx) }, onSettings = { showSettings = true })

        AnimatedVisibility(
            showList,
            enter = slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
        ) {
            Box(Modifier.fillMaxSize().then(overlayBg)) {
                Drawer(t, accent, cfg, apps, icons, favs, searchFocus, onClick = { ctx.open(it) }, onLong = { menuFor = it })
            }
        }

        AnimatedVisibility(
            showSettings,
            enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(160))
        ) {
            Box(Modifier.fillMaxSize().then(overlayBg)) {
                SettingsScreen(t, accent, cfg, onClose = { showSettings = false },
                    onDefault = { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) })
            }
        }
    }

    menuFor?.let { app ->
        val pinned = app.pkg in favs
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = if (t.light) Color(0xFFFFFBF5) else Color(0xFF1E1E24),
            shape = RoundedCornerShape(28.dp),
            title = { Text(app.label, color = t.text) },
            text = {
                Column {
                    TextButton(onClick = {
                        saveFavs(if (pinned) favs - app.pkg else (favs + app.pkg).takeLast(7)); menuFor = null
                    }) { Text(if (pinned) "Открепить с главного" else "Закрепить на главном", color = accent) }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        menuFor = null
                    }) { Text("О приложении", color = accent) }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))); menuFor = null
                    }) { Text("Удалить", color = accent) }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Закрыть", color = t.text.copy(alpha = .7f)) } }
        )
    }
}

@Composable
fun IconView(ic: Ic?, style: Int, size: Dp, t: Th, accent: Color) {
    if (ic == null || style == 0) return
    when (style) {
        1 -> Image(ic.mono, null, Modifier.size(size), colorFilter = ColorFilter.tint(t.text))
        2 -> Image(ic.color, null, Modifier.size(size))
        else -> Box(Modifier.size(size).clip(CircleShape).background(t.text.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Image(ic.mono, null, Modifier.size(size * 0.58f), colorFilter = ColorFilter.tint(accent))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    a: AppItem, ic: Ic?, style: Int, al: Int, size: Float, t: Th, accent: Color, endPad: Dp,
    onClick: () -> Unit, onLong: () -> Unit
) {
    val has = style != 0 && ic != null
    val iconSize = (size * 1.5f).dp
    Row(
        Modifier.fillMaxWidth().padding(end = endPad).clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalArrangement = when (al) { 1 -> Arrangement.Center; 2 -> Arrangement.End; else -> Arrangement.Start },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (al == 2) {
            Text(a.label, color = t.text, fontSize = size.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (has) { Spacer(Modifier.width(16.dp)); IconView(ic, style, iconSize, t, accent) }
        } else {
            if (has) { IconView(ic, style, iconSize, t, accent); Spacer(Modifier.width(16.dp)) }
            Text(a.label, color = t.text, fontSize = size.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private fun greeting(h: Int) = when {
    h < 5 -> "Доброй ночи"; h < 12 -> "Доброе утро"; h < 18 -> "Добрый день"; else -> "Добрый вечер"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    t: Th, accent: Color, cfg: Cfg, apps: List<AppItem>, favs: List<AppItem>, icons: Map<String, Ic>,
    pick: Char?, onPick: (Char?) -> Unit,
    onClick: (AppItem) -> Unit, onLong: (AppItem) -> Unit,
    onOpen: () -> Unit, onSearch: () -> Unit, onShade: () -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lv >= 0) battery = lv * 100 / sc
            }
            delay(5_000)
        }
    }
    val h24 = cfg.b("h24", true)
    val style = cfg.i("iconStyle", 1)
    val al = cfg.i("align", 0)
    val clockStyle = cfg.i("clock", 0)
    val size = cfg.f("favSize", 28f)
    val rail = cfg.b("rail", true)
    val ha = when (al) { 1 -> Alignment.CenterHorizontally; 2 -> Alignment.End; else -> Alignment.Start }
    val loc = Locale.getDefault()
    val time = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", loc).format(now)
    val hour = SimpleDateFormat("H", loc).format(now).toInt()

    val letters = remember(apps) {
        listOf('☆') + apps.map(::letterOf).distinct().sortedWith(compareBy({ it == '#' }, { it }))
    }
    var railH by remember { mutableFloatStateOf(1f) }
    var touching by remember { mutableStateOf(false) }
    fun jump(y: Float) {
        val l = letters[(y / railH * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        onPick(if (l == '☆') null else l)
    }
    val shown = if (pick == null) favs else apps.filter { letterOf(it) == pick }.take(8)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = { if (dy < -100) onOpen() else if (dy > 100 && cfg.b("shade", true)) onShade() }
                    ) { _, d -> dy += d }
                }
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { onPick(null) }, onLongClick = onSettings)
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = ha,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = ha) {
                if (cfg.b("greet", true)) Text(greeting(hour), color = t.text.copy(alpha = .7f), fontSize = 15.sp)
                when (clockStyle) {
                    1 -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(SimpleDateFormat("MMM", loc).format(now).uppercase().trimEnd('.'),
                            color = t.text.copy(alpha = .55f), fontSize = 76.sp, fontFamily = COND, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text(SimpleDateFormat("d", loc).format(now), color = accent, fontSize = 76.sp,
                            fontFamily = COND, fontWeight = FontWeight.Medium)
                    }
                    2 -> Text(SimpleDateFormat("EEEE", loc).format(now).uppercase(), color = t.text, fontSize = 36.sp,
                        fontFamily = COND, letterSpacing = 10.sp, modifier = Modifier.padding(vertical = 8.dp))
                    else -> Text(time, color = t.text, fontSize = 84.sp, fontFamily = LIGHT, letterSpacing = (-2).sp)
                }
                Text(
                    when (clockStyle) {
                        1 -> "$time · " + SimpleDateFormat("EEEE", loc).format(now)
                        2 -> SimpleDateFormat("d MMMM", loc).format(now) + " · $time"
                        else -> SimpleDateFormat("EEEE, d MMMM", loc).format(now)
                    }.replaceFirstChar { it.uppercase() },
                    color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                if (cfg.b("batt", true) && battery >= 0)
                    Text("Батарея $battery%", color = t.text.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(14.dp))
                Text("Поиск", color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(t.text.copy(alpha = .1f))
                        .clickable { onSearch() }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
            Column(horizontalAlignment = ha, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (shown.isEmpty()) Text("Долгое нажатие на приложение в списке — закрепить его здесь.",
                    color = t.text.copy(alpha = .55f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
                shown.forEach { a ->
                    AppRow(a, icons[a.pkg], style, al, size, t, accent, if (rail) 36.dp else 0.dp,
                        onClick = { onClick(a) }, onLong = { onLong(a) })
                }
                Box(Modifier.padding(top = 18.dp).align(Alignment.CenterHorizontally).width(44.dp).height(4.dp)
                    .clip(CircleShape).background(t.text.copy(alpha = .3f)).clickable { onOpen() })
            }
        }

        Text("⚙", color = t.text.copy(alpha = .6f), fontSize = 24.sp,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                .clip(CircleShape).clickable { onSettings() }.padding(10.dp))

        if (rail && letters.size > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp, top = 90.dp).fillMaxHeight(.58f).width(32.dp)
                    .onSizeChanged { railH = it.height.toFloat() }
                    .railTouch(letters, { touching = true; jump(it) }, { touching = false }),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEach { l ->
                    val sel = (l == '☆' && pick == null) || l == pick
                    Text(l.toString(), fontSize = if (letters.size > 30) 10.sp else 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) accent else t.text.copy(alpha = .55f))
                }
            }
            if (touching && pick != null) Text(pick.toString(), color = accent, fontSize = 64.sp, fontFamily = COND,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 52.dp, top = 90.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(
    t: Th, accent: Color, cfg: Cfg, apps: List<AppItem>, icons: Map<String, Ic>, favs: List<String>, focus: Boolean,
    onClick: (AppItem) -> Unit, onLong: (AppItem) -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) { if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), true) } }
    val grid = cfg.b("grid", true)
    val style = cfg.i("iconStyle", 1)
    val ls = rememberLazyListState()
    val gs = rememberLazyGridState()
    val letters = remember(apps) { apps.map(::letterOf).distinct().sortedWith(compareBy({ it == '#' }, { it })) }
    var railH by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf<Char?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focus) { delay(300); try { fr.requestFocus() } catch (e: Exception) { } } }

    val jumpTo: (Float) -> Unit = { y ->
        if (letters.isNotEmpty()) {
            val l = letters[(y / railH * letters.size).toInt().coerceIn(0, letters.lastIndex)]
            if (l != active) {
                active = l
                val target = list.indexOfFirst { letterOf(it) == l }
                if (target >= 0) {
                    if (grid) gs.requestScrollToItem(target) else ls.requestScrollToItem(target)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            BasicTextField(
                q, { q = it }, singleLine = true,
                textStyle = TextStyle(color = t.text, fontSize = 17.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { list.singleOrNull()?.let(onClick) }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.text.copy(alpha = .1f))
                        .padding(horizontal = 20.dp, vertical = 14.dp)) {
                        if (q.isEmpty()) Text("Поиск приложений и в интернете", color = t.text.copy(alpha = .45f), fontSize = 17.sp)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).focusRequester(fr)
            )
            if (q.isNotBlank()) Text("Искать «${q.trim()}» в интернете", color = accent, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().clickable {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { }
                }.padding(horizontal = 28.dp, vertical = 10.dp))
            if (grid) {
                val ds = if (style == 0) 2 else style
                LazyVerticalGrid(
                    GridCells.Fixed(4), state = gs,
                    contentPadding = PaddingValues(start = 12.dp, end = 40.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(list, key = { it.pkg }) { a ->
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .combinedClickable(onClick = { onClick(a) }, onLongClick = { onLong(a) })
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val ic = icons[a.pkg]
                            if (ic != null) IconView(ic, ds, 56.dp, t, accent)
                            else Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(t.text.copy(alpha = .1f)))
                            Spacer(Modifier.height(6.dp))
                            Text(a.label, color = if (a.pkg in favs) accent else t.text.copy(alpha = .9f),
                                fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                LazyColumn(state = ls, contentPadding = PaddingValues(start = 28.dp, end = 56.dp, top = 4.dp, bottom = 24.dp)) {
                    items(list, key = { it.pkg }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .combinedClickable(onClick = { onClick(a) }, onLongClick = { onLong(a) })
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ic = icons[a.pkg]
                            if (style != 0 && ic != null) { IconView(ic, style, 40.dp, t, accent); Spacer(Modifier.width(16.dp)) }
                            Text(a.label, maxLines = 1, fontSize = 22.sp, color = if (a.pkg in favs) accent else t.text)
                        }
                    }
                }
            }
        }

        if (q.isEmpty() && letters.size > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd).statusBarsPadding().navigationBarsPadding()
                    .padding(top = 72.dp).fillMaxHeight().width(36.dp)
                    .onSizeChanged { railH = it.height.toFloat() }
                    .railTouch(letters to grid, { jumpTo(it) }, { active = null }),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEach {
                    Text(it.toString(), fontSize = if (letters.size > 30) 10.sp else 12.sp,
                        fontWeight = if (it == active) FontWeight.Bold else FontWeight.Normal,
                        color = if (it == active) accent else t.text.copy(alpha = .55f))
                }
            }
        }
        active?.let {
            Box(Modifier.align(Alignment.Center).size(88.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text(it.toString(), fontSize = 42.sp, fontWeight = FontWeight.Medium, color = Color.Black.copy(alpha = .85f))
            }
        }
    }
}

@Composable
fun SettingsScreen(t: Th, accent: Color, cfg: Cfg, onClose: () -> Unit, onDefault: () -> Unit) {
    val sel = cfg.i("theme", 1)
    val selAcc = cfg.i("accent", 0)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Desmotery", color = t.text, fontSize = 30.sp, fontFamily = LIGHT, modifier = Modifier.weight(1f))
            Text("Готово", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onClose() }.padding(12.dp))
        }
        SectionTitle("Тема", t)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            THEMES.forEachIndexed { i, th ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { cfg.set("theme", i) }) {
                    val brush = if (th.wall) Brush.verticalGradient(listOf(Color(0xFF6B6B6B), Color(0xFF222222)))
                    else Brush.verticalGradient(listOf(th.c1, th.c2))
                    Box(Modifier.size(56.dp).clip(CircleShape).background(brush)
                        .border(if (i == sel) 3.dp else 1.dp, if (i == sel) accent else t.text.copy(alpha = .25f), CircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text(th.name, color = t.text.copy(alpha = if (i == sel) 1f else .6f), fontSize = 12.sp)
                }
            }
        }
        SectionTitle("Акцентный цвет", t)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ACCENTS.forEachIndexed { i, c ->
                Box(Modifier.size(40.dp).clip(CircleShape).background(c ?: t.accent)
                    .border(if (i == selAcc) 3.dp else 0.dp, t.text, CircleShape)
                    .clickable { cfg.set("accent", i) }, contentAlignment = Alignment.Center) {
                    if (c == null) Text("A", color = Color.Black.copy(alpha = .7f), fontWeight = FontWeight.Bold)
                }
            }
        }
        SectionTitle("Часы", t)
        ChipRow(listOf("Время", "Дата", "День недели"), cfg.i("clock", 0), t, accent) { cfg.set("clock", it) }
        SwitchRow("Приветствие", cfg.b("greet", true), t, accent) { cfg.set("greet", it) }
        SwitchRow("Уровень батареи", cfg.b("batt", true), t, accent) { cfg.set("batt", it) }
        SwitchRow("24-часовой формат", cfg.b("h24", true), t, accent) { cfg.set("h24", it) }
        SectionTitle("Список избранных", t)
        ChipRow(listOf("Слева", "По центру", "Справа"), cfg.i("align", 0), t, accent) { cfg.set("align", it) }
        Spacer(Modifier.height(12.dp))
        ChipRow(listOf("Только текст", "Моно", "Цветные", "В кружке"), cfg.i("iconStyle", 1), t, accent) { cfg.set("iconStyle", it) }
        SliderRow("Размер текста", cfg.f("favSize", 28f), 20f..40f, t, accent) { cfg.set("favSize", it) }
        SectionTitle("Управление", t)
        SwitchRow("Алфавит справа на главном", cfg.b("rail", true), t, accent) { cfg.set("rail", it) }
        SwitchRow("Свайп вниз — шторка уведомлений", cfg.b("shade", true), t, accent) { cfg.set("shade", it) }
        SwitchRow("Сетка в списке всех приложений", cfg.b("grid", true), t, accent) { cfg.set("grid", it) }
        SliderRow("Затемнение обоев (тема «Обои»)", cfg.f("dim", .35f), 0f..0.8f, t, accent) { cfg.set("dim", it) }
        Spacer(Modifier.height(20.dp))
        Text("Сделать лаунчером по умолчанию", color = Color.Black.copy(alpha = .85f), fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(accent).clickable { onDefault() }
                .padding(horizontal = 22.dp, vertical = 14.dp))
    }
}

@Composable
fun SectionTitle(s: String, t: Th) =
    Text(s.uppercase(), color = t.text.copy(alpha = .5f), fontSize = 12.sp, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))

@Composable
fun ChipRow(opts: List<String>, sel: Int, t: Th, accent: Color, onSel: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        opts.forEachIndexed { i, o ->
            Text(o, fontSize = 14.sp, color = if (i == sel) Color.Black.copy(alpha = .85f) else t.text,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (i == sel) accent else t.text.copy(alpha = .1f))
                    .clickable { onSel(i) }.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, v: Boolean, t: Th, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = t.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(v, onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White))
    }
}

@Composable
fun SliderRow(title: String, v: Float, range: ClosedFloatingPointRange<Float>, t: Th, accent: Color, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, color = t.text, fontSize = 16.sp)
        Slider(v, onChange, valueRange = range, colors = SliderDefaults.colors(
            thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = t.text.copy(alpha = .2f)))
    }
}
