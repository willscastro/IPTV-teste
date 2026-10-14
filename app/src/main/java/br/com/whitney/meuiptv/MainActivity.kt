package br.com.whitney.meuiptv

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
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
    }

    private fun abrirServico(service: Service) {
        when (service.type) {
            ServiceType.PAINEL -> {
                val i = Intent(this, WebPanelActivity::class.java)
                i.putExtra(WebPanelActivity.EXTRA_SERVICE_ID, service.id)
                i.putExtra(WebPanelActivity.EXTRA_URL, service.panelUrl)
                i.putExtra(WebPanelActivity.EXTRA_NOME, service.name)
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
                recarregar()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
