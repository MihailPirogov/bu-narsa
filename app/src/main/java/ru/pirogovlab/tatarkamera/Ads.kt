package ru.pirogovlab.tatarkamera

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/** Яндекс реклама (как в играх PirogovLab, SDK 8.6.0). Любая ошибка рекламы глушится — офлайн-распознавание не страдает. */
object Ads {
    // Яндекс Реклама: приложение 20302179
    const val BANNER_ID = "R-M-20302179-1"
    const val INTER_ID = "R-M-20302179-2"
    private const val TAG = "BuNarsaAds"
    private const val MIN_GAP_MS = 3 * 60 * 1000L
    private const val MIN_RECOGNITIONS = 3
    private val h = Handler(Looper.getMainLooper())
    @Volatile var ready = false; private set
    private var loader: InterstitialAdLoader? = null
    private var inter: InterstitialAd? = null
    private var loading = false; private var fails = 0; private var showing = false
    private var lastShown = 0L
    var recognitions = 0

    fun init(a: Activity) {
        try {
            loader = InterstitialAdLoader(a.applicationContext)
            YandexAds.initialize(a.applicationContext) { ready = true; loadInter() }
        } catch (t: Throwable) { Log.w(TAG, "init", t) }
    }

    private fun loadInter() {
        try {
            if (!ready || inter != null || loading) return
            val l = loader ?: return
            loading = true
            l.loadAd(AdRequest.Builder(INTER_ID).build(), object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) { loading = false; inter = ad; fails = 0 }
                override fun onAdFailedToLoad(error: AdRequestError) { loading = false; if (++fails <= 5) h.postDelayed({ loadInter() }, 20000L * fails) }
            })
        } catch (t: Throwable) { loading = false; Log.w(TAG, "loadInter", t) }
    }

    /** Межстраничная по «Ещё раз»: не раньше 3-го распознавания и не чаще раза в 3 минуты. then() вызывается всегда. */
    fun maybeInterstitial(a: Activity?, then: () -> Unit) {
        try {
            val ad = inter; val now = System.currentTimeMillis()
            if (a == null || ad == null || showing || recognitions < MIN_RECOGNITIONS || now - lastShown < MIN_GAP_MS) { loadInter(); then(); return }
            inter = null; showing = true; var done = false
            val end = { if (!done) { done = true; showing = false; lastShown = System.currentTimeMillis(); try { ad.setAdEventListener(null) } catch (_: Throwable) {}; then(); loadInter() } }
            ad.setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdShown() {}
                override fun onAdFailedToShow(adError: AdError) { end() }
                override fun onAdDismissed() { end() }
                override fun onAdClicked() {}
                override fun onAdImpression(impressionData: ImpressionData?) {}
            })
            ad.show(a)
        } catch (t: Throwable) { Log.w(TAG, "show", t); showing = false; then() }
    }
}

/** Адаптивный липкий баннер; пока не загружен (или нет сети) — занимает 0 высоты. */
@Composable
fun AdBanner(modifier: Modifier = Modifier.fillMaxWidth()) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        androidx.compose.foundation.layout.Box(modifier.height(50.dp).background(androidx.compose.ui.graphics.Color(0x22000000)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("баннер 320×50", color = androidx.compose.ui.graphics.Color.Gray) }
        return
    }
    var view by remember { mutableStateOf<BannerAdView?>(null) }
    DisposableEffect(Unit) { onDispose { try { view?.destroy() } catch (_: Throwable) {} } }
    AndroidView(modifier = modifier, factory = { c: Context ->
        val f = android.widget.FrameLayout(c)
        try {
            val b = BannerAdView(c)
            val d = c.resources.displayMetrics
            b.setAdSize(BannerAdSize.sticky(c, Math.round(d.widthPixels / d.density)))
            b.setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {}
                override fun onAdFailedToLoad(error: AdRequestError) {}
                override fun onAdClicked() {}
                override fun onImpression(impressionData: ImpressionData?) {}
            })
            f.addView(b, android.widget.FrameLayout.LayoutParams(-1, -2))
            b.loadAd(AdRequest.Builder(Ads.BANNER_ID).build())
            view = b
        } catch (t: Throwable) { Log.w("BuNarsaAds", "banner", t) }
        f
    })
}
