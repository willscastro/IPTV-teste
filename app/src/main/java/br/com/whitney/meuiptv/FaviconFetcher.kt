package br.com.whitney.meuiptv

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

/**
 * Busca o ícone (favicon) do site/painel que a pessoa cadastrou, pra
 * mostrar na listinha de serviços e nas abas — igual todo navegador
 * mostra o ícone de cada site na aba, sem precisar abrir a página pra
 * descobrir qual é.
 *
 * Usa um serviço público de favicons (o mesmo tipo de técnica usado por
 * navegadores e launchers): a partir do domínio do painel, pede o ícone
 * já em miniatura. Não acessa nem imita nenhum app de terceiros — é só
 * uma requisição HTTP comum para o domínio que a própria pessoa informou.
 */
object FaviconFetcher {
    private val client = OkHttpClient()

    fun buscarEBaixar(ctx: Context, service: Service, aoConcluir: (String?) -> Unit) {
        val panelUrl = service.panelUrl
        val host = panelUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        if (host.isNullOrBlank()) {
            aoConcluir(null)
            return
        }

        val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$host"
        val pedido = Request.Builder().url(faviconUrl).build()
        val appCtx = ctx.applicationContext

        client.newCall(pedido).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                postar { aoConcluir(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bytes = response.use { it.body?.bytes() }
                if (bytes == null || bytes.size < 32) {
                    postar { aoConcluir(null) }
                    return
                }
                val pasta = File(appCtx.filesDir, "favicons").apply { mkdirs() }
                val arquivo = File(pasta, "${service.id}.png")
                val salvou = runCatching { arquivo.writeBytes(bytes) }.isSuccess
                postar { aoConcluir(if (salvou) "favicons/${service.id}.png" else null) }
            }
        })
    }

    /** Devolve o arquivo local do ícone já baixado, ou null se não existir. */
    fun arquivoDoIcone(ctx: Context, service: Service): File? {
        val relativo = service.iconFile ?: return null
        val arquivo = File(ctx.applicationContext.filesDir, relativo)
        return if (arquivo.exists()) arquivo else null
    }

    fun remover(ctx: Context, serviceId: String) {
        File(ctx.applicationContext.filesDir, "favicons/$serviceId.png").delete()
    }

    private fun postar(acao: () -> Unit) {
        Handler(Looper.getMainLooper()).post(acao)
    }
}
