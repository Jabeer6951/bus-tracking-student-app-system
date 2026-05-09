package com.college.bustrackerstudent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class StudentHomeActivity : AppCompatActivity() {

    private lateinit var tvBusNumber: TextView
    private lateinit var tvRouteName: TextView
    private lateinit var tvStartLocation: TextView
    private lateinit var tvEndLocation: TextView
    private lateinit var tvDriverName: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvBusMissed: TextView

    private lateinit var btnTrackBus: Button
    private lateinit var btnSOS: Button
    private lateinit var btnSOSSettings: Button
    private lateinit var btnLogout: Button
    private lateinit var btnShowUpcomingBuses: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference

    private var selectedBusId: String = ""
    private var stopLat: Double = 0.0
    private var stopLng: Double = 0.0

    private var busReachedStop = false
    private var busMissed = false
    private var busLocationListener: ValueEventListener? = null
    private var busLocationRef: DatabaseReference? = null

    companion object {
        private const val REQUEST_CALL_PERMISSION = 1001
        private const val REQUEST_SMS_PERMISSION = 2001
        private const val BUS_REACHED_DISTANCE_METERS = 200f
        private const val BUS_MISSED_DISTANCE_METERS = 500f
        private const val DEFAULT_LAT_TEXT = "Latitude: 0.0"
        private const val DEFAULT_LNG_TEXT = "Longitude: 0.0"
        private const val SOS_MESSAGE = "Emergency! I need help. Please contact me immediately."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_home)

        auth = FirebaseAuth.getInstance()
        dbRef = FirebaseDatabase.getInstance().reference

        initViews()
        setupDefaultState()

        val busFromIntent = normalizeBusId(intent.getStringExtra("selectedBusId").orEmpty())

        if (busFromIntent.isNotEmpty()) {
            selectedBusId = busFromIntent
            loadBusDetails(selectedBusId)
            loadLiveBusLocation(selectedBusId)
            loadStudentStopAndStartMissCheck()
        } else {
            loadSelectedBusFromFirebase()
        }

        btnTrackBus.setOnClickListener {
            if (selectedBusId.isEmpty()) {
                Toast.makeText(this, "Selected bus not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mapIntent = Intent(this, MapsActivity::class.java)
            mapIntent.putExtra("selectedBusId", selectedBusId)
            startActivity(mapIntent)
        }

        btnShowUpcomingBuses.setOnClickListener {
            if (selectedBusId.isEmpty()) {
                Toast.makeText(this, "Selected bus not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (stopLat == 0.0 && stopLng == 0.0) {
                Toast.makeText(this, "Student stop not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val upcomingIntent = Intent(this, UpcomingBusesActivity::class.java)
            upcomingIntent.putExtra("selectedBusId", selectedBusId)
            upcomingIntent.putExtra("stopLat", stopLat)
            upcomingIntent.putExtra("stopLng", stopLng)
            startActivity(upcomingIntent)
        }

        btnSOS.setOnClickListener {
            sendSOS()
        }

        btnSOSSettings.setOnClickListener {
            try {
                startActivity(Intent(this, SosSettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "SOS settings screen not found", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            getSharedPreferences("StudentApp", MODE_PRIVATE).edit().clear().apply()

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            val loginIntent = Intent(this, LoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }
    }

    private fun initViews() {
        tvBusNumber = findViewById(R.id.tvBusNumber)
        tvRouteName = findViewById(R.id.tvRouteName)
        tvStartLocation = findViewById(R.id.tvStartLocation)
        tvEndLocation = findViewById(R.id.tvEndLocation)
        tvDriverName = findViewById(R.id.tvDriverName)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvBusMissed = findViewById(R.id.tvBusMissed)

        btnTrackBus = findViewById(R.id.btnTrackBus)
        btnSOS = findViewById(R.id.btnSOS)
        btnSOSSettings = findViewById(R.id.btnSOSSettings)
        btnLogout = findViewById(R.id.btnLogout)
        btnShowUpcomingBuses = findViewById(R.id.btnShowUpcomingBuses)
    }

    private fun setupDefaultState() {
        tvBusMissed.visibility = View.GONE
        btnShowUpcomingBuses.visibility = View.GONE
        tvLatitude.text = DEFAULT_LAT_TEXT
        tvLongitude.text = DEFAULT_LNG_TEXT
    }

    private fun normalizeBusId(rawBusId: String): String {
        if (rawBusId.isBlank()) return ""

        val upper = rawBusId.trim().uppercase()
        val number = upper.filter { it.isDigit() }

        return if (number.isNotEmpty()) {
            "BUS-" + number.padStart(2, '0')
        } else {
            upper
        }
    }

    private fun loadSelectedBusFromFirebase() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Student not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        dbRef.child("students").child(uid).child("selectedBusId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    selectedBusId = normalizeBusId(snapshot.getValue(String::class.java).orEmpty())

                    if (selectedBusId.isEmpty()) {
                        dbRef.child("students").child(uid).child("selectedBus")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(busSnap: DataSnapshot) {
                                    selectedBusId = normalizeBusId(busSnap.getValue(String::class.java).orEmpty())

                                    if (selectedBusId.isEmpty()) {
                                        Toast.makeText(
                                            this@StudentHomeActivity,
                                            "No bus selected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return
                                    }

                                    loadBusDetails(selectedBusId)
                                    loadLiveBusLocation(selectedBusId)
                                    loadStudentStopAndStartMissCheck()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(
                                        this@StudentHomeActivity,
                                        error.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                    } else {
                        loadBusDetails(selectedBusId)
                        loadLiveBusLocation(selectedBusId)
                        loadStudentStopAndStartMissCheck()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@StudentHomeActivity,
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun loadBusDetails(busId: String) {
        dbRef.child("buses").child(busId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(busSnapshot: DataSnapshot) {
                    if (!busSnapshot.exists()) {
                        Toast.makeText(
                            this@StudentHomeActivity,
                            "Bus details not found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val busName = busSnapshot.child("busName").getValue(String::class.java) ?: busId
                    val driverName = busSnapshot.child("driverName").getValue(String::class.java) ?: "Driver"
                    val routeName = busSnapshot.child("routeName").getValue(String::class.java) ?: "--"
                    val startLocation = busSnapshot.child("startLocation").getValue(String::class.java) ?: "--"
                    val endLocation = busSnapshot.child("endLocation").getValue(String::class.java) ?: "--"

                    tvBusNumber.text = "Bus Number: $busName"
                    tvRouteName.text = "Route: $routeName"
                    tvStartLocation.text = "Start: $startLocation"
                    tvEndLocation.text = "End: $endLocation"
                    tvDriverName.text = "Driver: $driverName"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@StudentHomeActivity,
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun loadLiveBusLocation(busId: String) {
        dbRef.child("buses").child(busId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(locationSnapshot: DataSnapshot) {
                    if (!locationSnapshot.exists()) {
                        tvLatitude.text = DEFAULT_LAT_TEXT
                        tvLongitude.text = DEFAULT_LNG_TEXT
                        return
                    }

                    val lat = locationSnapshot.child("latitude").value?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = locationSnapshot.child("longitude").value?.toString()?.toDoubleOrNull() ?: 0.0

                    tvLatitude.text = "Latitude: $lat"
                    tvLongitude.text = "Longitude: $lng"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@StudentHomeActivity,
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun loadStudentStopAndStartMissCheck() {
        val uid = auth.currentUser?.uid ?: return

        dbRef.child("students").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    stopLat = snapshot.child("stopLat").getValue(Double::class.java) ?: 0.0
                    stopLng = snapshot.child("stopLng").getValue(Double::class.java) ?: 0.0

                    if (stopLat == 0.0 || stopLng == 0.0) {
                        return
                    }

                    startBusMissCheck(selectedBusId)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@StudentHomeActivity,
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun startBusMissCheck(busId: String) {
        if (busId.isEmpty()) return

        busLocationRef?.let { ref ->
            busLocationListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }

        busLocationRef = dbRef.child("busLocation").child(busId)

        busLocationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0

                if (lat == 0.0 && lng == 0.0) return

                checkIfBusMissed(lat, lng)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@StudentHomeActivity,
                    error.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        busLocationRef?.addValueEventListener(busLocationListener as ValueEventListener)
    }

    private fun checkIfBusMissed(busLat: Double, busLng: Double) {
        val results = FloatArray(1)

        Location.distanceBetween(busLat, busLng, stopLat, stopLng, results)

        val distanceToStop = results[0]

        if (!busReachedStop && distanceToStop <= BUS_REACHED_DISTANCE_METERS) {
            busReachedStop = true
        }

        if (busReachedStop && distanceToStop > BUS_MISSED_DISTANCE_METERS && !busMissed) {
            busMissed = true
            tvBusMissed.visibility = View.VISIBLE
            btnShowUpcomingBuses.visibility = View.VISIBLE
            Toast.makeText(this, "Your bus has missed", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendSOS() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "Student not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        dbRef.child("students").child(uid).child("sosNumbers")
            .get()
            .addOnSuccessListener { snapshot ->
                val primary = snapshot.child("primary").getValue(String::class.java).orEmpty()
                val secondary = snapshot.child("secondary").getValue(String::class.java).orEmpty()
                val third = snapshot.child("third").getValue(String::class.java).orEmpty()

                if (primary.isEmpty()) {
                    Toast.makeText(this, "Primary SOS number not saved", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                callNumber(primary)
                sendSMS(primary, SOS_MESSAGE)

                if (secondary.isNotEmpty()) sendSMS(secondary, SOS_MESSAGE)
                if (third.isNotEmpty()) sendSMS(third, SOS_MESSAGE)

                Toast.makeText(this, "SOS triggered", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun callNumber(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL_PERMISSION
            )
            Toast.makeText(this, "Allow call permission", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(callIntent)
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQUEST_SMS_PERMISSION
            )
            Toast.makeText(this, "Allow SMS permission", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        busLocationRef?.let { ref ->
            busLocationListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
    }
}