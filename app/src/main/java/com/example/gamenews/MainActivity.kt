package com.example.gamenews

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class Article(
    val source: String, val title: String, val link: String,
    val image: String?, val html: String, val date: Long
)

val FEEDS = listOf(
    "Insider Gaming" to "https://insider-gaming.com/feed/",
    "VGC" to "https://www.videogameschronicle.com/feed/",
    "Gematsu" to "https://www.gematsu.com/feed",
    "IGN" to "https://feeds.feedburner.com/ign/news",
    "Eurogamer" to "https://www.eurogamer.net/feed",
    "GameSpot" to "https://www.gamespot.com/feeds/news/",
    "Kotaku" to "https://kotaku.com/rss",
    "Wccftech" to "https://wccftech.com/feed/",
    "GamingBolt" to "https://gamingbolt.com/feed"
)

fun parseDate(s: String): Long {
    val fmts = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX")
    for (f in fmts) try {
        return SimpleDateFormat(f, Locale.ENGLISH).parse(s.trim())!!.time
    } catch (_: Exception) {}
    return 0L
}

fun fetchFeed(source: String, url: String): List<Article> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) GameNews/1.0")
    conn.connectTimeout = 15000; conn.readTimeout = 15000
    val out = mutableListOf<Article>()
    conn.inputStream.use { stream ->
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(stream, null)
        var inItem = false
        var title = ""; var link = ""; var desc = ""; var content = ""
        var img: String? = null; var date = 0L
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = p.name
                if (n == "item" || n == "entry") {
                    inItem = true; title = ""; link = ""; desc = ""; content = ""; img = null; date = 0L
                } else if (inItem) {
                    try {
                        when (n) {
                            "title" -> title = p.nextText()
                            "link" -> {
                                val h = p.getAttributeValue(null, "href")
                                link = h ?: p.nextText()
                            }
                            "description", "summary" -> desc = p.nextText()
                            "content:encoded", "content" -> content = p.nextText()
                            "pubDate", "published", "updated" -> date = parseDate(p.nextText())
                            "media:content", "media:thumbnail", "enclosure" -> {
                                val u = p.getAttributeValue(null, "url")
                                val t = p.getAttributeValue(null, "type") ?: "image"
                                if (u != null && img == null && t.startsWith("image")) img = u
                            }
                        }
                    } catch (_: Exception) {}
                }
            } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
                inItem = false
                val html = if (content.isNotBlank()) content else desc
                val image = img ?: Regex("<img[^>]+src=[\"']([^\"']+)").find(html)?.groupValues?.get(1)
                out.add(Article(source, title.trim(), link.trim(), image, html, date))
            }
            ev = p.next()
        }
    }
    return out.take(15)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Article?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        articles = withContext(Dispatchers.IO) {
            FEEDS.map { (name, url) ->
                async { try { fetchFeed(name, url) } catch (e: Exception) { emptyList() } }
            }.awaitAll().flatten().sortedByDescending { it.date }
        }
        loading = false
    }

    BackHandler(enabled = selected != null) { selected = null }

    if (selected != null) {
        Detail(selected!!) { selected = null }
    } else {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Gaming News | أخبار الألعاب") },
                actions = { TextButton(onClick = { reload++ }) { Text("تحديث / Refresh") } }
            )
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(articles) { a -> Tile(a) { selected = a } }
                }
            }
        }
    }
}

@Composable
fun Tile(a: Article, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().height(220.dp).clickable { onClick() }) {
        Column {
            Text(
                a.source, Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp
            )
            AsyncImage(
                a.image, null, Modifier.fillMaxWidth().height(100.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                a.title, Modifier.padding(8.dp), fontSize = 13.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(a: Article, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(a.source) },
            navigationIcon = { TextButton(onClick = onBack) { Text("← رجوع") } }
        )
    }) { pad ->
        AndroidView(
            modifier = Modifier.padding(pad).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0xFF121212.toInt())
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?, r: android.webkit.WebResourceRequest?
                        ) = true
                    }
                    val page = """
                        <html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
                        <style>body{background:#121212;color:#eee;font-family:sans-serif;
                        padding:12px;line-height:1.6}img,video,iframe{max-width:100%;height:auto}
                        a{color:#8ab4f8;pointer-events:none}</style></head><body>
                        <h2>${a.title}</h2>${a.html}</body></html>
                    """.trimIndent()
                    loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
                }
            }
        )
    }
}
