package br.com.whitney.meuiptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(
    private val onClique: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private val todos = mutableListOf<Channel>()
    private val exibidos = mutableListOf<Channel>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val inicial: TextView = view.findViewById(R.id.txtInicialCanal)
        val nome: TextView = view.findViewById(R.id.txtNomeCanal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }

    override fun getItemCount() = exibidos.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = exibidos[position]
        holder.nome.text = c.name
        holder.inicial.text = c.name.trim().take(1).uppercase().ifBlank { "?" }
        holder.itemView.setOnClickListener { onClique(c) }
    }

    fun definirLista(novaLista: List<Channel>) {
        todos.clear()
        todos.addAll(novaLista)
        exibidos.clear()
        exibidos.addAll(novaLista)
        notifyDataSetChanged()
    }

    fun filtrar(termo: String) {
        exibidos.clear()
        if (termo.isBlank()) {
            exibidos.addAll(todos)
        } else {
            exibidos.addAll(todos.filter { it.name.contains(termo, ignoreCase = true) })
        }
        notifyDataSetChanged()
    }
}
