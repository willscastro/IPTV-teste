package br.com.whitney.meuiptv

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Guarda a escolha de tema claro/escuro da pessoa neste aparelho e aplica
 * em todas as telas (via AppCompatDelegate, que recria as activities
 * automaticamente). Padrão: escuro, como no visual aprovado.
 *
 * A troca sempre recria a Activity (comportamento do AppCompatDelegate),
 * então aplicamos um crossfade suave na transição para evitar o "salto"
 * visual abrupto da recriação padrão.
 */
object ThemeHelper {
    private const val PREFS = "meuiptv_tema"
    private const val KEY_MODO = "modo_noturno"

    fun aplicarSalvo(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modo = prefs.getInt(KEY_MODO, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(modo)
    }

    fun alternar(activity: Activity) {
        val novo = if (estaEscuro()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MODO, novo).apply()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        AppCompatDelegate.setDefaultNightMode(novo)
    }

    fun estaEscuro(): Boolean = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO
}
