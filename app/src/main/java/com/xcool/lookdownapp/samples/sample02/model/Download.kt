package com.xcool.lookdownapp.samples.sample02.model


import com.xcool.lookdownapp.R
import java.io.Serializable
import java.util.*


/**
 * @author André Filgueiras on 25/11/2020
 */
data class Download(
  val id:String=UUID.randomUUID().toString(),
  var progress:Int=0,
  val filename:String?=null,
  val title:String?=null,
  var state: DownloadState?= DownloadState.Empty,
):Serializable{
  
  
  fun getDownloadStateImage(): Int {
    return when(this.state){
      DownloadState.Empty       -> R.drawable.ic_baseline_arrow_downward_24
      DownloadState.Queued      -> R.drawable.ic_baseline_queue_24
      DownloadState.Downloading -> R.drawable.ic_baseline_pause_24
      DownloadState.Paused      -> R.drawable.ic_baseline_restart_24
      DownloadState.Downloaded  -> R.drawable.ic_baseline_delete_24
      else                      ->  R.drawable.ic_baseline_arrow_downward_24
    }
  }
  
  fun getCurrentDownloadState(): DownloadState {
    if(this.state == DownloadState.Paused) return DownloadState.Paused
    if(this.state == DownloadState.Downloading && this.progress < 100) return DownloadState.Downloading
    return when(this.progress){
      0 -> DownloadState.Empty
      in 1..99 -> DownloadState.Downloading
      100 -> DownloadState.Downloaded
      else -> DownloadState.Empty
    }
  }
  
  private fun updateDownloadState(){
    this.state = getCurrentDownloadState()
  }
  
  fun updateProgress(progress:Int){
    this.progress = progress
    updateDownloadState()
  }
  
  companion object{
    
    fun buildFakeDownloadList():List<Download>{
      val mutableList = mutableListOf<Download>()
      for (i in 1..15 ){
        mutableList.add(Download(i.toString(), 0, "Filename $i", "File $i", DownloadState.Empty))
      }
      return mutableList
    }
    
  }
  
  
}
