package com.xcool.lookdownapp.samples.sample02.model

import android.content.Context
import com.xcool.lookdown.LDGlobals
import com.xcool.lookdown.LookDownLite
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdown.model.LDDownloadState
import java.io.File
import java.lang.Exception


/**
 * @author André Filgueiras on 01/12/2020
 */
object LDDownloadUtils {
  
  private val takeatour = "https://www.dropbox.com/s/exjzq4qhcpatylm/takeatour.mp4?dl=1"
  // private val takeatour = "https://tekmoon.com/spaces/takeATour.mp4"
  
  fun getFileIfExists(context:Context, driver:Int?, folder:String?, filename:String, fileExtension:String): File? {
    return LookDownLite.getFile(context, driver ?: LDGlobals.LD_DEFAULT_DRIVER, folder?: LDGlobals.LD_DEFAULT_FOLDER, filename, fileExtension)
  }
  
  fun checkFileExists(context:Context, driver: Int, folder: String?, list:MutableList<LDDownload>):MutableList<LDDownload>{
    list.forEach {ldDownload ->
      try {
        val file = getFileIfExists(context, driver, folder, ldDownload.filename!!, ldDownload.fileExtension!!)
        if(file != null && file.exists()){
          ldDownload.file = file
          ldDownload.downloadedBytes = file.length()
          ldDownload.updateProgress(100)
        }else{
          val tempFile = getFileIfExists(context, driver, folder, ldDownload.filename!!, ldDownload.fileExtension!!+LDGlobals.LD_TEMP_EXT)
          if(tempFile != null && tempFile.exists()){
            ldDownload.downloadedBytes = tempFile.length()
            ldDownload.state = LDDownloadState.Incomplete
          }
        }
      }catch (e:Exception){
      }
    }
    return list
  }
  
  fun buildFakeLDDownloadList(): MutableList<LDDownload> {
    val mutableList = mutableListOf<LDDownload>()
    for (i in 1..15) {
      mutableList.add(LDDownload(id= i.toString(), url = takeatour, filename = "Take a Tour $i", fileExtension = ".mp4", progress = 0, state = LDDownloadState.Empty, title = "Filename $i"
        ))
    }
    return mutableList
  }
  
}