package com.college.bustrackerstudent

import android.location.Location
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot

class UpcomingBusesActivity : AppCompatActivity() {

    private lateinit var tvUpcomingBuses: TextView
    private lateinit var dbRef: DatabaseReference

    private var selectedBusId: String = ""
    private var stopLat: Double = 0.0
    private var stopLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upcoming_buses)

        tvUpcomingBuses = findViewById(R.id.tvUpcomingBuses)
        dbRef = FirebaseDatabase.getInstance().reference

        selectedBusId = intent.getStringExtra("selectedBusId").orEmpty()
        stopLat = intent.getDoubleExtra("stopLat", 0.0)
        stopLng = intent.getDoubleExtra("stopLng", 0.0)

        loadUpcomingBuses()
    }

    private fun loadUpcomingBuses() {
        dbRef.child("busLocation").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    tvUpcomingBuses.text = "No upcoming buses found."
                    return
                }

                val builder = StringBuilder()
                var found = false

                for (busSnap in snapshot.children) {
                    val busId = busSnap.key.orEmpty()

                    if (busId == selectedBusId) {
                        continue
                    }

                    val lat = busSnap.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = busSnap.child("longitude").getValue(Double::class.java) ?: 0.0
                    val busName = busSnap.child("busName").getValue(String::class.java) ?: busId

                    if (lat == 0.0 && lng == 0.0) {
                        continue
                    }

                    val results = FloatArray(1)
                    Location.distanceBetween(lat, lng, stopLat, stopLng, results)
                    val distanceMeters = results[0]

                    if (distanceMeters <= 5000) {
                        found = true
                        builder.append("Bus: ")
                        builder.append(busName)
                        builder.append("\n")
                        builder.append("Distance: ")
                        builder.append(distanceMeters.toInt())
                        builder.append(" meters")
                        builder.append("\n\n")
                    }
                }

                tvUpcomingBuses.text = if (found) {
                    builder.toString()
                } else {
                    "No nearby upcoming buses found."
                }
            }

            override fun onCancelled(error: DatabaseError) {
                tvUpcomingBuses.text = "Failed to load upcoming buses."
            }
        })
    }
}