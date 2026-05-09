package com.college.bustrackerstudent

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        dbRef = FirebaseDatabase.getInstance().reference

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etRollNumber = findViewById(R.id.etRollNumber)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)

        btnRegister.setOnClickListener {
            registerStudent()
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerStudent() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val rollNumber = etRollNumber.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Enter name"
            etName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            etEmail.error = "Enter email"
            etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter valid email"
            etEmail.requestFocus()
            return
        }

        if (rollNumber.isEmpty()) {
            etRollNumber.error = "Enter roll number"
            etRollNumber.requestFocus()
            return
        }

        if (password.isEmpty()) {
            etPassword.error = "Enter password"
            etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.error = "Confirm password"
            etConfirmPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }

        btnRegister.isEnabled = false
        checkRollNumberAndCreateUser(name, email, rollNumber, password)
    }

    private fun checkRollNumberAndCreateUser(
        name: String,
        email: String,
        rollNumber: String,
        password: String
    ) {
        dbRef.child("students")
            .orderByChild("rollNumber")
            .equalTo(rollNumber)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        btnRegister.isEnabled = true
                        Toast.makeText(
                            this@RegisterActivity,
                            "Roll number already registered",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        createFirebaseUser(name, email, rollNumber, password)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    btnRegister.isEnabled = true
                    Toast.makeText(this@RegisterActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun createFirebaseUser(
        name: String,
        email: String,
        rollNumber: String,
        password: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""

                    val student = Student(
                        uid = uid,
                        name = name,
                        email = email,
                        rollNumber = rollNumber,
                        selectedBusId = "",
                        createdAt = System.currentTimeMillis()
                    )

                    dbRef.child("students").child(uid).setValue(student)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                            auth.signOut()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "Failed to save student data", Toast.LENGTH_SHORT).show()
                        }

                } else {
                    btnRegister.isEnabled = true
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Registration failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}