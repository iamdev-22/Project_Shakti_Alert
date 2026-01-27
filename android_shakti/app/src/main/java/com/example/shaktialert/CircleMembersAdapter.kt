package com.example.shaktialert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying circle members in RecyclerView
 */
class CircleMembersAdapter(
    private val members: List<CircleMember>
) : RecyclerView.Adapter<CircleMembersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMemberName)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvBattery: TextView = view.findViewById(R.id.tvBattery)
        val imgProfile: ImageView = view.findViewById(R.id.imgMemberPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circle_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        holder.tvName.text = member.name
        holder.tvStatus.text = member.address
        holder.tvBattery.text = "${member.battery}%"
        
        // TODO: Load profile photo with Glide/Picasso if available
        // For now, use default avatar
    }

    override fun getItemCount() = members.size
}
