package com.andrefilgs.lookdownapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.andrefilgs.lookdownapp.samples.sample01.Sample01Activity
import com.andrefilgs.lookdownapp.samples.sample02.Sample02Activity
import com.andrefilgs.lookdownapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  
  private var _binding: ActivityMainBinding? = null
  private val binding get() = _binding!!
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setClickListeners()
  }
  
  private fun setClickListeners() {
    binding.btnSample01.setOnClickListener {
      goTo(Sample01Activity::class.java)
    }
  
    binding.btnSample02.setOnClickListener {
      goTo(Sample02Activity::class.java)
    }

  }
  
  private fun goTo(activity: Class<*>){
    startActivity(Intent(this, activity))
  }
}