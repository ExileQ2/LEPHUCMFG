package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.data.AbInsertDto
import com.example.lephucmfg.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ABTestingActivity : AppCompatActivity() {
    private val svc = RetrofitClient.ab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ab_testing)

        val etA = findViewById<EditText>(R.id.etA)
        val etB = findViewById<EditText>(R.id.etB)
        findViewById<Button>(R.id.btnSubmit).setOnClickListener { v ->
            val dto = AbInsertDto(etA.text.toString(), etB.text.toString())
            lifecycleScope.launch {
                val r = svc.postAb(dto)
                if (r.isSuccessful) Snackbar.make(v, "Saved", Snackbar.LENGTH_SHORT).show()
                else Snackbar.make(v, r.errorBody()!!.string(), Snackbar.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnView).setOnClickListener {
            startActivity(Intent(this, AbListActivity::class.java))
        }
    }
}
