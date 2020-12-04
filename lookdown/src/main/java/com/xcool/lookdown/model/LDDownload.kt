package com.xcool.lookdown.model

import com.xcool.lookdown.R
import java.io.File
import java.util.*

/**
 * @author André Filgueiras on 28/11/2020
 *
 *
 *
 * @param params generic mutable map for any other needed property
 */
data class LDDownload(
  var id: String = UUID.randomUUID().toString(),
  var url: String? = null,
  var filename: String? = null,
  var fileExtension: String? = null,
  var file: File? = null,
  var fileSize: Long? = null,
  var downloadedBytes: Long? = null,
  var lastModified: String? = null,
  var progress: Int = 0,
  var state: LDDownloadState? = LDDownloadState.Empty,
  var feedback: String? = null,
  var title: String? = null,
  var params: MutableMap<String, String>? = null,
){
  
  fun validateInfoForDownloading(): Boolean {
    if(url == null || filename == null || fileExtension ==null) return false
    return true
  }
  
  fun getDownloadStateImage(): Int {
    return when(this.state){
      LDDownloadState.Empty       -> R.drawable.ld_ic_baseline_download_24
      LDDownloadState.Queued      -> R.drawable.ld_ic_baseline_queue_24
      LDDownloadState.Downloading -> R.drawable.ld_ic_baseline_pause_24
      LDDownloadState.Paused      -> R.drawable.ld_ic_baseline_restart_24
      LDDownloadState.Incomplete  -> R.drawable.ld_ic_baseline_restart_24
      LDDownloadState.Downloaded  -> R.drawable.ld_ic_baseline_delete_24
      else                      ->  R.drawable.ld_ic_baseline_download_24
    }
  }
  
  fun getCurrentDownloadState(progressChanged:Boolean = false): LDDownloadState {
    if(!progressChanged){
      if(this.state == LDDownloadState.Paused) return LDDownloadState.Paused
      if(this.state == LDDownloadState.Queued) return LDDownloadState.Queued
      if(this.state == LDDownloadState.Incomplete) return LDDownloadState.Incomplete
      if(this.state == LDDownloadState.Downloading && this.progress < 100) return LDDownloadState.Downloading
      if(this.state is LDDownloadState.Error ) return this.state as LDDownloadState.Error
    }
    return getStateByProgress(this.progress)
  }
  
  
  fun updateProgress(progress:Int){
    this.progress = progress
    this.state = getCurrentDownloadState(true)
  }
  
  fun setStateAfterStopDownload(){
    val state = getStateByProgress(this.progress)
    this.state = when(state){
      LDDownloadState.Empty ->  LDDownloadState.Empty
      LDDownloadState.Downloading -> LDDownloadState.Incomplete
      LDDownloadState.Downloaded ->  LDDownloadState.Downloaded
      else -> LDDownloadState.Empty
    }
    
  }
  
  private fun getStateByProgress(progress: Int):LDDownloadState{
    return when(progress){
      0 -> LDDownloadState.Empty
      in 1..99 -> LDDownloadState.Downloading
      100 -> LDDownloadState.Downloaded
      else -> LDDownloadState.Empty
    }
  }


}