package com.example.shaktialert

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class ContactsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)
        
        val et1 = view.findViewById<EditText>(R.id.etContact1)
        val et2 = view.findViewById<EditText>(R.id.etContact2)
        val etEmail = view.findViewById<EditText>(R.id.etGuardianEmail)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val prefs = requireContext().getSharedPreferences("shakti_prefs", Context.MODE_PRIVATE)
        et1.setText(prefs.getString("emergency_contact_1", ""))
        et2.setText(prefs.getString("emergency_contact_2", ""))
        etEmail.setText(prefs.getString("guardian_email", ""))

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("emergency_contact_1", et1.text.toString())
                .putString("emergency_contact_2", et2.text.toString())
                .putString("guardian_email", etEmail.text.toString())
                .apply()
            Toast.makeText(context, "Contacts Saved", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
