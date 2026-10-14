package br.com.whitney.meuiptv

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Guarda a escolha de tema claro/escuro da pessoa neste aparelho e aplica
 * em todas as telas (via AppCompatDelegate, que recria as activities
 * automaticamente). Padrão: escuro, como no visual aprovado.
 */
object ThemeHelper {
    private const val PREFS = "meuiptv_tema"
    private const val KEY_MODO = "modo_noturno"

    fun aplicarSalvo(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modo = prefs.getInt(KEY_MODO, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(modo)
    }

    fun alternar(context: Context) {
        val novo = if (estaEscuro()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MODO, novo).apply()
        AppCompatDelegate.setDefaultNightMode(novo)
    }

    fun estaEscuro(): Boolean = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO
}
