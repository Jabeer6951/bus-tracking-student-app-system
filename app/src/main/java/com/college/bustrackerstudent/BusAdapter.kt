package com.college.bustrackerstudent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BusAdapter(
    private val busList: ArrayList<Bus>,
    private val onSelectClick: (Bus) -> Unit
) : RecyclerView.Adapter<BusAdapter.BusViewHolder>() {

    class BusViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBusNumber: TextView = itemView.findViewById(R.id.tvBusNumber)
        val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
        val tvStartLocation: TextView = itemView.findViewById(R.id.tvStartLocation)
        val tvEndLocation: TextView = itemView.findViewById(R.id.tvEndLocation)
        val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        val btnSelectBus: Button = itemView.findViewById(R.id.btnSelectBus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus, parent, false)
        return BusViewHolder(view)
    }

    override fun onBindViewHolder(holder: BusViewHolder, position: Int) {
        val bus = busList[position]

        holder.tvBusNumber.text = "Bus Number: ${bus.busNumber ?: 0L}"
        holder.tvRouteName.text = "Route: ${bus.routeName ?: ""}"
        holder.tvStartLocation.text = "Start: ${bus.startLocation ?: ""}"
        holder.tvEndLocation.text = "End: ${bus.endLocation ?: ""}"
        holder.tvDriverName.text = "Driver: ${bus.driverName ?: ""}"

        holder.btnSelectBus.setOnClickListener {
            onSelectClick(bus)
        }
    }

    override fun getItemCount(): Int = busList.size
}