package com.andrefilgs.lookdown_android

import android.content.Context
import androidx.lifecycle.*
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andrefilgs.fileman.Fileman
import com.andrefilgs.lookdown_android.LDGlobals.LD_CHUNK_SIZE
import com.andrefilgs.lookdown_android.LDGlobals.LD_CONNECT_TIMEOUT
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_DRIVER
import com.andrefilgs.lookdown_android.LDGlobals.LD_DEFAULT_FOLDER
import com.andrefilgs.lookdown_android.LDGlobals.LD_LOG_TAG
import com.andrefilgs.lookdown_android.LDGlobals.LD_PROGRESS_RENDER_DELAY
import com.andrefilgs.lookdown_android.LDGlobals.LD_TIMEOUT
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.remote.LookDownRemote
import com.andrefilgs.lookdown_android.remote.LookDownRemoteImpl
import com.andrefilgs.lookdown_android.utils.DispatcherProvider
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import com.andrefilgs.lookdown_android.wmservice.LDWorkManagerController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.*
import com.google.common.util.concurrent.ListenableFuture


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
class LookDown (
  private val context: Context,
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
  private var logTag:String = LD_LOG_TAG,
) {
  
  private val logger = LDLogger(showLogs = activateLogs, tag = logTag)
  private val dispatcher : DispatcherProvider = StandardDispatchers()
  private val remote : LookDownRemote = LookDownRemoteImpl(logger, dispatcher, timeout = timeout, connectionTimeout= connectionTimeout)
  
  private val workManager = WorkManager.getInstance(context)
  
  private val ldWorkManagerController: LDWorkManagerController = LDWorkManagerController(workManager, this.logger)
  

  
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
  
  //passing context as parameter may avoid delete failure
  fun deleteFile(filename: String, fileExtension: String=this.fileExtension, driver: Int = this.driver , folder: String = this.folder, context: Context?=null): Boolean {
    return Fileman.deleteFile(context ?: this.context, driver, folder, filename + fileExtension)
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
      withContext(dispatcher.main) { _ldDownload.value = ldDownload}
    }else{
      val now = System.currentTimeMillis()
      val delta = now - lastRender
      if(delta > progressRenderDelay){
        lastRender = now
        withContext(dispatcher.main) { _ldDownload.value = ldDownload}
      }
    }
  }
  
  
  
  
  @InternalCoroutinesApi
  /**
   * @param asService to download using WorkManager and Foreground Service
   * @param resume to start download from where it stopped
   */
  suspend fun download(ldDownload: LDDownload, asService:Boolean=false, resume: Boolean=this.resume):UUID?{
    try {
      if(asService) return downloadAsService(ldDownload, resume)
      
      withContext(dispatcher.io) { //to avoid Inappropriate blocking method call
        kotlin.runCatching {
          downloadWithFlow(ldDownload, resume = resume)
        }
      }
    }catch (e:Exception){
      ldDownload.state = LDDownloadState.Error(e.message ?: e.localizedMessage)
      updateLDDownload(ldDownload)
    }
    return null
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
   * @param asService to download using WorkManager and Foreground Service
   * @param resume to start download from where it stopped
   *
   */
  suspend fun download(url: String,
    filename: String,
    fileExtension: String = this.fileExtension,
    id: String? = null,
    title: String? = null,
    resume: Boolean = this.resume,
    params: MutableMap<String, String>? = null,
    lDDownload: LDDownload?=null,
    asService:Boolean=false
  ): UUID?{
    val mLdDownload = lDDownload ?: LDDownload(id = id ?: UUID.randomUUID().toString(), url = url, filename = filename, title = title, params = params, fileExtension = fileExtension)
    try {
      if(asService) return downloadAsService(mLdDownload, resume)
    
      withContext(dispatcher.io) { //to avoid Inappropriate blocking method call
        kotlin.runCatching {
          downloadWithFlow(mLdDownload, resume= resume)
        }
      }
    }catch (e:Exception){
      mLdDownload.state = LDDownloadState.Error(e.message ?: e.localizedMessage)
      updateLDDownload(mLdDownload)
    }
    return null
  }
  
  //endregion
 

  //region Service
  
  suspend fun downloadAsService(ldDownload: LDDownload, resume:Boolean=this.resume): UUID {
    return ldWorkManagerController.startDownload(ldDownload, resume)
  }
  
  fun getWorkInfoByLiveData(id:UUID):LiveData<WorkInfo>{
    return ldWorkManagerController.getWorkManager().getWorkInfoByIdLiveData(id)
  }
  
  fun cancelDownloadService(id: UUID): ListenableFuture<Operation.State.SUCCESS> {
    return ldWorkManagerController.cancelWorkById(id)
  }
  
  fun pruneWork(){
    ldWorkManagerController.getWorkManager().pruneWork()
  }
  
  /**
   * Download any file
   *
   * Override DownloadWithFlow function with LDDownload as parameter
   */
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  suspend fun downloadWithFlow(ldDownload: LDDownload, resume:Boolean=this.resume): Flow<LDDownload>? {
    try {
      return  downloadWithFlow(url = ldDownload.url!!, filename =  ldDownload.filename!!, fileExtension =  ldDownload.fileExtension ?: this.fileExtension, id = ldDownload.id,
        title = ldDownload.title, params = ldDownload.params, mLDDownload = ldDownload, resume = resume)
    }catch (e:Exception){
      ldDownload.state = LDDownloadState.Error(e.message ?: e.localizedMessage)
      updateLDDownload(ldDownload)
    }
    return null
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
  suspend fun downloadWithFlow(url: String,
    filename: String,
    fileExtension: String = this.fileExtension,
    id: String? = null,
    title: String? = null,
    resume: Boolean = this.resume,
    params: MutableMap<String, String>? = null,
    mLDDownload: LDDownload?=null
  ) :Flow<LDDownload>{
    // withContext(dispatcher.io) { //to avoid Inappropriate blocking method call
    //   kotlin.runCatching {
        val ldDownload = mLDDownload ?: LDDownload(id = id ?: UUID.randomUUID().toString(), url = url, filename = filename, title = title, params = params)
        
        return flow {
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
          
          val connection = remote.setup(
            url = url,
            resume = resume,
            file = file,
            headers = headers
          )
          if (connection == null) {
            ldDownload.state = LDDownloadState.Error("Couldn't establish connection")
            updateLDDownload(ldDownload)
            return@flow
          }
          
          remote.download(
            connection= connection,
            ldDownload= ldDownload,
            file= file,
            chunkSize= chunkSize,
            resume= resume
          ).collect {
            emit(it)
          }
        }.catch { e->
          logger.log("Catch ${e.printStackTrace()}")
          ldDownload.state = LDDownloadState.Error("Catch: ${e.printStackTrace()}")
          emit(ldDownload)
          return@catch
        }.flowOn(dispatcher.io)
      // }
    // }
  }
  
  
  //endregion
}