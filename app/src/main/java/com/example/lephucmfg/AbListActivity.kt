package com.example.lephucmfg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lephucmfg.data.AbRow
import com.example.lephucmfg.network.RetrofitClient
import com.example.lephucmfg.ui.AbAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AbListActivity : AppCompatActivity() {
    private val svc = RetrofitClient.ab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ab_list)

        val rv = findViewById<RecyclerView>(R.id.rv)
        rv.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            val r = svc.getAll()
            if (r.isSuccessful) rv.adapter = AbAdapter(r.body()!!)
            else Snackbar.make(rv, r.errorBody()!!.string(), Snackbar.LENGTH_LONG).show()
        }
    }
}
