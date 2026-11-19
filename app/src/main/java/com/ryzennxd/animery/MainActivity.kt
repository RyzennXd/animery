package com.ryzennxd.animery

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** animeRy - by RyzennXd */
class MainActivity : AppCompatActivity() {

    data class Src(val name: String, val url: String, val custom: Boolean = false)

    private val cats = listOf("Anime", "Donghua", "Manhwa", "Manhua", "Manga", "18+")
    private val icons = listOf("🎬", "🐉", "📖", "📚", "🈵", "🔞")

    // Sumber bawaan (legal). Tambah sumber lain lewat tombol "+" di app.
    private val defaults: Map<String, List<Src>> = mapOf(
        "Anime" to listOf(
            Src("Crunchyroll", "https://www.crunchyroll.com"),
            Src("Muse Asia", "https://m.youtube.com/@MuseIndonesia"),
            Src("AniList", "https://anilist.co")
        ),
        "Donghua" to listOf(
            Src("Bilibili", "https://www.bilibili.tv"),
            Src("iQIYI", "https://www.iq.com"),
            Src("WeTV", "https://wetv.vip")
        ),
        "Manhwa" to listOf(
            Src("Webtoon", "https://www.webtoons.com"),
            Src("Tapas", "https://tapas.io"),
            Src("MangaDex", "https://mangadex.org")
        ),
        "Manhua" to listOf(
            Src("Webtoon", "https://www.webtoons.com"),
            Src("Bilibili Comics", "https://www.bilibilicomics.com"),
            Src("MangaDex", "https://mangadex.org")
        ),
        "Manga" to listOf(
            Src("MANGA Plus", "https://mangaplus.shueisha.co.jp"),
            Src("MangaDex", "https://mangadex.org"),
            Src("VIZ", "https://www.viz.com")
        ),
        "18+" to emptyList()
    )

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var chipRow: LinearLayout
    private lateinit var navItems: List<TextView>
    private lateinit var root: FrameLayout
    private lateinit var favBtn: TextView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var curCat = 0
    private val prefs by lazy { getSharedPreferences("animery", Context.MODE_PRIVATE) }

    // Daftar host iklan/popup yang diblokir
    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "exosrv.com", "juicyads.com",
        "trafficjunky.net", "adsterra.com", "highperformanceformat.com", "clickadu.com", "hilltopads.net",
        "onclickads.net", "adnxs.com", "taboola.com", "outbrain.com", "histats.com", "mgid.com",
        "revcontent.com", "a-ads.com", "ad-maven.com", "adcash.com", "yllix.com", "tsyndicate.com"
    )
    private val adCss = """
        iframe[src*="ads"],ins.adsbygoogle,[id^="google_ads"],[class*="ad-banner"],[class*="adsbox"],
        [id*="popup-ad"],[class*="popup-ad"],.ads,.advert,.advertisement{display:none!important}
    """.trimIndent().replace("\n", "")

    private fun customSources(cat: String): List<Src> =
        (prefs.getStringSet("custom", emptySet()) ?: emptySet()).mapNotNull {
            val p = it.split("|", limit = 3); if (p.size == 3 && p[0] == cat) Src(p[1], p[2], true) else null
        }

    private fun sources(cat: String) = (defaults[cat] ?: emptyList()) + customSources(cat)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0F0F14")) }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(8), dp(6))
        }
        header.addView(TextView(this).apply {
            text = "anime"; setTextColor(Color.WHITE); textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Ry"; setTextColor(Color.parseColor("#FF4D8D")); textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fun hbtn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t; textSize = 20f; setPadding(dp(9), dp(6), dp(9), dp(6)); setOnClickListener { onClick() }
        }
        header.addView(hbtn("＋") { addSourceDialog() })
        header.addView(hbtn("⟳") { web.reload() })
        header.addView(hbtn("⌂") { loadSource(sources(cats[curCat]).firstOrNull()) })
        favBtn = hbtn("☆") { toggleFavorite() }
        header.addView(favBtn)
        header.addView(hbtn("🕘") { openLibrary() })
        col.addView(header)

        // Source chips
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(2), dp(10), dp(8)) }
        scroll.addView(chipRow)
        col.addView(scroll)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        col.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        web = WebView(this)
        setupWeb()
        col.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#15151C")); setPadding(0, dp(6), 0, dp(6))
        }
        navItems = cats.mapIndexed { i, c ->
            TextView(this).apply {
                text = "${icons[i]}\n$c"; gravity = Gravity.CENTER; textSize = 11f
                setOnClickListener { selectCategory(i) }
            }.also { nav.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        }
        col.addView(nav)

        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        selectCategory(0)
    }

    private fun selectCategory(i: Int) {
        if (cats[i] == "18+" && !prefs.getBoolean("age_ok", false)) {
            AlertDialog.Builder(this).setTitle("Konten 18+")
                .setMessage("Bagian ini khusus dewasa. Apakah kamu berusia 18 tahun ke atas?")
                .setPositiveButton("Ya, saya 18+") { _, _ -> prefs.edit().putBoolean("age_ok", true).apply(); selectCategory(i) }
                .setNegativeButton("Tidak", null).show()
            return
        }
        curCat = i
        navItems.forEachIndexed { idx, tv ->
            tv.setTextColor(if (idx == i) Color.parseColor("#FF4D8D") else Color.parseColor("#9A9AAA"))
        }
        renderChips()
        loadSource(sources(cats[i]).firstOrNull())
    }

    private fun renderChips() {
        chipRow.removeAllViews()
        val list = sources(cats[curCat])
        if (list.isEmpty()) {
            chipRow.addView(TextView(this).apply {
                text = "Belum ada sumber. Tekan ＋ untuk menambah link website."; setTextColor(Color.parseColor("#9A9AAA")); textSize = 13f
            })
            web.loadData("<body style='background:#0F0F14;color:#aaa;font-family:sans-serif;text-align:center;padding-top:30vh'>Tekan ＋ untuk menambah sumber ${cats[curCat]}</body>", "text/html", "utf-8")
            return
        }
        list.forEach { s ->
            chipRow.addView(TextView(this).apply {
                text = s.name; setTextColor(Color.WHITE); textSize = 13f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#252532")) }
                setOnClickListener { loadSource(s) }
                setOnLongClickListener {
                    if (s.custom) AlertDialog.Builder(this@MainActivity).setTitle("Hapus ${s.name}?")
                        .setPositiveButton("Hapus") { _, _ -> removeCustom(cats[curCat], s); renderChips() }
                        .setNegativeButton("Batal", null).show()
                    true
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
    }

    private fun loadSource(s: Src?) { if (s != null) web.loadUrl(s.url) }

    private fun addSourceDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val n = EditText(this).apply { hint = "Nama (mis. Situs Saya)" }
        val u = EditText(this).apply { hint = "https://contoh.com" }
        box.addView(n); box.addView(u)
        AlertDialog.Builder(this).setTitle("Tambah sumber ke ${cats[curCat]}").setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                var url = u.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "https://$url"
                    val set = (prefs.getStringSet("custom", emptySet()) ?: emptySet()).toMutableSet()
                    set.add("${cats[curCat]}|${n.text.toString().ifBlank { url }.replace("|", " ")}|$url")
                    prefs.edit().putStringSet("custom", set).apply()
                    renderChips(); web.loadUrl(url)
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun removeCustom(cat: String, s: Src) {
        val set = (prefs.getStringSet("custom", emptySet()) ?: emptySet()).toMutableSet()
        set.remove("$cat|${s.name}|${s.url}")
        prefs.edit().putStringSet("custom", set).apply()
    }

    // ---------- Favorit & Riwayat ----------
    data class Item(val title: String, val url: String)

    private fun readList(key: String): MutableList<Item> =
        (prefs.getString(key, "") ?: "").split("\n").mapNotNull {
            val p = it.split("\t"); if (p.size == 2 && p[1].isNotBlank()) Item(p[0], p[1]) else null
        }.toMutableList()

    private fun writeList(key: String, list: List<Item>) =
        prefs.edit().putString(key, list.joinToString("\n") { it.title.replace("\t", " ").replace("\n", " ") + "\t" + it.url }).apply()

    private fun addHistory(title: String, url: String) {
        val l = readList("hist"); l.removeAll { it.url == url }; l.add(0, Item(title, url))
        writeList("hist", l.take(100))
    }

    private fun updateStar(url: String?) {
        val fav = readList("fav").any { it.url == url }
        favBtn.text = if (fav) "★" else "☆"
        favBtn.setTextColor(if (fav) Color.parseColor("#FFC107") else Color.WHITE)
    }

    private fun toggleFavorite() {
        val url = web.url ?: return
        if (!url.startsWith("http")) return
        val l = readList("fav")
        if (l.any { it.url == url }) { l.removeAll { it.url == url }; toast("Dihapus dari favorit") }
        else { l.add(0, Item(web.title ?: url, url)); toast("Ditambahkan ke favorit ★") }
        writeList("fav", l); updateStar(url)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun openLibrary() {
        AlertDialog.Builder(this).setTitle("Koleksi")
            .setPositiveButton("★ Favorit") { _, _ -> showList("fav", "Favorit") }
            .setNegativeButton("🕘 Riwayat") { _, _ -> showList("hist", "Riwayat baca/tonton") }
            .setNeutralButton("Tutup", null).show()
    }

    private fun showList(key: String, title: String) {
        val l = readList(key)
        if (l.isEmpty()) { toast("$title masih kosong"); return }
        val d = AlertDialog.Builder(this).setTitle("$title (tahan lama untuk hapus)")
            .setItems(l.map { it.title.take(60) }.toTypedArray()) { _, i -> web.loadUrl(l[i].url) }
            .setNegativeButton("Tutup", null)
            .also { if (key == "hist") it.setNeutralButton("Hapus semua") { _, _ -> writeList("hist", emptyList()); toast("Riwayat dihapus") } }
            .create()
        d.show()
        d.listView.setOnItemLongClickListener { _, _, i, _ ->
            l.removeAt(i); writeList(key, l); d.dismiss(); updateStar(web.url); toast("Dihapus"); true
        }
    }

    private fun isBlocked(host: String?) = host != null && blockedHosts.any { host == it || host.endsWith(".$it") }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true; useWideViewPort = true; builtInZoomControls = true; displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                if (isBlocked(r.url.host)) WebResourceResponse("text/plain", "utf-8", null) else null

            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sch = r.url.scheme ?: return true
                return when {
                    sch != "http" && sch != "https" -> true          // blok intent:// & app redirect
                    isBlocked(r.url.host) -> true
                    else -> false
                }
            }
            override fun onPageFinished(v: WebView, url: String?) {
                if (url != null && url.startsWith("http")) { addHistory(v.title ?: url, url); updateStar(url) }
                v.evaluateJavascript("(function(){var s=document.createElement('style');s.innerHTML='$adCss';document.head.appendChild(s);})()", null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p; progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            }
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, m: android.os.Message?) = false // blok popup
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                customView = view; customCb = cb
                root.addView(view, FrameLayout.LayoutParams(-1, -1)); web.visibility = View.GONE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }; customView = null; customCb?.onCustomViewHidden()
                web.visibility = View.VISIBLE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> web.webChromeClient?.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }
}
