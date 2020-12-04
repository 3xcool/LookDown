package com.xcool.lookdown

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xcool.lookdown.LDGlobals.LD_CHUNK_SIZE
import com.xcool.lookdown.LDGlobals.LD_CONNECTTIMEOUT
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_DRIVER
import com.xcool.lookdown.LDGlobals.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LDGlobals.LD_TIMEOUT
import com.xcool.lookdown.log.LDLogger
import com.xcool.lookdown.model.LDDownload
import com.xcool.lookdown.model.LDDownloadState
import com.xcool.lookdown.utils.formatFileSize
import com.xcool.lookdownapp.utils.orDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


/**
 * @author André Filgueiras on 28/11/2020
 */

/**
 * Main Class
 */
class LookDown(private val context: Context,
               private var id: String = UUID.randomUUID().toString(),
               private var driver:Int=LD_DEFAULT_DRIVER,
               private var folder:String= LD_DEFAULT_FOLDER,
               private var tryToResume: Boolean = true,
               private var headers: Map<String, String>? = null,
               private var chunkSize: Int= LD_CHUNK_SIZE,
               private var timeout: Int= LD_TIMEOUT,
               private var connectTimeout: Int= LD_CONNECTTIMEOUT,
               private var fileExtension:String= ""
               ) {
    
  //region Companion
  companion object{
    fun activateLogs() { LDLogger.showLogs = true }
    fun deactivateLogs() { LDLogger.showLogs = false }
    fun setDefaultTimeout(timeout:Int){ LDGlobals.LD_TIMEOUT = timeout }
    fun setDefaultConnectionTimeout(timeout:Int){ LDGlobals.LD_CONNECTTIMEOUT = timeout }
    fun setDefaultDriver(driver:Int){ LD_DEFAULT_DRIVER = driver }
    fun setDefaultFolder(folder:String){ LD_DEFAULT_FOLDER = folder }
    fun setDefaultChunkSize(chunkSize:Int){ LD_CHUNK_SIZE = chunkSize }
  }
  //endregion
  
  
  //region Builder
  class Builder(private val context: Context){
    private var id: String = UUID.randomUUID().toString()
    private var driver:Int=LD_DEFAULT_DRIVER
    private var folder:String= LD_DEFAULT_FOLDER
    private var forceResume:Boolean = true
    private var headers: Map<String, String>? = null
    private var chunkSize: Int= LD_CHUNK_SIZE
    private var timeout: Int= LD_TIMEOUT
    private var connectTimeout: Int= LD_CONNECTTIMEOUT
    private var fileExtension: String= ""
    
    fun setId(id:String) {this.id = id }
    fun setDriver(driver:Int) {this.driver = driver}
    fun setFolder(folder:String) {this.folder = folder}
    fun setForceResume(forceResume: Boolean) {this.forceResume = forceResume}
    fun setHeaders(headers: Map<String, String>?) {this.headers = headers}
    fun setChunkSize(size:Int) {this.chunkSize = size}
    fun setTimeout(timeout:Int) {this.timeout = timeout}
    fun setConnectTimeout(connectTimeout:Int) {this.connectTimeout = connectTimeout}
    fun setFileExtension(fileExtension: String) {this.fileExtension = fileExtension}
    
  
    private fun validateBuilder():Boolean{
      return true // this.url != null && this.filename != null && this.fileExtension != null
    }
  
    fun build(): LookDown {
      return LookDown(context, id=id, driver= driver, folder= folder, tryToResume= forceResume, headers= headers,
                      chunkSize= chunkSize, timeout= timeout, connectTimeout=connectTimeout, fileExtension=fileExtension)
    }
  }

  //endregion
  
  
  //region Properties
  fun setDriver(driver:Int) {this.driver = driver}
  fun setFolder(folder:String) {this.folder = folder}
  fun setChunkSize(size:Int) {this.chunkSize = size}
  fun setTimeout(timeout:Int) {this.timeout = timeout}
  fun setConnectTimeout(connectTimeout:Int) {this.connectTimeout = connectTimeout}
  //endregion
  
  
  //region File Management
  
  fun deleteFile(filename: String, fileExtension: String=this.fileExtension, driver: Int = this.driver , folder: String = this.folder): Boolean {
    return TempFileman.deleteFile(context, driver, folder, filename + fileExtension)
  }
  
  fun getFile(filename: String, fileExtension: String = this.fileExtension, driver: Int? = this.driver , folder: String? = this.folder): File? {
    return TempFileman.getFile(context, driver?:this.driver, folder ?:this.folder, filename + fileExtension)
  }
  
  //endregion
  
  //region Main Download
  private var _ldDownload: MutableLiveData<LDDownload> = MutableLiveData()
  var ldDownloadLiveData: LiveData<LDDownload> = _ldDownload
  
  suspend fun updateLDDownload(ldDownload: LDDownload) {
    withContext(Dispatchers.Main) {
      _ldDownload.value = ldDownload
    }
  }
  
  /**
   * Download any file
   *
   * Override DownloadWithFlow function with LDDownload as parameter
   */
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  suspend fun download(ldDownload: LDDownload){
    try {
      return  download(urlStr = ldDownload.url!!, filename =  ldDownload.filename!!, fileExtension =  ldDownload.fileExtension ?: this.fileExtension, id = ldDownload.id,
                       title = ldDownload.title, params = ldDownload.params, mLDDownload = ldDownload)
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
   * @param urlStr*
   * @param filename*
   * @param fileExtension* (e.g. ".mp4", ".png" ...)
   * @param title
   * @param params generic mutable map for any other needed property
   *
   */
  suspend fun download(urlStr: String,
                       filename: String,
                       fileExtension: String = this.fileExtension,
                       id: String? = null,
                       title: String? = null,
                       params: MutableMap<String, String>? = null,
                       mLDDownload: LDDownload?=null
  ) {
    var input: InputStream? = null
    var output: OutputStream? = null
    var fileTotalLength = 0L
    var tempFileExists = false
    
    val ldDownload = mLDDownload ?: LDDownload(id = id ?: UUID.randomUUID().toString(), url = urlStr, filename = filename, state = LDDownloadState.Queued, title = title, params = params)
    val file = this.getFile(filename, "$fileExtension${LDGlobals.LD_TEMP_EXT}")
    
    if (file == null) {
      ldDownload.state = LDDownloadState.Error("File can't be null")
      updateLDDownload(ldDownload)
      return
    }
    
    val url = URL(urlStr)
    val connection : HttpURLConnection? = url.openConnection() as HttpURLConnection
    
    if (connection == null) {
      ldDownload.state = LDDownloadState.Error("Failed to create a connection")
      updateLDDownload(ldDownload)
      return
    }
    
    flow {
      LDLogger.log("Starting download: $urlStr")
      emit(ldDownload)
      
      if (file.exists() && tryToResume.orDefault()) {
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
      if (tempFileExists) fileTotalLength += file.length()  //if is resumed the server will download only the difference, so we need to add to fileLength which are the bytes already downloaded
      
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
        if (fileTotalLength > 0) {
          ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
        }
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
        if (ldDownload.state == LDDownloadState.Downloaded) TempFileman.removeTempExtension(file, LDGlobals.LD_TEMP_EXT)
      } catch (ignored: IOException) {
      }
      connection.disconnect()
      emit(ldDownload)
    }.flowOn(Dispatchers.IO).collect {
      updateLDDownload(it)
    }
  }
  
  
  
  //endregion
  
  
  
  //region Simple Download
  
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
  fun downloadSimple(ldDownload: LDDownload): LDDownload {
    return try {
      downloadSimple(ldDownload.url!!, ldDownload.filename!!, ldDownload.fileExtension ?: this.fileExtension, ldDownload)
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
   * @param urlStr
   * @param filename
   * @param fileExtension (e.g. ".mp4", ".png" ...)
   *
   */
  fun downloadSimple(urlStr: String,
                     filename: String,
                     fileExtension: String=this.fileExtension,
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
    
    val file = TempFileman.getFile(context, driver ?: LD_DEFAULT_DRIVER, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension${LDGlobals.LD_TEMP_EXT}")
    if (file == null) {
      ldDownload.state = LDDownloadState.Error("File can't be null")
      return ldDownload
    }
    
    try {
      val url = URL(urlStr)
      connection  = url.openConnection() as HttpURLConnection
      
      LDLogger.log("Starting download: $urlStr")
      if (file.exists() && tryToResume.orDefault()) {
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
      if (tempFileExists) fileTotalLength += file.length()  //if is resumed, the server will download only the difference, so we need to add to fileLength the bytes already downloaded
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
          TempFileman.removeTempExtension(file, LDGlobals.LD_TEMP_EXT)
        }
        downloadJobs.remove(urlStr)
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
    }
    ldDownload.state?.let { LDLogger.log("Finished download with state: ${it::class.java.simpleName}") }
    return ldDownload
  }
  
  //endregion
}