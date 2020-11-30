package com.xcool.lookdownapp.samples.sample01

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.xcool.lookdownapp.R
import com.xcool.lookdownapp.databinding.ActivitySample01Binding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Sample01Activity : AppCompatActivity() {
  
  // private val takeatour = "https://www.dropbox.com/s/exjzq4qhcpatylm/takeatour.mp4?dl=1"
  private val takeatour = "https://tekmoon.com/spaces/takeATour.mp4"
  // private val takeatour = "https://tekmoon.com/spaces/images/3XCool_evt.png"
  // private val takeatour = "https://drive.google.com/file/d/1Q-ZeezvF3thypfLcip1FrfsrpX4YhUKR/view?usp=sharing"
  private val taylorswift = "https://www.dropbox.com/s/9zubam40vxkujir/taylorswift.png?dl=1"
  
  private var _binding: ActivitySample01Binding? = null
  private val binding get() = _binding!!
  
  private val viewModel : Sample01ViewModel by viewModels()
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivitySample01Binding.inflate(layoutInflater)
    setContentView(binding.root)
    setClicklisteners()
    subscribeObservers()
  }
  
  private fun subscribeObservers() {
    
    viewModel.loading.observe(this, {loading->
      binding.progressbar.visibility = if(loading) View.VISIBLE else View.GONE
    })
  
    viewModel.feedback.observe(this, {message->
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    })
  }
  
  private fun setClicklisteners() {
    binding.btnDownload.setOnClickListener {
      viewModel.download(takeatour)
    }
  }
}