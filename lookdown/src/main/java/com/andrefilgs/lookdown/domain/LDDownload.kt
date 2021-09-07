package com.andrefilgs.lookdown.domain

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
  
  
  fun getCurrentDownloadState(progressChanged:Boolean = false): LDDownloadState {
    if(!progressChanged){
      if(this.state == LDDownloadState.Downloading && this.progress < 100) return LDDownloadState.Downloading
      return this.state ?: LDDownloadState.Empty
    }
    return getStateByProgress(this.progress)
  }
  
  
  fun updateProgress(progress:Int){
    this.progress = progress
    if(progress == 0) this.downloadedBytes = 0
    this.state = getCurrentDownloadState(true)
  }
  
  fun setStateAfterDownloadStops(){
    val state = getStateByProgress(this.progress)
    this.state = when(state){
      LDDownloadState.Downloading -> LDDownloadState.Incomplete
      LDDownloadState.Downloaded  -> LDDownloadState.Downloaded
      else                        -> LDDownloadState.Empty
    }
    
  }
  
  private fun getStateByProgress(progress: Int): LDDownloadState {
    return when(progress){
      0 -> LDDownloadState.Empty
      in 1..99 -> LDDownloadState.Downloading
      100 -> LDDownloadState.Downloaded
      else -> LDDownloadState.Empty
    }
  }


}