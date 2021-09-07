package com.andrefilgs.lookdown_android

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.andrefilgs.fileman.Fileman
import com.andrefilgs.lookdown_android.LDGlobals.LD_CHUNK_SIZE
import com.andrefilgs.lookdown_android.LDGlobals.LD_CONNECT_TIMEOUT
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_DRIVER
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_FOLDER
import com.andrefilgs.lookdown_android.LDGlobals.LD_LOG_TAG
import com.andrefilgs.lookdown_android.LDGlobals.LD_PROGRESS_RENDER_DELAY
import com.andrefilgs.lookdown_android.LDGlobals.LD_TIMEOUT
// import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.remote.LookDownRemote
import com.andrefilgs.lookdown_android.remote.LookDownRemoteImpl
import com.andrefilgs.lookdown_android.utils.DispatcherProvider
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.*


/**
 * @author André Filgueiras on 28/11/2020
 */

/**
 * Main Class
 * Avoid calling the functions on Main thread
 *
 * @param resume try to return the download from where it stopped (check server availability)
 * @param chunkSize bytes size that will be written when it reaches (e.g. 1024 = 1KB optimize this value)
 * @param progressRenderDelay To avoid UI Render Blinking inside RecyclerView we add some delay only for Downloading State (use 500ms or higher)
 */
@ExperimentalCoroutinesApi
class LookDown(private val context: Context,
               private val id: String = UUID.randomUUID().toString(),
               private var driver:Int=LD_DEFAULT_DRIVER,
               private var folder:String= LD_DEFAULT_FOLDER,
               private var resume: Boolean = true,
               private var headers: Map<String, String>? = null,
               private var chunkSize: Int= LD_CHUNK_SIZE,
               private var timeout: Int= LD_TIMEOUT,
               private var connectionTimeout: Int= LD_CONNECT_TIMEOUT,
               private var fileExtension:String= "",
               private var progressRenderDelay:Long= LD_PROGRESS_RENDER_DELAY,
               private var activateLogs:Boolean = false,
               private var logTag:String = LD_LOG_TAG
               ) {
  
  private val logger = LDLogger(showLogs = activateLogs, tag = logTag)
  private val dispatcher : DispatcherProvider = StandardDispatchers()
  private val remote : LookDownRemote = LookDownRemoteImpl(logger, dispatcher, timeout = timeout, connectionTimeout= connectionTimeout)
  
  
  fun setLogTag(tag:String) { logger.tag = tag }
  fun activateLogs() { logger.showLogs = true }
  fun deactivateLogs() { logger.showLogs = false }
  
  //region Companion
  companion object{
    fun setDefaultTimeout(timeout:Int){ LD_TIMEOUT = timeout }
    fun setDefaultConnectionTimeout(timeout:Int){ LD_CONNECT_TIMEOUT = timeout }
    fun setDefaultDriver(driver:Int){ LD_DEFAULT_DRIVER = driver }
    fun setDefaultFolder(folder:String){ LD_DEFAULT_FOLDER = folder }
    fun setDefaultChunkSize(chunkSize:Int){ LD_CHUNK_SIZE = chunkSize }
    fun setDefaultProgressRenderDelay(renderDelay:Long){ LD_PROGRESS_RENDER_DELAY = renderDelay }
    fun setDefaultLogTag(logTag:String){ LD_LOG_TAG = logTag }
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
    private var connectTimeout: Int= LD_CONNECT_TIMEOUT
    private var fileExtension: String= ""
    private var progressRenderDelay: Long= LD_PROGRESS_RENDER_DELAY
    private var activateLogs: Boolean= false
    private var logTag: String= LD_LOG_TAG
    
    fun setId(id:String) {this.id = id }
    fun setDriver(driver:Int) {this.driver = driver}
    fun setFolder(folder:String) {this.folder = folder}
    fun setForceResume(forceResume: Boolean) {this.forceResume = forceResume}
    fun setHeaders(headers: Map<String, String>?) {this.headers = headers}
    fun setChunkSize(size:Int) {this.chunkSize = size}
    fun setTimeout(timeout:Int) {this.timeout = timeout}
    fun setConnectTimeout(connectTimeout:Int) {this.connectTimeout = connectTimeout}
    fun setFileExtension(fileExtension: String) {this.fileExtension = fileExtension}
    fun setProgressRenderDelay(renderDelay: Long) {this.progressRenderDelay = renderDelay}
    fun activateLogs() {this.activateLogs = true}
    fun deactivateLogs() {this.activateLogs = false}
    fun setLogTag(logTag:String) {this.logTag = logTag}
    
    
    fun build(): LookDown {
      return LookDown(context, id=id, driver= driver, folder= folder, resume= forceResume, headers= headers,
                      chunkSize= chunkSize, timeout= timeout, connectionTimeout=connectTimeout,
        fileExtension=fileExtension, progressRenderDelay= progressRenderDelay, activateLogs = activateLogs, logTag= logTag)
    }
  }

  //endregion
  
  
  //region Properties
  fun setDriver(driver:Int) {this.driver = driver}
  fun setFolder(folder:String) {this.folder = folder}
  fun setFileExtension(extension:String) {this.fileExtension = extension}
  fun setChunkSize(size:Int) {this.chunkSize = size}
  fun setTimeout(timeout:Int) {this.timeout = timeout}
  fun setConnectTimeout(connectTimeout:Int) {this.connectionTimeout = connectTimeout}
  //endregion
  
  
  //region File Management
  
  fun deleteFile(filename: String, fileExtension: String=this.fileExtension, driver: Int = this.driver , folder: String = this.folder): Boolean {
    return Fileman.deleteFile(context, driver, folder, filename + fileExtension)
  }
  
  fun getFile(filename: String, fileExtension: String = this.fileExtension, driver: Int? = this.driver , folder: String? = this.folder): File? {
    return Fileman.getFile(context, driver?:this.driver, folder ?:this.folder, filename + fileExtension)
  }
  
  //endregion
  
  //region Main Download
  private var lastRender = 0L
  
  private var _ldDownload: MutableLiveData<LDDownload> = MutableLiveData()
  var ldDownloadLiveData: LiveData<LDDownload> = _ldDownload
  
  
  /**
   *
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
  suspend fun download(ldDownload: LDDownload){
    try {
      return  download(url = ldDownload.url!!, filename =  ldDownload.filename!!, fileExtension =  ldDownload.fileExtension ?: this.fileExtension, id = ldDownload.id,
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
   * @param url*
   * @param filename*
   * @param fileExtension* (e.g. ".mp4", ".png" ...)
   * @param title
   * @param params generic mutable map for any other needed property
   *
   */
  suspend fun download(url: String,
                       filename: String,
                       fileExtension: String = this.fileExtension,
                       id: String? = null,
                       title: String? = null,
                       resume: Boolean = this.resume,
                       params: MutableMap<String, String>? = null,
                       mLDDownload: LDDownload?=null
  ) {
    withContext(dispatcher.io) { //to avoid Inappropriate blocking method call
      kotlin.runCatching {
        val ldDownload = mLDDownload ?: LDDownload(id = id ?: UUID.randomUUID().toString(), url = url, filename = filename, title = title, params = params)
        
        flow {
          logger.log("Checking download (file and connection): $url")
          ldDownload.state = LDDownloadState.Queued
          emit(ldDownload)
  
          val file = getFile(
            filename,
            "$fileExtension${LDGlobals.LD_TEMP_EXT}"
          )
          if (file == null) {
            ldDownload.state = LDDownloadState.Error("File can't be null")
            updateLDDownload(ldDownload)
            return@flow
          }
          ldDownload.file = file
  
          val setupRes = remote.setup(
            url = url,
            resume = resume,
            file = file,
            headers = headers
          )
          if (!setupRes.first) {
            ldDownload.state = LDDownloadState.Error(setupRes.second)
          }
  
          remote.download(
            ldDownload= ldDownload,
            file= file,
            chunkSize= chunkSize,
            resume= resume
          ).collect {
            emit(it)
          }
        }.flowOn(dispatcher.io).collect {
          updateLDDownload(it, forceUpdate = ldDownload.state != LDDownloadState.Downloading)  //if it's downloading don't force update
        }
        
      }
    }
  }
  
  
  
  //endregion
}