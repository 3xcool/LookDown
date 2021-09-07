package com.andrefilgs.lookdownapp.samples.sample02.model

import android.content.Context
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.LookDownLite
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import java.io.File
import java.lang.Exception


/**
 * @author André Filgueiras on 01/12/2020
 */
object LDDownloadUtils {
  
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
          val tempFile = getFileIfExists(context, driver, folder, ldDownload.filename!!, ldDownload.fileExtension!!+ LDGlobals.LD_TEMP_EXT)
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
  
}