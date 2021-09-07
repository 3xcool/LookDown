package com.andrefilgs.lookdownapp.samples.sample01

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdownapp.app.AppLogger
import com.andrefilgs.lookdownapp.databinding.ActivitySample01Binding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@AndroidEntryPoint
class Sample01Activity : AppCompatActivity() {
  
  private val takeatour = "https://www.dropbox.com/s/exjzq4qhcpatylm/takeatour.mp4?dl=1"
  // private val takeatour = "https://tekmoon.com/spaces/takeATour.mp4"
  // private val 3xCoolEVT = "https://tekmoon.com/spaces/images/3XCool_evt.png"
  
  
  private var _binding: ActivitySample01Binding? = null
  private val binding get() = _binding!!
  
  private val viewModel : Sample01ViewModel by viewModels()
  
  var start = 0L
  var chunkSize = LDGlobals.LD_CHUNK_SIZE
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivitySample01Binding.inflate(layoutInflater)
    setContentView(binding.root)
    setClickListeners()
    subscribeObservers()
  }
  
  private fun subscribeObservers() {
    
    viewModel.loading.observe(this, {loading->
      binding.progressbar.visibility = if(loading) View.VISIBLE else View.GONE
    })
  
    viewModel.feedback.observe(this, {message->
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    })
    
    viewModel.ldDownload.observe(this, { ldDownload ->
      renderScreen(ldDownload)
    })
  }
  
  private fun renderScreen(ldDownload: LDDownload){
    when(ldDownload.state){
      LDDownloadState.Downloading -> binding.progressbar.visibility = View.VISIBLE
      LDDownloadState.Queued      -> binding.progressbar.visibility = View.VISIBLE
      else                        -> binding.progressbar.visibility = View.GONE
    }
    binding.progressbarDownload.isIndeterminate = ldDownload.state == LDDownloadState.Queued
    binding.progressbarDownload.progress = ldDownload.progress
    binding.tvProgress.text = "${ldDownload.state!!::class.java.simpleName}: ${ldDownload.progress}%"
  
    if(ldDownload.state == LDDownloadState.Downloaded) finishDownload()
  }
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  private fun setClickListeners() {
    binding.btnDecreaseChunck.setOnClickListener {
      if(chunkSize == 1024){
        toaster("Can't be less than 1024")
        return@setOnClickListener
      }
      changeChunkSize(-1024)
    }
  
    binding.btnIncreaseChunck.setOnClickListener {
      changeChunkSize(+1024)
    }
    
    binding.btnDelete.setOnClickListener {
      viewModel.deleteFile()
    }
    
    binding.btnCancel.setOnClickListener {
      viewModel.stopDownload()
    }
    
    binding.btnDownload.setOnClickListener {
      startDownload(false)
    }
  
    binding.btnResume.setOnClickListener {
      startDownload(true)
    }
  }
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  private fun startDownload(withResume:Boolean){
    start = System.currentTimeMillis()
    AppLogger.log("Start download")
    binding.tvOutput.text = ""
    viewModel.downloadWithFlow(takeatour, withResume)
  }
  
  private fun finishDownload(){
    val end = System.currentTimeMillis()
    val delta = end-start
    binding.tvOutput.text = "End after ${delta}ms"
    AppLogger.log("End after ${delta}ms")
  }
  
  private fun changeChunkSize(increaseDecrease:Int){
    chunkSize += increaseDecrease
    binding.tvChunckSize.text = chunkSize.toString()
    viewModel.setChunkSize(chunkSize)
  }
  
  private fun toaster(msg:String){
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
  }

}