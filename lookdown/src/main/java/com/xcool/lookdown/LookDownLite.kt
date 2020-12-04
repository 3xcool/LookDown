package com.xcool.lookdown

import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.*
import com.andrefilgs.fileman.Fileman
import com.xcool.lookdown.LDGlobals.LD_CHUNK_SIZE
import com.xcool.lookdown.LDGlobals.LD_CONNECTTIMEOUT
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_DRIVER
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LDGlobals.LD_PROGRESS_RENDER_DELAY
import com.xcool.lookdown.LDGlobals.LD_TEMP_EXT
import com.xcool.lookdown.LDGlobals.LD_TIMEOUT
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
 *
 * Simple Object with static functions
 *
 * It is preferable that you use the LookDown class
 */

object LookDownLite : ViewModel() {
  
  var progressRenderDelay = LD_PROGRESS_RENDER_DELAY
  var chunkSize = LD_CHUNK_SIZE
  var timeout = LD_TIMEOUT
  var connectTimeout = LD_CONNECTTIMEOUT
  
  fun deleteFile(context: Context, driver: Int, folder: String, filename: String, fileExtension: String): Boolean {
    return Fileman.deleteFile(context, driver, folder, filename + fileExtension)
  }
  
  fun getFile(context: Context, driver: Int, folder: String, filename: String, fileExtension: String): File? {
    return Fileman.getFile(context, driver, folder, filename + fileExtension)
  }
  
  //to Allow cancel
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
   * Override Download function with LDDownload as parameter
   */
  fun downloadSimple(context: Context,
                     ldDownload: LDDownload,
                     driver: Int? = LD_DEFAULT_DRIVER,
                     folder: String? = LD_DEFAULT_FOLDER,
                     resumeDownload: Boolean?=true,
                     headers: Map<String, String>? = null,
  ): LDDownload {
    return try {
      downloadSimple(context, ldDownload.url!!, ldDownload.filename!!, ldDownload.fileExtension!!, driver, folder, resumeDownload, headers, ldDownload)
    }catch (e:Exception){
      ldDownload.state = LDDownloadState.Error(e.message ?: e.localizedMessage)
      ldDownload
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
   * @param headers any needed header for HTTP authentication
   *
   */
  fun downloadSimple(context: Context,
                     urlStr: String,
                     filename: String,
                     fileExtension: String,
                     driver: Int? = LD_DEFAULT_DRIVER,
                     folder: String? = LD_DEFAULT_FOLDER,
                     resumeDownload: Boolean? = true,
                     headers: Map<String, String>? = null,
                     mLDDownload: LDDownload?=null
  ): LDDownload {
    downloadJobs[urlStr] = true
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var tempFileExists = false
    val ldDownload = mLDDownload ?: LDDownload(url = urlStr, filename = filename, fileExtension = fileExtension)
    ldDownload.state = LDDownloadState.Queued
    
    if(!ldDownload.validateInfoForDownloading()){
      ldDownload.state = LDDownloadState.Error("Invalid input data")
      return ldDownload
    }
    
    val file = Fileman.getFile(context, driver ?: LD_DEFAULT_DRIVER, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")
    if (file == null) {
      ldDownload.state = LDDownloadState.Error("File can't be null")
      return ldDownload
    }
    
    try {
      val url = URL(urlStr)
      connection  = url.openConnection() as HttpURLConnection
      
      LDLogger.log("Starting download: $urlStr")
      if (file.exists() && resumeDownload.orDefault()) {
        tempFileExists = true
        LDLogger.log("File exists and download will be resumed from ${formatFileSize(file.length())}")
        //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
        connection.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
        // val lastModified = connection.getHeaderField("Last-Modified")
        // connection.setRequestProperty("If-Range", lastModified);
        output = FileOutputStream(file, true) //Append file
      } else {
        LDLogger.log("Starting download from the beginning")
        file.createNewFile()
        output = FileOutputStream(file, false)
      }
      ldDownload.file = file
      
      connection.readTimeout = timeout
      connection.connectTimeout = connectTimeout
      connection.useCaches = false
      connection.requestMethod = "GET" // GET, POST,HEAD,OPTIONS,PUT,DELETE,TRACE
      
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
      
      connection.doInput = true
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      
      headers?.let { map ->
        for ((key, value) in map) connection.setRequestProperty(key, value)
      }
      
      connection.connect()
      
      val httpResponse = "Server returned HTTP " + connection.responseCode + " " + connection.responseMessage
      LDLogger.log(httpResponse)
      
      // expect HTTP 200 OK or 206 Partial
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = LDDownloadState.Error("Server returned HTTP " + connection.responseCode + " " + connection.responseMessage)
        return ldDownload
      }
      
      // might be -1: server did not report the length
      var fileTotalLength = connection.contentLength.toLong()
      LDLogger.log("Bytes to download: $fileTotalLength")
      if (tempFileExists && fileTotalLength > 0) fileTotalLength += file.length()  //if is resumed, the server will download only the difference, so we need to add to fileLength the bytes already downloaded
      LDLogger.log("File Total Size: $fileTotalLength")
      ldDownload.fileSize = fileTotalLength
      
      ldDownload.lastModified = connection.getHeaderField("Last-Modified")
      
      input = connection.inputStream // download the file
      
      val data = ByteArray(chunkSize)
      var downloadedBytes = file.length()
      var chunk: Int
      
      ldDownload.state = LDDownloadState.Downloading
      LDLogger.log("Downloading...")
      while (input.read(data).also { chunk = it } != -1 && downloadJobs[urlStr].orDefault()) {
        downloadedBytes += chunk.toLong()
        ldDownload.downloadedBytes = downloadedBytes
        if (fileTotalLength > 0) ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
        output.write(data, 0, chunk)
      }
      LDLogger.log("Is file fully downloaded? ${file.length() == fileTotalLength} (Downloaded Bytes: ${file.length()} | File Size: $fileTotalLength)")
      if (file.length() == fileTotalLength) ldDownload.state = LDDownloadState.Downloaded else ldDownload.state = LDDownloadState.Incomplete
    } catch (e: Exception) {
      LDLogger.log("Catch ${e.stackTrace}")
      ldDownload.state = LDDownloadState.Error("Catch: ${e.stackTrace}")
      return ldDownload
    } finally {
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == LDDownloadState.Downloaded) {
          LDLogger.log("Removing .tmp extension")
          Fileman.removeTempExtension(file, LD_TEMP_EXT)
        }
        downloadJobs.remove(urlStr)
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
    }
    ldDownload.state?.let { LDLogger.log("Finished download with state: ${it::class.java.simpleName}") }
    return ldDownload
  }
  
  
  private var lastRender = 0L
  
  private var _ldDownload: MutableLiveData<LDDownload> = MutableLiveData()
  var ldDownloadLiveData: LiveData<LDDownload> = _ldDownload
  
  /**
   * @param forceUpdate will always update the value
   */
  suspend fun updateLDDownload(ldDownload: LDDownload, forceUpdate :Boolean= true) {
    if(forceUpdate){
      withContext(Dispatchers.Main) { _ldDownload.value = ldDownload}
    }else{
      val now = System.currentTimeMillis()
      val delta = now - lastRender
      if(delta > progressRenderDelay){
        lastRender = now
        withContext(Dispatchers.Main) { _ldDownload.value = ldDownload}
      }
    }
  }
  
  /**
   * Download any file
   *
   * Override DownloadWithFlow function with LDDownload as parameter
   */
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  suspend fun download(context: Context,
                       ldDownload: LDDownload,
                       driver: Int? = LD_DEFAULT_DRIVER,
                       folder: String? = LD_DEFAULT_FOLDER,
                       resumeDownload: Boolean?=true,
                       headers: Map<String, String>? = null,
  ){
    try {
      return  download(context, urlStr = ldDownload.url!!, filename =  ldDownload.filename!!, fileExtension =  ldDownload.fileExtension!!, id = ldDownload.id,
                       driver = driver, folder =  folder, resumeDownload =  resumeDownload, headers =  headers,
                       params = ldDownload.params, title = ldDownload.title, mLDDownload = ldDownload )
    }catch (e:Exception){
      ldDownload.state = LDDownloadState.Error(e.message ?: e.localizedMessage)
      updateLDDownload(ldDownload)
    }
  }

  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  /**
   * Download any file using flow
   *
   * Check download progress via ldDownloadLiveData
   *
   * @param context*
   * @param urlStr*
   * @param filename*
   * @param fileExtension* (e.g. ".mp4", ".png" ...)
   * @param driver (e.g. 0, 1 or 2, see FilemanDrivers)
   * @param folder (e.g. "/lookdown")
   * @param resumeDownload Restart download (will not download the same byte twice)
   * @param headers any needed header for HTTP authentication
   * @param title
   * @param params generic mutable map for any other needed property
   *
   */
  suspend fun download(context: Context,
                       urlStr: String,
                       filename: String,
                       fileExtension: String,
                       id: String? = null,
                       driver: Int? = LD_DEFAULT_DRIVER,
                       folder: String? = LD_DEFAULT_FOLDER,
                       resumeDownload: Boolean? = true,
                       headers: Map<String, String>? = null,
                       title: String? = null,
                       params: MutableMap<String, String>? = null,
                       mLDDownload: LDDownload?=null
  ) {
    var input: InputStream? = null
    var output: OutputStream? = null
    var fileTotalLength = 0L
    var tempFileExists = false
    
    val ldDownload = mLDDownload ?: LDDownload(id = id ?: UUID.randomUUID().toString(), url = urlStr, filename = filename, state = LDDownloadState.Queued, title = title, params = params)
    val file = Fileman.getFile(context, driver ?: LD_DEFAULT_DRIVER, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")
    
    if (file == null) {
      ldDownload.state = LDDownloadState.Error("File can't be null")
      updateLDDownload(ldDownload)
      return
    }
  
    val url = URL(urlStr)
    val connection :HttpURLConnection? = url.openConnection() as HttpURLConnection
  
    if (connection == null) {
      ldDownload.state = LDDownloadState.Error("Failed to create a connection")
      updateLDDownload(ldDownload)
      return
    }
    
    flow {
      LDLogger.log("Starting download: $urlStr")
      emit(ldDownload)
      if (file.exists() && resumeDownload.orDefault()) {
        tempFileExists = true
        LDLogger.log("File exists and download will be restarted from ${formatFileSize(file.length())}")
        //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
        connection.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
        // val lastModified = connection.getHeaderField("Last-Modified")
        // connection.setRequestProperty("If-Range", lastModified);
        output = FileOutputStream(file, true) //Append file
      } else {
        LDLogger.log("Starting download from beginning")
        file.createNewFile()
        output = FileOutputStream(file, false)
      }
      ldDownload.file = file
      
      connection.readTimeout = timeout
      connection.connectTimeout = connectTimeout
      connection.useCaches = false
      connection.requestMethod = "GET" // GET, POST,HEAD,OPTIONS,PUT,DELETE,TRACE
      
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
      
      connection.doInput = true
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      
      headers?.let { map ->
        for ((key, value) in map) connection.setRequestProperty(key, value)
      }
      connection.connect()
      
      val httpResponse = "Server returned HTTP " + connection.responseCode + " " + connection.responseMessage
      LDLogger.log(httpResponse)
      
      // expect HTTP 200 OK or 206 Partial
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        ldDownload.state = LDDownloadState.Error(httpResponse)
        emit(ldDownload)
        return@flow
      }
      
      fileTotalLength = connection.contentLength.toLong() // might be -1: server did not report the length
      if (tempFileExists && fileTotalLength > 0) fileTotalLength += file.length()  //if is resumed the server will download only the difference, so we need to add to fileLength which are the bytes already downloaded
      
      ldDownload.fileSize = fileTotalLength
      ldDownload.lastModified = connection.getHeaderField("Last-Modified")
      
      input = connection.inputStream // download the file
      
      val data = ByteArray(chunkSize)
      var downloadedBytes = file.length()
      var chunk: Int
      
      //Writing
      ldDownload.state = LDDownloadState.Downloading
      LDLogger.log("Downloading...")
      while (input!!.read(data).also { chunk = it } != -1) {
        downloadedBytes += chunk.toLong()
        ldDownload.downloadedBytes = downloadedBytes
        if (fileTotalLength > 0) ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
        // LDLogger.log("Writing ${ldDownload.filename}. Current progress: ${ldDownload.progress}")
        output!!.write(data, 0, chunk)
        emit(ldDownload)
      }
      LDLogger.log("Is file ${ldDownload.filename} fully downloaded? ${file.length() == fileTotalLength} (Downloaded Bytes: ${file.length()} | File Size: $fileTotalLength)")
    }.catch { e ->
      LDLogger.log("Catch ${e.stackTrace}")
      ldDownload.state = LDDownloadState.Error("Catch: ${e.stackTrace}")
      emit(ldDownload)
      return@catch
    }.onCompletion {
      if (file.length() == fileTotalLength) ldDownload.state = LDDownloadState.Downloaded else ldDownload.state = LDDownloadState.Incomplete
      ldDownload.state?.let { LDLogger.log("Finished download with state: ${it::class.java.simpleName}") }
      try {
        output?.close()
        input?.close()
        if (ldDownload.state == LDDownloadState.Downloaded) Fileman.removeTempExtension(file, LD_TEMP_EXT)
      } catch (ignored: IOException) {
      }
      connection.disconnect()
      emit(ldDownload)
    }.flowOn(Dispatchers.IO).collect {
      updateLDDownload(it)
    }
  }

}