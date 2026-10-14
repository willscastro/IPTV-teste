package br.com.whitney.meuiptv

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Abre o painel que a PRÓPRIA pessoa cadastrou, dentro de uma WebView em
 * tela cheia — como uma aba de navegador dedicada a esse serviço.
 *
 * Não há domínio fixo, não há verificação de assinatura do servidor, não
 * há atualização forçada: o app só abre a URL que a pessoa informou.
 *
 * O salvamento de login é opcional e sempre feito a pedido da pessoa,
 * nunca capturado em segredo da página.
 */
class WebPanelActivity : AppCompatActivity() {

    private lateinit var webview: WebView
    private lateinit var progress: ProgressBar
    private lateinit var painelErro: LinearLayout
    private lateinit var serviceId: String
    private var url: String = "about:blank"

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
        const val EXTRA_URL = "url"
        const val EXTRA_NOME = "nome"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_panel)

        serviceId = intent.getStringExtra(EXTRA_SERVICE_ID).orEmpty()
        url = intent.getStringExtra(EXTRA_URL) ?: "about:blank"
        val nome = intent.getStringExtra(EXTRA_NOME).orEmpty()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = nome
        toolbar.setNavigationOnClickListener { finish() }

        webview = findViewById(R.id.webview)
        progress = findViewById(R.id.progress)
        painelErro = findViewById(R.id.painelErro)

        with(webview.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true)

        webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val esquema = request.url.scheme
                if (esquema != "http" && esquema != "https") {
                    return abrirForaDoApp(request.url)
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                painelErro.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    painelErro.visibility = View.VISIBLE
                }
            }
        }

        findViewById<View>(R.id.btnTentarDeNovo).setOnClickListener {
            painelErro.visibility = View.GONE
            webview.reload()
        }
        findViewById<View>(R.id.btnAbrirNavegador).setOnClickListener {
            abrirForaDoApp(Uri.parse(url))
        }

        webview.loadUrl(url)
    }

    private fun abrirForaDoApp(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) webview.goBack() else super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_web_panel, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_preencher_login -> {
                preencherLoginSalvo()
                true
            }
            R.id.action_salvar_login -> {
                abrirDialogoSalvarLogin()
                true
            }
            R.id.action_esquecer_login -> {
                PasswordVault.esquecer(this, serviceId)
                Toast.makeText(this, R.string.login_forgotten, Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun preencherLoginSalvo() {
        val login = PasswordVault.carregar(this, serviceId)
        if (login == null) {
            Toast.makeText(this, "Nenhum login salvo para este serviço.", Toast.LENGTH_SHORT).show()
            return
        }
        val (usuario, senha) = login
        // Preenchimento simples, baseado nos tipos de campo mais comuns de formulários de login.
        val js = """
            (function() {
                function preencher(el, valor) {
                    if (!el) return;
                    el.focus();
                    el.value = valor;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var campoUsuario = document.querySelector('input[type=email], input[type=text][name*=user i], input[type=text][name*=login i], input[name*=user i]');
                var campoSenha = document.querySelector('input[type=password]');
                preencher(campoUsuario, ${org.json.JSONObject.quote(usuario)});
                preencher(campoSenha, ${org.json.JSONObject.quote(senha)});
            })();
        """.trimIndent()
        webview.evaluateJavascript(js, null)
    }

    private fun abrirDialogoSalvarLogin() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val editUsuario = EditText(this).apply { hint = getString(R.string.label_xtream_user) }
        val editSenha = EditText(this).apply {
            hint = getString(R.string.label_xtream_pass)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(editUsuario)
        container.addView(editSenha)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_save_login)
            .setView(container)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val usuario = editUsuario.text.toString().trim()
                val senha = editSenha.text.toString()
                if (usuario.isNotEmpty() && senha.isNotEmpty()) {
                    PasswordVault.salvar(this, serviceId, usuario, senha)
                    Toast.makeText(this, R.string.login_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
