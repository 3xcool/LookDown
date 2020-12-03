package com.xcool.lookdown

import android.content.Context
import androidx.lifecycle.*
import com.xcool.lookdown.LDConstants.LD_CONNECTTIMEOUT
import com.xcool.lookdown.LDConstants.LD_DEFAULT_DRIVER
import com.xcool.lookdown.LDConstants.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LDConstants.LD_TEMP_EXT
import com.xcool.lookdown.LDConstants.LD_TIMEOUT
import com.xcool.lookdown.log.LDLogger
import com.xcool.lookdown.model.LDDownloadState
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
  
  var chunkSize = LDConstants.LD_CHUNK_SIZE
  
  fun activateLogs() {
    LDLogger.showLogs = true
  }
  
  fun deactivateLogs() {
    LDLogger.showLogs = false
  }
  
  fun deleteFile(context: Context, drive: Int, folderStr: String, filename: String, fileExtension: String): Boolean {
    return TempFileman.deleteFile(context, drive, folderStr, filename+fileExtension)
  }
  
  fun getFile(context: Context, drive: Int, folderStr: String, filename: String, fileExtension: String): File? {
    return TempFileman.getFile(context, drive, folderStr, filename+fileExtension)
  }
  
  var downloadJobs: MutableMap<String, Boolean> = mutableMapOf()
  
  fun cancelDownloadByUrl(url: String) {
    downloadJobs[url] = false
  }
  
  fun cancelAllDownloads() {
    for ((key, _) in downloadJobs) {
      LDLogger.log("Cancelling $key")
      downloadJobs[key] = false
    }
  }
  
  
  /**
   * Download any file
   * Be aware to avoid calling this method from Main thread
   * You can cancel by calling cancelAllDownloads or cancelDownloadByUrl
   *
   * @param context
   * @param urlStr
   * @param filename
   * @param fileExtension (e.g. ".mp4", ".png" ...)
   * @param driver (e.g. 0, 1 or 2, see FilemanDrivers)
   * @param folder (e.g. "/lookdown")
   * @param resumeDownload Restart download (will not download the same byte twice if the server allows it)
   * @param headers (any needed header for authentication for example)
   *
   */
  fun download(context: Context, urlStr: String, filename: String, fileExtension: String, driver: Int? = LD_DEFAULT_DRIVER, folder: String? = LD_DEFAULT_FOLDER, resumeDownload: Boolean? = true, headers: Map<String, String>? = null): LDDownload {
    downloadJobs[urlStr] = true
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var file: File? = null
    val ldDownload = LDDownload(url = urlStr, filename = filename, state = LDDownloadState.Queued)
    var isResume = false
    try {
      LDLogger.log("Starting download: $urlStr")
      file = TempFileman.getFile(context, driver ?: LD_DEFAULT_DRIVER, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")!!
      
      val url = URL(urlStr)
      connection = url.openConnection() as HttpURLConnection
      
      file.let { file ->
        if (file.exists() && resumeDownload.orDefault()) {
          isResume = true
          LDLogger.log("File exists and download will be resumed from ${formatFileSize(file.length())}")
          //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
          connection!!.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
          // val lastModified = connection.getHeaderField("Last-Modified")
          // connection.setRequestProperty("If-Range", lastModified);
          output = FileOutputStream(file, true) //Append file
        } else {
          LDLogger.log("Starting download from the beginning")
          file.createNewFile()
          output = FileOutputStream(file, false)
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
        for ((key, value) in map) connection.setRequestProperty(key, value)
      }
      
      connection.connect()
      
      // expect HTTP 200 OK or 206 Partial
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = LDDownloadState.Error("Server returned HTTP " + connection.responseCode + " " + connection.responseMessage)
        return ldDownload
      }
      
      // might be -1: server did not report the length
      var fileTotalLength = connection.contentLength.toLong()
      LDLogger.log("Bytes to download: $fileTotalLength")
      if (isResume) fileTotalLength += file.length()  //if is resumed, the server will download only the difference, so we need to add to fileLength the bytes already downloaded
      LDLogger.log("File Total Size: $fileTotalLength")
      ldDownload.fileSize = fileTotalLength
      
      ldDownload.lastModified = connection.getHeaderField("Last-Modified")
      
      input = connection.inputStream // download the file
      
      val data = ByteArray(chunkSize)
      var downloadedBytes = file.length()
      var chunk: Int
      
      ldDownload.state = LDDownloadState.Downloading
      while (input.read(data).also { chunk = it } != -1 && downloadJobs[urlStr].orDefault()) {
        LDLogger.log("Flag from $url: ${downloadJobs.get(url).orDefault(true)}")
        downloadedBytes += chunk.toLong()
        if (fileTotalLength > 0) ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
        LDLogger.log("Writing at file")
        output!!.write(data, 0, chunk)
      }
      LDLogger.log("Is file fully downloaded ${file.length() == fileTotalLength}\nBytes downloaded: ${file.length()}\nFile Length: $fileTotalLength")
      if (file.length() == fileTotalLength) ldDownload.state = LDDownloadState.Downloaded else ldDownload.state = LDDownloadState.Incomplete
    } catch (e: Exception) {
      ldDownload.state = LDDownloadState.Error("Catch: ${e.stackTrace}")
      return ldDownload
    } finally {
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == LDDownloadState.Downloaded) {
          LDLogger.log("Removing .tmp extension")
          TempFileman.removeTempExtension(file!!, LD_TEMP_EXT)
        }
        downloadJobs.remove(urlStr)
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
  /**
   * Download any file using flow
   *
   * Check download progress via ldDownloadLiveData
   *
   * @param context
   * @param urlStr
   * @param filename
   * @param fileExtension (e.g. ".mp4", ".png" ...)
   * * @param driver (e.g. 0, 1 or 2, see FilemanDrivers)
   * @param folder (e.g. "/lookdown")
   * @param resumeDownload Restart download (will not download the same byte twice)
   * @param headers (any needed header for authentication for example)
   *
   */
  suspend fun downloadWithFlow( context: Context,
                               urlStr: String,
                               filename: String,
                               fileExtension: String,
                               id: String? = null,
                               driver: Int? = LD_DEFAULT_DRIVER,
                               folder: String? = LD_DEFAULT_FOLDER,
                               resumeDownload: Boolean? = true,
                               headers: Map<String, String>? = null,
                                title:String?=null,
                                params:MutableMap<String,String>?=null
  ) {
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var file: File? = null
    var fileTotalLength = 0L
    val ldDownload = LDDownload(id = id ?: UUID.randomUUID().toString(), url = urlStr, filename = filename, state = LDDownloadState.Queued, params = params, title = title)
    var isResume = false
    
    flow {
      LDLogger.log("Starting download: $urlStr")
      emit(ldDownload)
      file = TempFileman.getFile(context, driver ?: LD_DEFAULT_DRIVER, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")!!
      val url = URL(urlStr)
      connection = url.openConnection() as HttpURLConnection
      
      file?.let { file ->
        if (file.exists() && resumeDownload.orDefault()) {
          isResume = true
          LDLogger.log("File exists and download will be restarted from ${formatFileSize(file.length())}")
          //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
          connection!!.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
          // val lastModified = connection.getHeaderField("Last-Modified")
          // connection.setRequestProperty("If-Range", lastModified);
          output = FileOutputStream(file, true) //Append file
        } else {
          LDLogger.log("Starting download from beginning")
          file.createNewFile()
          output = FileOutputStream(file, false)
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
        for ((key, value) in map) connection!!.setRequestProperty(key, value)
      }
      connection!!.connect()
      
      val httpResponse = "Server returned HTTP " + connection?.responseCode + " " + connection?.responseMessage
      LDLogger.log(httpResponse)
      
      // expect HTTP 200 OK or 206 Partial
      if (connection!!.responseCode != HttpURLConnection.HTTP_OK && connection!!.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = LDDownloadState.Error(httpResponse)
        emit(ldDownload)
        return@flow
      }
      
      fileTotalLength = connection!!.contentLength.toLong() // might be -1: server did not report the length
      if (isResume) fileTotalLength += file!!.length()  //if is resumed the server will download only the difference, so we need to add to fileLength which are the bytes already downloaded
      
      ldDownload.fileSize = fileTotalLength
      ldDownload.lastModified = connection!!.getHeaderField("Last-Modified")
      
      input = connection!!.inputStream // download the file
      
      val data = ByteArray(chunkSize)
      var downloadedBytes = file!!.length()
      var chunk: Int
      
      //Writing
      ldDownload.state = LDDownloadState.Downloading
      while (input!!.read(data).also { chunk = it } != -1) {
        downloadedBytes += chunk.toLong()
        if (fileTotalLength > 0) {
          ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
        }
        LDLogger.log("Writing file. Current progress: ${ldDownload.progress}")
        output!!.write(data, 0, chunk)
        emit(ldDownload)
      }
      LDLogger.log("Is file fully downloaded ${file?.length() == fileTotalLength}\nbytes downloaded: ${file?.length()}\nFile Size: $fileTotalLength")
      if (file!!.length() == fileTotalLength) ldDownload.state = LDDownloadState.Downloaded
    }.catch { e ->
      LDLogger.log("On catch ${e.stackTrace}")
      ldDownload.state = LDDownloadState.Error("Catch: ${e.stackTrace}")  //todo 1000 test catch
      emit(ldDownload)
      return@catch
    }.onCompletion {
      if (ldDownload.state != LDDownloadState.Downloaded) ldDownload.state = LDDownloadState.Incomplete
      ldDownload.state?.let { LDLogger.log("Finished download with state: ${it::class.java.simpleName}") }
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == LDDownloadState.Downloaded) TempFileman.removeTempExtension(file!!, LD_TEMP_EXT)
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
      emit(ldDownload)
    }.flowOn(Dispatchers.IO).collect {
      updateLDDownload(it)
    }
  }
}