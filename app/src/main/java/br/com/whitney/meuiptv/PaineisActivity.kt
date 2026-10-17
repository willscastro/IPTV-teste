package br.com.whitney.meuiptv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tela de "Painéis abertos": cada serviço do tipo PAINEL que a pessoa toca
 * vira uma aba aqui, igual abas de navegador. As WebViews ficam vivas em
 * PanelTabManager (não são recriadas ao trocar de aba), então ir e voltar
 * entre painéis não recarrega a página nem derruba o login.
 */
class PaineisActivity : AppCompatActivity() {

    private lateinit var tabStrip: LinearLayout
    private lateinit var webviewContainer: FrameLayout
    private lateinit var painelErro: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var txtPainelNome: TextView
    private lateinit var txtPainelUrl: TextView
    private lateinit var txtSemAbas: TextView

    private var abaAtivaId: String? = null

    // Evita tentar o auto-login de novo a cada navegação dentro do mesmo painel,
    // já logado - só tenta uma vez por aba, por abertura desta tela.
    private val autoLoginTentado = mutableSetOf<String>()

    // Abas cujo WebView já recebeu a ponte JS que escuta o envio do formulário de login.
    private val capturaInstalada = mutableSetOf<String>()

    // Login que a própria pessoa acabou de digitar e enviar na página, aguardando
    // a próxima página carregar pra perguntar se quer salvar pra próxima vez.
    private val loginCapturadoPendente = mutableMapOf<String, Pair<String, String>>()

    companion object {
        const val EXTRA_FOCUS_SERVICE_ID = "focus_service_id"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paineis)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tabStrip = findViewById(R.id.tabStrip)
        webviewContainer = findViewById(R.id.webviewContainer)
        painelErro = findViewById(R.id.painelErro)
        progress = findViewById(R.id.progress)
        txtPainelNome = findViewById(R.id.txtPainelNome)
        txtPainelUrl = findViewById(R.id.txtPainelUrl)
        txtSemAbas = findViewById(R.id.txtSemAbas)

        findViewById<View>(R.id.btnTentarDeNovo).setOnClickListener {
            painelErro.visibility = View.GONE
            PanelTabManager.webViewDe(abaAtivaId)?.reload()
        }
        findViewById<View>(R.id.btnAbrirNavegador).setOnClickListener {
            val url = PanelTabManager.abas(this).find { it.serviceId == abaAtivaId }?.url
            if (url != null) abrirForaDoApp(Uri.parse(url))
        }

        intent.getStringExtra(EXTRA_FOCUS_SERVICE_ID)?.let { PanelTabManager.focoAtual = it }
        abaAtivaId = PanelTabManager.focoAtual
    }

    override fun onResume() {
        super.onResume()
        redesenharTudo()
    }

    override fun onPause() {
        super.onPause()
        // onPause é chamado de forma bem mais confiável que onStop antes do
        // sistema matar o app, então é aqui que garantimos o login salvo de verdade.
        PanelTabManager.salvarTodos(this)
    }

    override fun onStop() {
        super.onStop()
        PanelTabManager.salvarTodos(this)
    }

    private fun redesenharTudo() {
        val abas = PanelTabManager.abas(this)
        if (abas.isEmpty()) {
            tabStrip.removeAllViews()
            webviewContainer.removeAllViews()
            painelErro.visibility = View.GONE
            progress.visibility = View.GONE
            txtPainelNome.text = ""
            txtPainelUrl.text = ""
            txtSemAbas.visibility = View.VISIBLE
            return
        }
        txtSemAbas.visibility = View.GONE
        if (abaAtivaId == null || abas.none { it.serviceId == abaAtivaId }) {
            abaAtivaId = abas.first().serviceId
        }
        PanelTabManager.focoAtual = abaAtivaId
        montarTabStrip(abas)
        mostrarAba(abaAtivaId!!)
    }

    private fun montarTabStrip(abas: List<PanelTabManager.Tab>) {
        tabStrip.removeAllViews()
        val dp = resources.displayMetrics.density

        abas.forEach { tab ->
            val ativa = tab.serviceId == abaAtivaId
            val corTexto = if (ativa) {
                ContextCompat.getColor(this, R.color.window_background)
            } else {
                ContextCompat.getColor(this, R.color.text_secondary)
            }

            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                background = chipBackground(ativa)
                isClickable = true
                isFocusable = true
                setOnClickListener { selecionarAba(tab.serviceId) }
            }

            val arquivoIcone = PanelTabManager.arquivoIcone(this, tab)
            val bitmapIcone = arquivoIcone?.let {
                runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            }
            if (bitmapIcone != null) {
                val icone = ImageView(this).apply {
                    val tamanho = (18 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(tamanho, tamanho).apply {
                        marginEnd = (6 * dp).toInt()
                    }
                    setImageDrawable(
                        androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(resources, bitmapIcone).apply {
                            isCircular = true
                        }
                    )
                }
                chip.addView(icone)
            }

            val label = TextView(this).apply {
                text = tab.nome
                setTextColor(corTexto)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = (120 * dp).toInt()
            }

            val fechar = ImageButton(this).apply {
                setImageResource(R.drawable.ic_fechar_aba)
                background = null
                contentDescription = "Fechar aba"
                layoutParams = LinearLayout.LayoutParams((26 * dp).toInt(), (26 * dp).toInt()).apply {
                    marginStart = (8 * dp).toInt()
                }
                setPadding(0, 0, 0, 0)
                drawable?.mutate()?.setTint(corTexto)
                setOnClickListener { fecharAba(tab.serviceId) }
            }

            chip.addView(label)
            chip.addView(fechar)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (8 * dp).toInt()
            tabStrip.addView(chip, lp)
        }

        val mais = TextView(this).apply {
            text = "+"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@PaineisActivity, R.color.text_secondary))
            gravity = Gravity.CENTER
            val tamanho = (38 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(tamanho, tamanho)
            background = chipTracejadaBackground()
            isClickable = true
            contentDescription = "Abrir outro serviço"
            setOnClickListener { finish() }
        }
        tabStrip.addView(mais)
    }

    private fun chipBackground(ativa: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 12 * resources.displayMetrics.density
        if (ativa) {
            setColor(ContextCompat.getColor(this@PaineisActivity, R.color.brand_gold))
        } else {
            setColor(ContextCompat.getColor(this@PaineisActivity, R.color.surface))
            setStroke((1 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this@PaineisActivity, R.color.border))
        }
    }

    private fun chipTracejadaBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 12 * resources.displayMetrics.density
        setColor(ContextCompat.getColor(this@PaineisActivity, R.color.surface))
        setStroke(
            (1 * resources.displayMetrics.density).toInt(),
            ContextCompat.getColor(this@PaineisActivity, R.color.border),
            (3 * resources.displayMetrics.density),
            (2 * resources.displayMetrics.density),
        )
    }

    private fun selecionarAba(serviceId: String) {
        if (serviceId == abaAtivaId) return
        PanelTabManager.salvarEstado(this, abaAtivaId)
        abaAtivaId = serviceId
        PanelTabManager.focoAtual = serviceId
        redesenharTudo()
    }

    private fun fecharAba(serviceId: String) {
        val eraAtiva = serviceId == abaAtivaId
        PanelTabManager.fechar(this, serviceId)
        autoLoginTentado.remove(serviceId)
        capturaInstalada.remove(serviceId)
        loginCapturadoPendente.remove(serviceId)
        if (eraAtiva) {
            abaAtivaId = PanelTabManager.abas(this).firstOrNull()?.serviceId
            PanelTabManager.focoAtual = abaAtivaId
        }
        if (PanelTabManager.abas(this).isEmpty()) {
            finish()
            return
        }
        redesenharTudo()
    }

    private fun mostrarAba(serviceId: String) {
        val tab = PanelTabManager.abas(this).find { it.serviceId == serviceId } ?: return
        txtPainelNome.text = tab.nome
        txtPainelUrl.text = tab.url
        painelErro.visibility = View.GONE

        val client = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val esquema = request.url.scheme
                if (esquema != "http" && esquema != "https") {
                    return abrirForaDoApp(request.url)
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (serviceId == abaAtivaId) {
                    progress.visibility = View.VISIBLE
                    painelErro.visibility = View.GONE
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (serviceId == abaAtivaId) {
                    progress.visibility = View.GONE
                    txtPainelUrl.text = url
                }
                PanelTabManager.salvarEstado(this@PaineisActivity, serviceId)
                garantirInterfaceCaptura(serviceId)
                instalarCapturaDeLogin(serviceId)
                tentarAutoLoginSeNecessario(serviceId)
                if (serviceId == abaAtivaId) oferecerSalvarLoginSeTiverPendente(serviceId)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && serviceId == abaAtivaId) {
                    progress.visibility = View.GONE
                    painelErro.visibility = View.VISIBLE
                }
            }
        }

        val webView = PanelTabManager.obterOuCriarWebView(this, tab, client)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webviewContainer.removeAllViews()
        webviewContainer.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun abrirForaDoApp(uri: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: Exception) {
        false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val webView = PanelTabManager.webViewDe(abaAtivaId)
        if (webView?.canGoBack() == true) webView.goBack() else super.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_paineis, menu)
        val item = menu.findItem(R.id.action_toggle_theme)
        item?.setIcon(if (ThemeHelper.estaEscuro()) R.drawable.ic_moon else R.drawable.ic_sun)
        item?.icon?.mutate()?.setTint(resources.getColor(R.color.text_primary, theme))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_theme -> {
                ThemeHelper.alternar(this)
                true
            }
            R.id.action_preencher_login -> {
                preencherLoginSalvo()
                true
            }
            R.id.action_salvar_login -> {
                abrirDialogoSalvarLogin()
                true
            }
            R.id.action_esquecer_login -> {
                abaAtivaId?.let { PasswordVault.esquecer(this, it) }
                Toast.makeText(this, R.string.login_forgotten, Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Objeto exposto ao JavaScript da página, pra saber quando a pessoa envia um login na mão. */
    private inner class CapturaLogin(private val serviceId: String) {
        @JavascriptInterface
        fun capturar(usuario: String, senha: String) {
            if (usuario.isBlank() || senha.isBlank()) return
            runOnUiThread {
                val salvo = PasswordVault.carregar(this@PaineisActivity, serviceId)
                if (salvo != null && salvo.first == usuario && salvo.second == senha) return@runOnUiThread
                loginCapturadoPendente[serviceId] = usuario to senha
            }
        }
    }

    /** Liga a ponte JS → Android nessa aba, uma única vez por WebView. */
    private fun garantirInterfaceCaptura(serviceId: String) {
        if (!capturaInstalada.add(serviceId)) return
        val webView = PanelTabManager.webViewDe(serviceId) ?: return
        webView.addJavascriptInterface(CapturaLogin(serviceId), "AndroidLoginCapture")
    }

    /** Escuta o "enviar" do formulário de login da página, pra saber o que a pessoa digitou. */
    private fun instalarCapturaDeLogin(serviceId: String) {
        val webView = PanelTabManager.webViewDe(serviceId) ?: return
        val js = """
            (function() {
                var campoSenha = document.querySelector('input[type=password]');
                if (!campoSenha) return;
                var formulario = campoSenha.closest('form');
                if (!formulario || formulario.dataset.meuiptvCaptura) return;
                formulario.dataset.meuiptvCaptura = '1';
                formulario.addEventListener('submit', function() {
                    var campoUsuario = document.querySelector('input[type=email], input[type=text][name*=user i], input[type=text][name*=login i], input[name*=user i]');
                    var usuario = campoUsuario ? campoUsuario.value : '';
                    var senha = campoSenha.value;
                    if (window.AndroidLoginCapture && usuario && senha) {
                        window.AndroidLoginCapture.capturar(usuario, senha);
                    }
                }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Se a pessoa acabou de logar na mão (e ainda não tínhamos esse login salvo), pergunta se quer salvar. */
    private fun oferecerSalvarLoginSeTiverPendente(serviceId: String) {
        val (usuario, senha) = loginCapturadoPendente.remove(serviceId) ?: return
        val nomeAba = PanelTabManager.abas(this).find { it.serviceId == serviceId }?.nome ?: "este painel"
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.offer_save_login_title)
            .setMessage(getString(R.string.offer_save_login_message, nomeAba))
            .setPositiveButton(R.string.btn_save) { _, _ ->
                PasswordVault.salvar(this, serviceId, usuario, senha)
                autoLoginTentado.remove(serviceId)
                Toast.makeText(this, R.string.login_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_not_now, null)
            .show()
    }

    /**
     * JS compartilhado pelos preenchimentos de login: acha o campo de usuário (com um
     * leque bem maior de pistas - nome, autocomplete, placeholder - e, no limite, "é o
     * único campo de texto desse formulário") e preenche usando o setter nativo do
     * input. Isso é necessário porque em sites feitos com React/Vue/Angular, um simples
     * "el.value = texto" não atualiza o estado interno do framework: o campo parece
     * preenchido por um instante e, ao confirmar, o site manda o valor antigo (vazio)
     * mesmo - a "pisca a tela e não vai" que o site lookdefense.top apresentava.
     */
    private fun jsAcharEPreencherCampos(usuario: String, senha: String): String = """
        var campoSenha = document.querySelector('input[type=password]');
        if (!campoSenha) return null;

        function valorNativo(el, valor) {
            var proto = window.HTMLInputElement.prototype;
            var descritor = Object.getOwnPropertyDescriptor(proto, 'value');
            if (descritor && descritor.set) {
                descritor.set.call(el, valor);
            } else {
                el.value = valor;
            }
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        }
        function preencher(el, valor) {
            if (!el) return;
            el.focus();
            valorNativo(el, valor);
            el.blur();
        }

        var seletorUsuario = 'input[type=email], input[autocomplete="username" i], input[autocomplete="email" i], '
            + 'input[type=text][name*=user i], input[type=text][name*=login i], input[type=text][name*=email i], input[name*=user i], '
            + 'input[type=text][placeholder*=usu i], input[type=text][placeholder*=user i], input[type=text][placeholder*=email i], input[type=text][placeholder*=login i]';
        var campoUsuario = document.querySelector(seletorUsuario);
        if (!campoUsuario) {
            var formularioBusca = campoSenha.closest('form') || document;
            var candidatos = formularioBusca.querySelectorAll('input[type=text], input[type=email], input:not([type])');
            if (candidatos.length === 1) campoUsuario = candidatos[0];
        }

        preencher(campoUsuario, ${org.json.JSONObject.quote(usuario)});
        preencher(campoSenha, ${org.json.JSONObject.quote(senha)});
        return campoSenha;
    """.trimIndent()

    /** Tenta logar automaticamente com o login salvo, uma vez por aba, se a página pedir senha. */
    private fun tentarAutoLoginSeNecessario(serviceId: String) {
        if (!autoLoginTentado.add(serviceId)) return
        val login = PasswordVault.carregar(this, serviceId) ?: return
        val webView = PanelTabManager.webViewDe(serviceId) ?: return
        val (usuario, senha) = login
        val js = """
            (function() {
                var campoSenhaAntes = document.querySelector('input[type=password]');
                if (!campoSenhaAntes || campoSenhaAntes.value) return; // não é tela de login, ou já tem algo preenchido
                ${jsAcharEPreencherCampos(usuario, senha)}
                var formulario = campoSenha.closest('form');
                if (formulario) {
                    if (typeof formulario.requestSubmit === 'function') formulario.requestSubmit();
                    else formulario.submit();
                } else {
                    var botao = document.querySelector('button[type=submit], input[type=submit]');
                    if (botao) botao.click();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun preencherLoginSalvo() {
        val id = abaAtivaId ?: return
        val login = PasswordVault.carregar(this, id)
        if (login == null) {
            Toast.makeText(this, "Nenhum login salvo para este serviço.", Toast.LENGTH_SHORT).show()
            return
        }
        val (usuario, senha) = login
        val webView = PanelTabManager.webViewDe(id) ?: return
        val js = """
            (function() {
                ${jsAcharEPreencherCampos(usuario, senha)}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun abrirDialogoSalvarLogin() {
        val id = abaAtivaId ?: return
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
                    PasswordVault.salvar(this, id, usuario, senha)
                    autoLoginTentado.remove(id)
                    Toast.makeText(this, R.string.login_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
