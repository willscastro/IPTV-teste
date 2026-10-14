package br.com.whitney.meuiptv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Tela de abertura: identidade da Janus (quem desenvolveu o app) seguida
 * do selo do Meu IPTV. Puramente de apresentação — não faz login, não
 * verifica licença, não trava nada. Um toque em "Continuar" leva direto
 * para a lista de serviços da pessoa.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<MaterialButton>(R.id.btnContinuar).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
