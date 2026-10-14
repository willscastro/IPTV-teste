package br.com.whitney.meuiptv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Toca o canal escolhido pela pessoa, usando o endereço de stream que veio
 * da lista M3U/Xtream que ela mesma cadastrou.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOME = "nome"
        const val EXTRA_URL = "url"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val playerView = findViewById<PlayerView>(R.id.playerView)

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
