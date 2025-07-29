package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

import com.example.lephucmfg.ABTestingActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.frontpage)

        findViewById<Button>(R.id.btnAbTesting).setOnClickListener {
            startActivity(Intent(this, ABTestingActivity::class.java))
        }
        findViewById<Button>(R.id.btnMachineLog).setOnClickListener {
            startActivity(Intent(this, MachineLogActivity::class.java))
        }
    }
}
