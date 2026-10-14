package br.com.whitney.meuiptv

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tipo de serviço cadastrado pela própria pessoa que usa o app.
 * PAINEL  -> um site/painel que abre dentro de uma WebView (como um navegador em aba).
 * M3U     -> uma lista de canais a partir de um link M3U.
 * XTREAM  -> uma lista de canais a partir de credenciais Xtream Codes (servidor/usuário/senha).
 */
enum class ServiceType { PAINEL, M3U, XTREAM }

data class Service(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: ServiceType,
    // PAINEL
    var panelUrl: String? = null,
    // M3U
    var m3uUrl: String? = null,
    // XTREAM
    var xtreamServer: String? = null,
    var xtreamUser: String? = null,
    var xtreamPass: String? = null,
    // Ícone do site/painel, baixado automaticamente (caminho relativo dentro de filesDir)
    var iconFile: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        panelUrl?.let { put("panelUrl", it) }
        m3uUrl?.let { put("m3uUrl", it) }
        xtreamServer?.let { put("xtreamServer", it) }
        xtreamUser?.let { put("xtreamUser", it) }
        xtreamPass?.let { put("xtreamPass", it) }
        iconFile?.let { put("iconFile", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Service = Service(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            type = runCatching { ServiceType.valueOf(o.getString("type")) }.getOrDefault(ServiceType.PAINEL),
            panelUrl = o.optStringOrNull("panelUrl"),
            m3uUrl = o.optStringOrNull("m3uUrl"),
            xtreamServer = o.optStringOrNull("xtreamServer"),
            xtreamUser = o.optStringOrNull("xtreamUser"),
            xtreamPass = o.optStringOrNull("xtreamPass"),
            iconFile = o.optStringOrNull("iconFile"),
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key)) optString(key) else null

/**
 * Guarda a lista de serviços cadastrados em SharedPreferences, só neste aparelho.
 * Não há servidor central: cada pessoa que instala o app cadastra os próprios serviços.
 */
object ServiceStore {
    private const val PREFS = "meuiptv_services"
    private const val KEY_LIST = "lista"

    fun listar(ctx: android.content.Context): MutableList<Service> {
        val prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<Service>()
        for (i in 0 until arr.length()) {
            out.add(Service.fromJson(arr.getJSONObject(i)))
        }
        return out
    }

    fun salvar(ctx: android.content.Context, servicos: List<Service>) {
        val prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val arr = JSONArray()
        servicos.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun adicionar(ctx: android.content.Context, servico: Service) {
        val atuais = listar(ctx)
        atuais.add(servico)
        salvar(ctx, atuais)
    }

    fun remover(ctx: android.content.Context, id: String) {
        val atuais = listar(ctx).filterNot { it.id == id }
        salvar(ctx, atuais)
    }

    /** Substitui um serviço já existente (mesmo id) com os dados atualizados. */
    fun atualizar(ctx: android.content.Context, servico: Service) {
        val atuais = listar(ctx).map { if (it.id == servico.id) servico else it }
        salvar(ctx, atuais)
    }
}
