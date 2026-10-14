package br.com.whitney.meuiptv

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mantém os painéis (WebViews) que a pessoa abriu vivos em memória — igual
 * abas de navegador — enquanto o processo do app estiver rodando, e guarda
 * o suficiente em disco (ordem das abas, URL atual e o estado de navegação
 * de cada WebView) para reconstruir tudo do jeito que estava se o sistema
 * matar o app em segundo plano.
 *
 * O login em si não precisa ser restaurado "na mão": os cookies da
 * WebView já são persistidos pelo próprio CookieManager do Android.
 */
object PanelTabManager {

    data class Tab(val serviceId: String, var nome: String, var url: String)

    private const val PREFS = "meuiptv_abas"
    private const val KEY_ORDEM = "ordem"
    private fun chaveEstado(id: String) = "estado_$id"

    private val ordem = mutableListOf<Tab>()
    private val webViews = mutableMapOf<String, WebView>()
    private var carregouDisco = false

    /** Último serviço focado — sobrevive a recriações da Activity (ex.: troca de tema). */
    var focoAtual: String? = null

    @Synchronized
    private fun garantirCarregado(ctx: Context) {
        if (carregouDisco) return
        carregouDisco = true
        val raw = prefs(ctx).getString(KEY_ORDEM, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                ordem.add(Tab(o.getString("id"), o.optString("nome"), o.optString("url")))
            }
        }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun abas(ctx: Context): List<Tab> {
        garantirCarregado(ctx)
        return ordem.toList()
    }

    fun abrirOuFocar(ctx: Context, service: Service) {
        garantirCarregado(ctx)
        if (ordem.none { it.serviceId == service.id }) {
            ordem.add(Tab(service.id, service.name, service.panelUrl ?: "about:blank"))
            persistirOrdem(ctx)
        }
        focoAtual = service.id
    }

    fun fechar(ctx: Context, serviceId: String) {
        garantirCarregado(ctx)
        ordem.removeAll { it.serviceId == serviceId }
        webViews.remove(serviceId)?.let { webView ->
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        prefs(ctx).edit().remove(chaveEstado(serviceId)).apply()
        persistirOrdem(ctx)
    }

    fun webViewDe(serviceId: String?): WebView? = serviceId?.let { webViews[it] }

    fun atualizarUrl(ctx: Context, serviceId: String, url: String) {
        ordem.find { it.serviceId == serviceId }?.url = url
        persistirOrdem(ctx)
    }

    private fun persistirOrdem(ctx: Context) {
        val arr = JSONArray()
        ordem.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.serviceId)
                put("nome", t.nome)
                put("url", t.url)
            })
        }
        prefs(ctx).edit().putString(KEY_ORDEM, arr.toString()).apply()
    }

    @Suppress("SetJavaScriptEnabled")
    fun obterOuCriarWebView(ctx: Context, tab: Tab, client: WebViewClient): WebView {
        val existente = webViews[tab.serviceId]
        if (existente != null) {
            existente.webViewClient = client
            return existente
        }

        val webView = WebView(ctx.applicationContext)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = client
        webViews[tab.serviceId] = webView

        val restaurado = restaurarEstado(ctx, tab.serviceId, webView)
        if (!restaurado) webView.loadUrl(tab.url)
        return webView
    }

    fun salvarEstado(ctx: Context, serviceId: String?) {
        serviceId ?: return
        val webView = webViews[serviceId] ?: return
        runCatching {
            val bundle = Bundle()
            webView.saveState(bundle)
            val bytes = bundleParaBytes(bundle)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs(ctx).edit().putString(chaveEstado(serviceId), b64).apply()
        }
        webView.url?.let { atualizarUrl(ctx, serviceId, it) }
    }

    fun salvarTodos(ctx: Context) {
        webViews.keys.toList().forEach { salvarEstado(ctx, it) }
    }

    private fun restaurarEstado(ctx: Context, serviceId: String, webView: WebView): Boolean {
        val b64 = prefs(ctx).getString(chaveEstado(serviceId), null) ?: return false
        return runCatching {
            val bundle = bytesParaBundle(Base64.decode(b64, Base64.NO_WRAP))
            webView.restoreState(bundle) != null
        }.getOrDefault(false)
    }

    private fun bundleParaBytes(bundle: Bundle): ByteArray {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun bytesParaBundle(bytes: ByteArray): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readBundle(WebView::class.java.classLoader) ?: Bundle()
        } finally {
            parcel.recycle()
        }
    }
}
