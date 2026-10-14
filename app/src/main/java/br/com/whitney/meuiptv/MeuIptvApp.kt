package br.com.whitney.meuiptv

import android.app.Application

class MeuIptvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.aplicarSalvo(this)
    }
}
