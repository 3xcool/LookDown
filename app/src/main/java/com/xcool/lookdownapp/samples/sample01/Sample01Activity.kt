package com.xcool.lookdownapp.samples.sample01

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.viewModelScope
import com.xcool.lookdown.LookDownConstants
import com.xcool.lookdown.model.DownloadState
import com.xcool.lookdownapp.app.AppLogger
import com.xcool.lookdownapp.databinding.ActivitySample01Binding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.InternalCoroutinesApi

@AndroidEntryPoint
class Sample01Activity : AppCompatActivity() {
  
  // private val takeatour = "https://www.dropbox.com/s/exjzq4qhcpatylm/takeatour.mp4?dl=1"  //todo 10000 check when server don't pass file length
  private val takeatour = "https://tekmoon.com/spaces/takeATour.mp4"
  // private val takeatour = "https://tekmoon.com/spaces/images/3XCool_evt.png"
  // private val takeatour = "https://drive.google.com/file/d/1Q-ZeezvF3thypfLcip1FrfsrpX4YhUKR/view?usp=sharing"
  private val taylorswift = "https://www.dropbox.com/s/9zubam40vxkujir/taylorswift.png?dl=1"
  
  private var _binding: ActivitySample01Binding? = null
  private val binding get() = _binding!!
  
  private val viewModel : Sample01ViewModel by viewModels()
  
  var start = 0L
  var chunkSize = LookDownConstants.LD_CHUNK_SIZE
  
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
      when(ldDownload.state){
        DownloadState.Downloading -> binding.progressbar.visibility = View.VISIBLE
        DownloadState.Queued -> binding.progressbar.visibility = View.VISIBLE
        else -> binding.progressbar.visibility = View.GONE
      }
      binding.progressbarDownload.isIndeterminate = ldDownload.state == DownloadState.Queued
      binding.progressbarDownload.progress = ldDownload.progress
      binding.tvProgress.text = "${ldDownload.state!!::class.java.simpleName}: ${ldDownload.progress}%"
    
      if(ldDownload.state == DownloadState.Downloaded){
        finishedDownload()
      }
    })
    
    viewModel.ldDownloadFlow.observe(this, { ldDownload ->
      when(ldDownload.state){
        DownloadState.Downloading -> binding.progressbar.visibility = View.VISIBLE
        DownloadState.Queued -> binding.progressbar.visibility = View.VISIBLE
        else -> binding.progressbar.visibility = View.GONE
      }
      binding.progressbarDownload.isIndeterminate = ldDownload.state == DownloadState.Queued
      binding.progressbarDownload.progress = ldDownload.progress
      binding.tvProgress.text = "${ldDownload.state!!::class.java.simpleName}: ${ldDownload.progress}%"
  
      if(ldDownload.state == DownloadState.Downloaded){
        finishedDownload()
      }
    })
  }
  
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
      viewModel.stopAllDownloads(binding.switchShowProgress.isChecked)
    }
    
    binding.btnDownload.setOnClickListener {
      start = System.currentTimeMillis()
      AppLogger.log("Start download")
      
      if(binding.switchShowProgress.isChecked){
        binding.tvOutput.text = ""
        viewModel.downloadFlow(takeatour)
      }else{
        binding.tvOutput.text = ""
        binding.progressbarDownload.progress = 0
        binding.tvProgress.text = "Downloading..."
        viewModel.download(takeatour)
        // viewModel.dummy()
      }
      
    }
  }
  
  private fun finishedDownload(){
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