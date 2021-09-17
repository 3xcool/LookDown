package com.andrefilgs.lookdownapp.samples.sample02

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.work.WorkInfo
import com.xcool.coroexecutor.core.CoroUtil
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import com.andrefilgs.lookdown.LDGlobals
import com.andrefilgs.lookdown.LookDown
import com.andrefilgs.lookdown.domain.LDDownload
import com.andrefilgs.lookdown.domain.LDDownloadState
import com.andrefilgs.lookdown.utils.StandardDispatchers
import com.andrefilgs.lookdown.utils.orDefault
import com.andrefilgs.lookdown.wmservice.utils.getLDOutputMsg
import com.andrefilgs.lookdown.wmservice.utils.getLDId
import com.andrefilgs.lookdown.wmservice.utils.getLDProgress
import com.andrefilgs.lookdownapp.app.AppLogger
import com.andrefilgs.lookdownapp.samples.sample02.model.LDDownloadUtils
import com.andrefilgs.lookdownapp.utils.BaseViewModel
import com.andrefilgs.lookdownapp.utils.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject



@ExperimentalCoroutinesApi
@HiltViewModel
class DownloadViewModel @Inject constructor(
  private val lookDown: LookDown,
  executor: Executor,
  ): BaseViewModel(executor) {
  
  companion object{
    const val KEY_POSITION = "position"
    val driver = LDGlobals.LD_DEFAULT_DRIVER
    val folder = LDGlobals.LD_DEFAULT_FOLDER
  }
  
  private val dispatchers = StandardDispatchers()
  

  init {
    lookDown.pruneWork()
  }

  
  //Executor schema
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  private var _list = MutableLiveData<Event<MutableList<LDDownload>>>()
  var list: LiveData<Event<MutableList<LDDownload>>> = _list
  

  //LookDown LiveData
  val ldDownload: LiveData<Event<LDDownload>> = Transformations.map(lookDown.ldDownloadLiveData) {
    Event(it)
  }
  
  
  //Workaround for Conflate
  private var workingJobList: MutableMap<String, Triple<ExecutorSchema, Job, LDDownload>> = mutableMapOf()
  
  
  fun buildList(context:Context){
    baseCoroutineScope.launch(dispatchers.io) {
      val list = LDDownloadUtils.checkFileExists(context, driver, folder, buildFakeLDDownloadList())
      withContext(dispatchers.main){
        _list.value = Event(list)
      }
    }
  }
  
  private fun removeJob(download: LDDownload) {
    workingJobList.remove(download.id)
  }
  
  fun setExecutorSchema(schema: ExecutorSchema) {
    this._schema.value = schema
    launch(CoroUtil.MainThread, this.schema.value) {
      _schema.value = schema
    }
  }
  
  fun stopDownload(download: LDDownload, position: Int, withService:Boolean? = false) {
    launch(schema.value) {
      
      if(withService.orDefault()){
        stopServiceById(download)
      }
      
      workingJobList[download.id]?.let {
        it.second.cancelAndJoin()
      }
      download.setStateAfterDownloadStops()
      download.params?.set(KEY_POSITION, position.toString())
      lookDown.updateLDDownload(download)
    }
  }
  
  
  private fun stopServiceById(download: LDDownload){
    serviceList[download.id]?.let{ ldDownload ->
      AppLogger.log("Stopping service with id ${ldDownload.id} and workId: ${ldDownload.workId}")
      lookDown.cancelDownloadService(ldDownload.workId!!)
      serviceList.remove(ldDownload.id)
    }
  }
  
  
  private fun stopAllServices(){
    serviceList.forEach { (k,v) ->
      stopServiceById(v)
    }
  }
  
  fun stopAllDownload() {
    launch(ExecutorSchema.Concurrent) {
      stopAllServices()
      workingJobList.forEach { map ->
        map.value.third.setStateAfterDownloadStops()
        lookDown.updateLDDownload(map.value.third)
        map.value.second.cancel()
      }
    }
  }
  
  fun deleteDownload(download: LDDownload, position: Int) {
    launch(ExecutorSchema.Concurrent) {
      withContext(dispatchers.io){
        val res = lookDown.deleteFile(download.filename!!, download.fileExtension!!, driver=driver, folder=folder)
        if(!res){
          download.state = LDDownloadState.Error("File not deleted")
        }else{
          download.updateProgress(0)
          download.params?.set(KEY_POSITION, position.toString())
        }
        lookDown.updateLDDownload(download)
      }
    }
  }
  
  fun refreshState(download: LDDownload){
    launch(ExecutorSchema.Concurrent) {
      download.updateProgress(download.progress)
      lookDown.updateLDDownload(download, forceUpdate = true)
    }
  }


  
  @InternalCoroutinesApi
  fun startDownload(lifecycleOwner: LifecycleOwner, download: LDDownload, position:Int, withService: Boolean){
    baseCoroutineScope.launch {
      //force QUEUE state to avoid collision and Update UI.
      download.state = LDDownloadState.Queued
      download.params = mutableMapOf(Pair(KEY_POSITION, position.toString()))
      lookDown.updateLDDownload(download)
  
      val job = launch(schema.value) {
        withContext(dispatchers.io) {
          AppLogger.log("Start download file ${download.title} with progress ${download.progress}")
          downloadFile(lifecycleOwner, download, withService)
          AppLogger.log("Finished download file ${download.title} with progress ${download.progress}")
        }
      }
      handleConflate(schema.value)
      workingJobList[download.id] = Triple(schema.value, job, download)
      
      job.join()
      removeJob(download) //remove job after finish
    }
  }
  

  
  private suspend fun handleConflate(schema: ExecutorSchema) {
    //Conflate will kill any working progress, so avoid use it for download
    //Manually stopping other downloads
    if(schema == ExecutorSchema.Conflated){
      workingJobList.filter { map -> map.value.first == ExecutorSchema.Conflated }
        .forEach { map->
          if(map.value.third.state == LDDownloadState.Downloading){
            map.value.second.cancel()
            map.value.third.state = LDDownloadState.Paused
            lookDown.updateLDDownload(map.value.third)
          }
        }
    }
  }
  
  private val serviceList : MutableMap<String, LDDownload> = mutableMapOf()  //key will be LDDownload ID
  
  @InternalCoroutinesApi
  private suspend fun downloadFile(lifecycleOwner: LifecycleOwner, ldDownload: LDDownload, withService:Boolean){
    if(ldDownload.validateInfoForDownloading()){
      if(withService) {
        val workId = lookDown.downloadAsService(ldDownload,notificationId = null, notificationImportance = 4) // 4 = NotificationManager.IMPORTANCE_HIGH and pass notificationId null to let LookDown counter handle it
        ldDownload.workId = workId
        serviceList[ldDownload.id] = ldDownload
        observeWorkService(lifecycleOwner, workId)
      } else {
        lookDown.download(ldDownload)
      }
    }else{
      ldDownload.state = LDDownloadState.Error("Missing download info")
      lookDown.updateLDDownload(ldDownload)
    }
  }
  
  
  private suspend fun observeWorkService(lifecycleOwner: LifecycleOwner, workId:UUID){
    withContext(dispatchers.main){
      lookDown.getWorkInfoByLiveData(workId).observe(lifecycleOwner){ workInfo ->
        AppLogger.log("Received workInfo $workInfo")
        val data = when(workInfo.state) {
          WorkInfo.State.RUNNING -> workInfo.progress
          else -> workInfo.outputData
        }
        AppLogger.log("Received workInfo with id ${data.getLDId()}")
  
        val ldDownload = serviceList[data.getLDId()] ?: return@observe
        
        val currentProgress = data.getLDProgress()
        AppLogger.log("Received workInfo with progress $currentProgress")
        
        if(currentProgress> 0){
          ldDownload.updateProgress(currentProgress)
        }
        launch(schema.value){
          lookDown.updateLDDownload(ldDownload, forceUpdate = true)
        }
        // data.getLDElapsedTime()
        data.getLDOutputMsg()?.let{
          AppLogger.log(it)
        }
      }
    }
  }
  
  override fun onCleared() {
    super.onCleared()
  }
  
}