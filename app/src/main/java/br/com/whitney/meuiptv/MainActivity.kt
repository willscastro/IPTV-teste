package br.com.whitney.meuiptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.appbar.MaterialToolbar

/**
 * Tela inicial: lista os serviços que a PRÓPRIA pessoa cadastrou neste
 * aparelho. Não existe conta, login central, bloqueio por servidor ou
 * verificação de assinatura: qualquer pessoa que instale o app pode
 * cadastrar e usar livremente os serviços que ela mesma já tem acesso.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ServiceAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var txtVazio: TextView
    private val buscandoIcone = mutableSetOf<String>()
    private var servicoEscolhendoIcone: Service? = null

    private val selecionarImagemDaGaleria =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val servico = servicoEscolhendoIcone
            servicoEscolhendoIcone = null
            if (uri != null && servico != null) {
                FaviconFetcher.salvarIconeManual(this, servico.id, uri) { caminho ->
                    if (caminho != null) {
                        servico.iconFile = caminho
                        ServiceStore.atualizar(this, servico)
                        recarregar()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        recycler = findViewById(R.id.recyclerServicos)
        txtVazio = findViewById(R.id.txtVazio)

        adapter = ServiceAdapter(
            itens = mutableListOf(),
            onAbrir = { abrirServico(it) },
            onRemover = { confirmarRemocao(it) },
            onEditarIcone = { abrirMenuDeIcone(it) },
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdicionar).setOnClickListener {
            startActivity(Intent(this, AddServiceActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        recarregar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val item = menu.findItem(R.id.action_toggle_theme)
        item?.setIcon(if (ThemeHelper.estaEscuro()) R.drawable.ic_moon else R.drawable.ic_sun)
        item?.icon?.mutate()?.setTint(resources.getColor(R.color.text_primary, theme))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_toggle_theme) {
            ThemeHelper.alternar(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun recarregar() {
        val lista = ServiceStore.listar(this)
        adapter.atualizar(lista)
        txtVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        buscarIconesFaltantes(lista)
    }

    /** Busca o ícone do painel em segundo plano para quem ainda não tem um salvo. */
    private fun buscarIconesFaltantes(lista: List<Service>) {
        lista
            .filter {
                it.type == ServiceType.PAINEL && it.iconFile == null && !it.panelUrl.isNullOrBlank() &&
                    it.id !in buscandoIcone
            }
            .forEach { servico ->
                buscandoIcone.add(servico.id)
                FaviconFetcher.buscarEBaixar(this, servico) { caminho ->
                    buscandoIcone.remove(servico.id)
                    if (caminho != null) {
                        servico.iconFile = caminho
                        ServiceStore.atualizar(this, servico)
                        recarregar()
                    }
                }
            }
    }

    /** Toque no avatar do serviço: deixa a pessoa escolher a logo da galeria, ou tentar buscar de novo. */
    private fun abrirMenuDeIcone(service: Service) {
        val opcoes = arrayOf(
            "Escolher da galeria",
            "Buscar automaticamente de novo",
            "Remover logo (usar inicial)",
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(service.name)
            .setItems(opcoes) { _, index ->
                when (index) {
                    0 -> {
                        servicoEscolhendoIcone = service
                        selecionarImagemDaGaleria.launch("image/*")
                    }
                    1 -> {
                        buscandoIcone.remove(service.id)
                        buscandoIcone.add(service.id)
                        FaviconFetcher.buscarEBaixar(this, service) { caminho ->
                            buscandoIcone.remove(service.id)
                            service.iconFile = caminho
                            ServiceStore.atualizar(this, service)
                            recarregar()
                        }
                    }
                    2 -> {
                        FaviconFetcher.remover(this, service.id)
                        service.iconFile = null
                        ServiceStore.atualizar(this, service)
                        recarregar()
                    }
                }
            }
            .show()
    }

    private fun abrirServico(service: Service) {
        when (service.type) {
            ServiceType.PAINEL -> {
                PanelTabManager.abrirOuFocar(this, service)
                val i = Intent(this, PaineisActivity::class.java)
                i.putExtra(PaineisActivity.EXTRA_FOCUS_SERVICE_ID, service.id)
                startActivity(i)
            }
            ServiceType.M3U, ServiceType.XTREAM -> {
                val i = Intent(this, ChannelListActivity::class.java)
                i.putExtra(ChannelListActivity.EXTRA_SERVICE_ID, service.id)
                startActivity(i)
            }
        }
    }

    private fun confirmarRemocao(service: Service) {
        MaterialAlertDialogBuilder(this)
            .setTitle(service.name)
            .setMessage("Remover este serviço da lista? Isso não cancela nem afeta a assinatura em si, só tira o atalho daqui.")
            .setPositiveButton("Remover") { _, _ ->
                ServiceStore.remover(this, service.id)
                PasswordVault.esquecer(this, service.id)
                PanelTabManager.fechar(this, service.id)
                FaviconFetcher.remover(this, service.id)
                recarregar()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
