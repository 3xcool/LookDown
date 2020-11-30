package com.xcool.lookdown

import android.content.Context
import com.andrefilgs.fileman.Fileman
import com.xcool.lookdownapp.utils.subLua
import java.io.File


//todo 1000 add this to Fileman library
/**
 * @author André Filgueiras on 28/11/2020
 */
object TempFileman {
  
  fun getFile(context: Context, drive: Int, folderStr: String, fileName: String): File? {
    val folder: File? = Fileman.getFolder(context, drive, folderStr)
    return if (folder != null) {
      File(folder, fileName)
    } else null
  }
  
  //https://stackoverflow.com/questions/2896733/how-to-rename-a-file-on-sdcard-with-android-application
  fun renameFile(currentFile: File, newFilename:String) {
    val currentLocation = currentFile.absolutePath.substringBeforeLast("/".single())
    val newFile = File("$currentLocation/$newFilename")
    currentFile.renameTo(newFile)
  }
  
  fun removeTempExtension(currentFile: File, tempExtension:String): Boolean {
    return try {
      val newFilename = currentFile.name.subLua(1, currentFile.name.length - tempExtension.length)
      renameFile(currentFile, newFilename)
      true
    }catch (e:Exception){
      false
    }
  }
}