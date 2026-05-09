package com.college.bustrackerstudent

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SosSettingsActivity : AppCompatActivity() {

    private lateinit var etPrimary: EditText
    private lateinit var etSecondary: EditText
    private lateinit var etThird: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_settings)

        etPrimary = findViewById(R.id.etPrimaryNumber)
        etSecondary = findViewById(R.id.etSecondaryNumber)
        etThird = findViewById(R.id.etThirdNumber)
        btnSave = findViewById(R.id.btnSaveSosNumbers)

        loadSavedNumbers()

        btnSave.setOnClickListener {
            saveNumbers()
        }
    }

    private fun saveNumbers() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "Student not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val primary = etPrimary.text.toString().trim()
        val secondary = etSecondary.text.toString().trim()
        val third = etThird.text.toString().trim()

        val map = HashMap<String, Any>()
        map["primary"] = primary
        map["secondary"] = secondary
        map["third"] = third

        FirebaseDatabase.getInstance().reference
            .child("students")
            .child(uid)
            .child("sosNumbers")
            .setValue(map)
            .addOnSuccessListener {
                Toast.makeText(this, "SOS numbers saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadSavedNumbers() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance().reference
            .child("students")
            .child(uid)
            .child("sosNumbers")
            .get()
            .addOnSuccessListener { snapshot ->
                etPrimary.setText(snapshot.child("primary").getValue(String::class.java) ?: "")
                etSecondary.setText(snapshot.child("secondary").getValue(String::class.java) ?: "")
                etThird.setText(snapshot.child("third").getValue(String::class.java) ?: "")
            }
    }
}