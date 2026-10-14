package br.com.whitney.meuiptv

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Busca a lista de canais de um serviço M3U ou Xtream Codes que a própria
 * pessoa cadastrou. Faz apenas a requisição que a pessoa pediu, para o
 * endereço/credenciais que ela mesma informou — sem nenhum servidor
 * intermediário do app.
 */
object PlaylistLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Executa em thread de fundo; lança exceção em caso de falha. */
    fun carregar(service: Service): List<Channel> {
        return when (service.type) {
            ServiceType.M3U -> carregarM3u(service.m3uUrl.orEmpty())
            ServiceType.XTREAM -> carregarXtream(
                servidor = service.xtreamServer.orEmpty(),
                usuario = service.xtreamUser.orEmpty(),
                senha = service.xtreamPass.orEmpty(),
            )
            ServiceType.PAINEL -> emptyList()
        }
    }

    private fun baixarTexto(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Falha ao baixar lista (HTTP ${resp.code})")
            return resp.body?.string() ?: throw RuntimeException("Resposta vazia")
        }
    }

    private fun carregarM3u(url: String): List<Channel> {
        require(url.isNotBlank()) { "Link M3U vazio" }
        val texto = baixarTexto(url)
        return parseM3u(texto)
    }

    fun parseM3u(conteudo: String): List<Channel> {
        val linhas = conteudo.lines()
        val canais = mutableListOf<Channel>()
        var nomeAtual: String? = null
        var logoAtual: String? = null
        var grupoAtual: String? = null

        for (linha in linhas) {
            val l = linha.trim()
            when {
                l.startsWith("#EXTINF", ignoreCase = true) -> {
                    nomeAtual = l.substringAfterLast(',').trim().ifBlank { "Canal" }
                    logoAtual = Regex("tvg-logo=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                    grupoAtual = Regex("group-title=\"([^\"]*)\"").find(l)?.groupValues?.get(1)
                }
                l.isNotEmpty() && !l.startsWith("#") -> {
                    canais.add(
                        Channel(
                            name = nomeAtual ?: "Canal",
                            streamUrl = l,
                            logoUrl = logoAtual,
                            group = grupoAtual,
                        )
                    )
                    nomeAtual = null
                    logoAtual = null
                    grupoAtual = null
                }
            }
        }
        return canais
    }

    private fun carregarXtream(servidor: String, usuario: String, senha: String): List<Channel> {
        require(servidor.isNotBlank() && usuario.isNotBlank() && senha.isNotBlank()) {
            "Dados do Xtream Codes incompletos"
        }
        val base = servidor.trimEnd('/')

        // Lista de canais ao vivo via player_api.php (padrão Xtream Codes).
        val urlLista = "$base/player_api.php?username=$usuario&password=$senha&action=get_live_streams"
        val json = baixarTexto(urlLista)
        val arr = JSONArray(json)

        val canais = mutableListOf<Channel>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val streamId = item.optInt("stream_id", -1)
            if (streamId < 0) continue
            val nome = item.optString("name", "Canal")
            val logo = item.optStringOrNull("stream_icon")
            val streamUrl = "$base/live/$usuario/$senha/$streamId.m3u8"
            canais.add(Channel(name = nome, streamUrl = streamUrl, logoUrl = logo))
        }
        return canais
    }
}

private fun org.json.JSONObject.optStringOrNull(key: String): String? {
    val v = optString(key, "")
    return v.ifBlank { null }
}
