package com.college.bustrackerstudent

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SelectBusActivity : AppCompatActivity() {

    private lateinit var recyclerViewBuses: RecyclerView
    private lateinit var busList: ArrayList<Bus>
    private lateinit var busAdapter: BusAdapter
    private lateinit var dbRef: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_bus)

        auth = FirebaseAuth.getInstance()
        dbRef = FirebaseDatabase.getInstance().reference

        recyclerViewBuses = findViewById(R.id.recyclerViewBuses)
        recyclerViewBuses.layoutManager = LinearLayoutManager(this)

        busList = ArrayList()
        busAdapter = BusAdapter(busList) { selectedBus ->
            saveSelectedBus(selectedBus)
        }

        recyclerViewBuses.adapter = busAdapter

        loadBuses()
    }

    private fun loadBuses() {
        dbRef.child("buses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                busList.clear()

                for (busSnap in snapshot.children) {
                    try {
                        val bus = busSnap.getValue(Bus::class.java)
                        if (bus != null) {
                            val busWithId = bus.copy(busId = busSnap.key ?: "")
                            busList.add(busWithId)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@SelectBusActivity,
                            "Bus data error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                busAdapter.notifyDataSetChanged()

                if (busList.isEmpty()) {
                    Toast.makeText(this@SelectBusActivity, "No buses found", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SelectBusActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveSelectedBus(bus: Bus) {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Student not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (bus.busId.isEmpty()) {
            Toast.makeText(this, "Bus ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid

        dbRef.child("students").child(uid).child("selectedBusId").setValue(bus.busId)
            .addOnSuccessListener {
                Toast.makeText(this, "${bus.busNumber} selected", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, StudentHomeActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save selected bus: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}