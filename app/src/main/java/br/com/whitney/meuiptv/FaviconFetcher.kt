package br.com.whitney.meuiptv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Busca o ícone (favicon) do site/painel que a pessoa cadastrou, pra
 * mostrar na listinha de serviços e nas abas — igual todo navegador
 * mostra o ícone de cada site na aba, sem precisar abrir a página pra
 * descobrir qual é.
 *
 * Usa só técnicas públicas e genéricas (as mesmas que qualquer navegador
 * usa): lê as tags <link rel="icon"> do próprio HTML da página, tenta o
 * /favicon.ico padrão do domínio, e só como último recurso usa o serviço
 * público de miniaturas do Google — descartando o ícone "genérico" que
 * ele devolve quando não encontra nada de verdade. Não acessa nem imita
 * nenhum app de terceiros: é tudo requisição HTTP comum para o domínio
 * que a própria pessoa informou.
 */
object FaviconFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    fun buscarEBaixar(ctx: Context, service: Service, aoConcluir: (String?) -> Unit) {
        val panelUrl = service.panelUrl
        val host = panelUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        if (panelUrl == null || host.isNullOrBlank()) {
            postar { aoConcluir(null) }
            return
        }
        val appCtx = ctx.applicationContext

        candidatosDoHtml(panelUrl) { candidatosHtml ->
            val candidatos = candidatosHtml + "https://$host/favicon.ico"
            tentarSequencia(candidatos, 0) { bytesEncontrados ->
                if (bytesEncontrados != null) {
                    concluirComBytes(appCtx, service.id, bytesEncontrados, aoConcluir)
                } else {
                    // Último recurso: serviço público do Google, rejeitando o ícone
                    // genérico de "não encontrei nada" que ele devolve em vez de erro.
                    buscarViaGoogle(host) { bytesGoogle ->
                        if (bytesGoogle != null) {
                            concluirComBytes(appCtx, service.id, bytesGoogle, aoConcluir)
                        } else {
                            postar { aoConcluir(null) }
                        }
                    }
                }
            }
        }
    }

    /** Usado quando a própria pessoa escolhe uma imagem da galeria para ser a logo do serviço. */
    fun salvarIconeManual(ctx: Context, serviceId: String, uri: Uri, aoConcluir: (String?) -> Unit) {
        val appCtx = ctx.applicationContext
        Thread {
            val caminho = runCatching {
                val bitmap = appCtx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: return@runCatching null
                if (salvarBitmap(appCtx, serviceId, bitmap)) "favicons/$serviceId.png" else null
            }.getOrNull()
            postar { aoConcluir(caminho) }
        }.start()
    }

    /** Devolve o arquivo local do ícone já baixado/escolhido, ou null se não existir. */
    fun arquivoDoIcone(ctx: Context, service: Service): File? {
        val relativo = service.iconFile ?: return null
        val arquivo = File(ctx.applicationContext.filesDir, relativo)
        return if (arquivo.exists()) arquivo else null
    }

    fun remover(ctx: Context, serviceId: String) {
        File(ctx.applicationContext.filesDir, "favicons/$serviceId.png").delete()
    }

    // --- Descoberta via HTML da própria página (jeito mais confiável de achar a logo real) ---

    private fun candidatosDoHtml(pageUrl: String, callback: (List<String>) -> Unit) {
        val pedido = runCatching {
            Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
        }.getOrNull()
        if (pedido == null) {
            callback(emptyList())
            return
        }
        client.newCall(pedido).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(emptyList())

            override fun onResponse(call: Call, response: Response) {
                val baseUrl = response.request.url.toString()
                val html = response.use { if (it.isSuccessful) it.body?.string() else null }
                if (html.isNullOrBlank()) {
                    callback(emptyList())
                    return
                }
                val candidatos = runCatching {
                    val doc = Jsoup.parse(html, baseUrl)
                    doc.select("link[rel]")
                        .toList()
                        .filter { it.attr("rel").lowercase().contains("icon") }
                        .sortedByDescending { el ->
                            val rel = el.attr("rel").lowercase()
                            val tamanho = el.attr("sizes").substringBefore("x").toIntOrNull() ?: 0
                            (if (rel.contains("apple-touch-icon")) 1000 else 0) + tamanho
                        }
                        .mapNotNull { it.absUrl("href").ifBlank { null } }
                        .distinct()
                        .take(5)
                }.getOrDefault(emptyList())
                callback(candidatos)
            }
        })
    }

    // --- Serviço público do Google, com rejeição do ícone genérico de "não encontrado" ---

    private fun buscarViaGoogle(host: String, callback: (ByteArray?) -> Unit) {
        val sentinela = "meuiptv-sem-favicon-${System.nanoTime()}.invalid"
        var bytesReal: ByteArray? = null
        var bytesSentinela: ByteArray? = null
        var restantes = 2
        val lock = Any()

        fun checarFim() {
            val terminou = synchronized(lock) {
                restantes--
                restantes <= 0
            }
            if (!terminou) return
            val real = bytesReal
            if (real == null || !pareceIconeValido(real)) {
                callback(null)
            } else if (bytesSentinela != null && real.contentEquals(bytesSentinela)) {
                // mesmo ícone devolvido para um domínio inventado: é o genérico, não a logo real
                callback(null)
            } else {
                callback(real)
            }
        }

        baixar(enderecoFaviconGoogle(host)) { bytes -> bytesReal = bytes; checarFim() }
        baixar(enderecoFaviconGoogle(sentinela)) { bytes -> bytesSentinela = bytes; checarFim() }
    }

    private fun enderecoFaviconGoogle(host: String) = "https://www.google.com/s2/favicons?sz=128&domain=$host"

    // --- Helpers genéricos ---

    private fun tentarSequencia(urls: List<String>, index: Int, aoConcluir: (ByteArray?) -> Unit) {
        if (index >= urls.size) {
            aoConcluir(null)
            return
        }
        baixar(urls[index]) { bytes ->
            if (bytes != null && pareceIconeValido(bytes)) {
                aoConcluir(bytes)
            } else {
                tentarSequencia(urls, index + 1, aoConcluir)
            }
        }
    }

    private fun baixar(url: String, callback: (ByteArray?) -> Unit) {
        val pedido = runCatching {
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        }.getOrNull()
        if (pedido == null) {
            callback(null)
            return
        }
        client.newCall(pedido).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null)
            override fun onResponse(call: Call, response: Response) {
                val bytes = response.use { if (it.isSuccessful) it.body?.bytes() else null }
                callback(bytes)
            }
        })
    }

    /** Confere se os bytes realmente decodificam como uma imagem de verdade (não um .ico quebrado, nem vazio). */
    private fun pareceIconeValido(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.size < 64) return false
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
        return opts.outWidth > 1 && opts.outHeight > 1
    }

    private fun concluirComBytes(appCtx: Context, serviceId: String, bytes: ByteArray, aoConcluir: (String?) -> Unit) {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        if (bitmap == null) {
            postar { aoConcluir(null) }
            return
        }
        val caminho = if (salvarBitmap(appCtx, serviceId, bitmap)) "favicons/$serviceId.png" else null
        postar { aoConcluir(caminho) }
    }

    private fun salvarBitmap(appCtx: Context, serviceId: String, bitmap: Bitmap): Boolean {
        val pasta = File(appCtx.filesDir, "favicons").apply { mkdirs() }
        val arquivo = File(pasta, "$serviceId.png")
        return runCatching {
            FileOutputStream(arquivo).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }.isSuccess
    }

    private fun postar(acao: () -> Unit) {
        Handler(Looper.getMainLooper()).post(acao)
    }
}
