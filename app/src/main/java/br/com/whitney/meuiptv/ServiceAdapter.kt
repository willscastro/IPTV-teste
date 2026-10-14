package br.com.whitney.meuiptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ServiceAdapter(
    private val itens: MutableList<Service>,
    private val onAbrir: (Service) -> Unit,
    private val onRemover: (Service) -> Unit,
) : RecyclerView.Adapter<ServiceAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val inicial: TextView = view.findViewById(R.id.txtInicial)
        val nome: TextView = view.findViewById(R.id.txtNome)
        val tipo: TextView = view.findViewById(R.id.txtTipo)
        val remover: ImageButton = view.findViewById(R.id.btnRemover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = itens.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = itens[position]
        holder.nome.text = s.name
        holder.inicial.text = s.name.trim().take(1).uppercase().ifBlank { "?" }
        holder.tipo.text = when (s.type) {
            ServiceType.PAINEL -> "Painel"
            ServiceType.M3U -> "Lista M3U"
            ServiceType.XTREAM -> "Xtream Codes"
        }
        holder.itemView.setOnClickListener { onAbrir(s) }
        holder.remover.setOnClickListener { onRemover(s) }
    }

    fun atualizar(novaLista: List<Service>) {
        itens.clear()
        itens.addAll(novaLista)
        notifyDataSetChanged()
    }
}
