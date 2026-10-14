package br.com.whitney.meuiptv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cofre de senhas local e opcional: a pessoa escolhe salvar o PRÓPRIO login
 * de um painel que ELA MESMA cadastrou, só para não digitar de novo depois.
 *
 * A senha nunca sai do aparelho: é cifrada com AES-256-GCM usando uma chave
 * guardada no Android Keystore (não existe em lugar nenhum fora do hardware
 * seguro do aparelho da pessoa).
 */
object PasswordVault {
    private const val ALIAS = "meuiptv_cofre"
    private const val PREFS = "meuiptv_cofre_prefs"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gerador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gerador.generateKey()
    }

    private fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, chave())
        val iv = c.iv
        val dados = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + dados, Base64.NO_WRAP)
    }

    private fun decifrar(valor: String): String {
        val bytes = Base64.decode(valor, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val dados = bytes.copyOfRange(12, bytes.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, iv))
        return String(c.doFinal(dados), Charsets.UTF_8)
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Salva (ou atualiza) o login da pessoa para um serviço específico. */
    fun salvar(ctx: Context, serviceId: String, usuario: String, senha: String) {
        val obj = JSONObject().apply {
            put("usuario", usuario)
            put("senha", cifrar(senha))
        }
        prefs(ctx).edit().putString(serviceId, obj.toString()).apply()
    }

    /** Devolve o login salvo (usuario, senha em texto claro) ou null se não houver. */
    fun carregar(ctx: Context, serviceId: String): Pair<String, String>? {
        val raw = prefs(ctx).getString(serviceId, null) ?: return null
        val obj = JSONObject(raw)
        val usuario = obj.optString("usuario", "")
        val senha = runCatching { decifrar(obj.optString("senha", "")) }.getOrNull() ?: return null
        return usuario to senha
    }

    fun existe(ctx: Context, serviceId: String): Boolean =
        prefs(ctx).contains(serviceId)

    fun esquecer(ctx: Context, serviceId: String) {
        prefs(ctx).edit().remove(serviceId).apply()
    }
}
