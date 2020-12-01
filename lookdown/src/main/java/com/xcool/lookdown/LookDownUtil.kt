package com.xcool.lookdown

import android.content.Context
import androidx.lifecycle.*
import com.andrefilgs.fileman.FilemanDrivers
import com.xcool.lookdown.LookDownConstants.LD_CONNECTTIMEOUT
import com.xcool.lookdown.LookDownConstants.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LookDownConstants.LD_TEMP_EXT
import com.xcool.lookdown.LookDownConstants.LD_TIMEOUT
import com.xcool.lookdown.log.LDLogger
import com.xcool.lookdown.model.DownloadState
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdown.utils.formatFileSize
import com.xcool.lookdownapp.utils.orDefault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


/**
 * @author André Filgueiras on 28/11/2020
 */
object LookDownUtil : ViewModel() {
  
  var forceInterrupt = false
  
  var chunkSize = LookDownConstants.LD_CHUNK_SIZE
  var driver = FilemanDrivers.Internal.type
  
  fun deleteFile(context: Context, drive: Int, folderStr: String, fileName: String): Boolean {
    return TempFileman.deleteFile(context, drive, folderStr, fileName)
  }
  
  var counter = 0
  var flag = true
  
  var keepRunning = true
  
  fun dummy(){
    counter++
    LDLogger.log("Counter: $counter")
  }
  
  fun download(context: Context, url: String, filename: String, fileExtension: String, folder: String? = LD_DEFAULT_FOLDER, resumeDownload: Boolean? = true, headers: Map<String, String>? = null): LDDownload {
    keepRunning = true
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var file: File? = null
    val ldDownload = LDDownload(url = url, filename = filename, state = DownloadState.Queued)
    var isResume = false
    try {
      file = TempFileman.getFile(context, driver, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")!!
      
      val url = URL(url)
      connection = url.openConnection() as HttpURLConnection
  
      file.let { file ->
        if (!file.exists()) {
          LDLogger.log("File doesn't exist")
          file.createNewFile()
          output = FileOutputStream(file, false)
        } else if (!resumeDownload.orDefault()) {
          LDLogger.log("File exists but download will be refreshed")
          file.createNewFile()
          output = FileOutputStream(file, false)
        } else {
          isResume = true
          LDLogger.log("File exists and download will be resumed from ${formatFileSize(file.length())}")
          //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
          connection!!.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
          // val lastModified = connection.getHeaderField("Last-Modified")
          // connection.setRequestProperty("If-Range", lastModified);
          output = FileOutputStream(file, true) //Append file
        }
        ldDownload.file = file
      }
      
      connection.readTimeout = LD_TIMEOUT
      connection.connectTimeout = LD_CONNECTTIMEOUT
      connection.useCaches = false
      connection.requestMethod = "GET" // GET, POST,HEAD,OPTIONS,PUT,DELETE,TRACE
      
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
      
      connection.doInput = true
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      
      headers?.let { map ->
        for ((key, value) in map) {
          connection.setRequestProperty(key, value)
        }
      }
      
      connection.connect()
      
      // expect HTTP 200 OK or 206 Partial, so we don't mistakenly save error report
      // instead of the file
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = DownloadState.Error("Server returned HTTP " + connection.responseCode + " " + connection.responseMessage)
        return ldDownload
      }
      
      // this will be useful to display download percentage
      // might be -1: server did not report the length
      var fileTotalLength = connection.contentLength.toLong()
      LDLogger.log("Bytes to download: $fileTotalLength")
      if (isResume) fileTotalLength += file.length()  //if is resumed, the server will download only the difference, so we need to add to fileLength the bytes already downloaded
      LDLogger.log("File Total Size: $fileTotalLength")
      ldDownload.fileSize = fileTotalLength
      
      ldDownload.lastModified = connection.getHeaderField("Last-Modified")
      
      // download the file
      input = connection.inputStream
      
      val data = ByteArray(chunkSize) //1024 = 57s / 1024*1024 = 75s / 1024*1024*3 = 108
      var bytesDownloaded = file.length()
      var chunk: Int
      
      ldDownload.state = DownloadState.Downloading
      while (input.read(data).also { chunk = it } != -1 && keepRunning) {
        bytesDownloaded += chunk.toLong()
        if (fileTotalLength > 0) {
          ldDownload.progress = (bytesDownloaded * 100 / fileTotalLength).toInt()
        }
        LDLogger.log("Writing at file")
        output!!.write(data, 0, chunk)
      }
      LDLogger.log("Is file downloaded ${file.length() == fileTotalLength}\nFile length: ${file.length()}\nFilLength: $fileTotalLength")
      if (file.length() == fileTotalLength) ldDownload.state = DownloadState.Downloaded else ldDownload.state = DownloadState.Incomplete
    } catch (e: Exception) {
      ldDownload.state = DownloadState.Error("Catch: ${e.stackTrace}")
      return ldDownload
    } finally {
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == DownloadState.Downloaded) {
          LDLogger.log("Removing .tmp extension")
          TempFileman.removeTempExtension(file!!, LD_TEMP_EXT)
        }
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
    }
    return ldDownload
  }
  
  
  private var _ldDownload: MutableLiveData<LDDownload> = MutableLiveData()
  var ldDownloadLiveData: LiveData<LDDownload> = _ldDownload
  
  suspend fun updateLDDownload(ldDownload: LDDownload) {
    withContext(Dispatchers.Main) {
      _ldDownload.value = ldDownload
    }
  }
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  suspend fun downloadFlow(context: Context, url: String, filename: String, fileExtension: String, folder: String? = LD_DEFAULT_FOLDER, resumeDownload: Boolean? = true, headers: Map<String, String>? = null) {
    LDLogger.log("Start")
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var file: File? = null
    var fileTotalLength = 0L
    val ldDownload = LDDownload(url = url, filename = filename, state = DownloadState.Queued)
    var isResume = false
    
    flow {
      emit(ldDownload)
      file = TempFileman.getFile(context, driver, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")!!
      val url = URL(url)
      connection = url.openConnection() as HttpURLConnection
      
      file?.let { file ->
        if (!file.exists()) {
          LDLogger.log("File doesn't exist")
          file.createNewFile()
          output = FileOutputStream(file, false)
        } else if (!resumeDownload.orDefault()) {
          LDLogger.log("File exists but download will be refreshed")
          file.createNewFile()
          output = FileOutputStream(file, false)
        } else {
          isResume = true
          LDLogger.log("File exists and download will be resumed from ${formatFileSize(file.length())}")
          //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
          connection!!.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
          // val lastModified = connection.getHeaderField("Last-Modified")
          // connection.setRequestProperty("If-Range", lastModified);
          output = FileOutputStream(file, true) //Append file
        }
        ldDownload.file = file
      }
      
      connection!!.readTimeout = LD_TIMEOUT
      connection!!.connectTimeout = LD_CONNECTTIMEOUT
      connection!!.useCaches = false
      connection!!.requestMethod = "GET" // GET, POST,HEAD,OPTIONS,PUT,DELETE,TRACE
      
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
      
      connection!!.doInput = true
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      
      headers?.let { map ->
        for ((key, value) in map) {
          connection!!.setRequestProperty(key, value)
        }
      }
      connection!!.connect()
      
      // expect HTTP 200 OK or 206 Partial, so we don't mistakenly save error report
      // instead of the file
      if (connection!!.responseCode != HttpURLConnection.HTTP_OK && connection!!.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = DownloadState.Error("Server returned HTTP " + connection!!.responseCode + " " + connection!!.responseMessage)
        emit(ldDownload)
        return@flow
      }
      
      // this will be useful to display download percentage
      // might be -1: server did not report the length
      fileTotalLength = connection!!.contentLength.toLong()
      if (isResume) fileTotalLength += file!!.length()  //if is resumed, the server will download only the difference, so we need to add to fileLength the bytes already downloaded
      
      ldDownload.fileSize = fileTotalLength
      
      ldDownload.lastModified = connection!!.getHeaderField("Last-Modified")
      
      // download the file
      input = connection!!.inputStream
      
      val data = ByteArray(chunkSize)
      var total = file!!.length()
      var count: Int
      var flag = true
      
      ldDownload.state = DownloadState.Downloading
      while (input!!.read(data).also { count = it } != -1 && flag) {
        total += count.toLong()
        if (fileTotalLength > 0) {
          ldDownload.progress = (total * 100 / fileTotalLength).toInt()
        }
        output!!.write(data, 0, count)
        
        emit(ldDownload)
        
        //todo 1000 testing resume download
        if (file!!.length() > fileTotalLength / 2 && forceInterrupt) {
          flag = false
        }
      }
      LDLogger.log("Is file downloaded ${file?.length() == fileTotalLength}\nFile length: ${file?.length()}\nFilLength: ${fileTotalLength}")
      if (file!!.length() == fileTotalLength) ldDownload.state = DownloadState.Downloaded
    }.catch { e ->
      LDLogger.log("On catch ${e.stackTrace}")
      ldDownload.state = DownloadState.Error("Catch: ${e.stackTrace}")  //todo 1000 test catch
      emit(ldDownload)
      return@catch
    }.onCompletion {
      LDLogger.log("On completion ${ldDownload.state.toString()}")
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == DownloadState.Downloaded) TempFileman.removeTempExtension(file!!, LD_TEMP_EXT)
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
      emit(ldDownload)
    }.flowOn(Dispatchers.IO).collect {
      // LDLogger.log("On Collect ${it.state.toString()}")
      updateLDDownload(it)
    }
  }
}