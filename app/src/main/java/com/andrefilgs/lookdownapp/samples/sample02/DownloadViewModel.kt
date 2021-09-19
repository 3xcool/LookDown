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
  
  private val serviceList : MutableMap<String, LDDownload> = mutableMapOf()  //key will be LDDownload ID
  private val dispatchers = StandardDispatchers()
  
  companion object{
    const val KEY_POSITION = "position"
    val driver = LDGlobals.LD_DEFAULT_DRIVER
    val folder = LDGlobals.LD_DEFAULT_FOLDER
  }
  

  

  init {
    // lookDown.pruneWork()
  }
  
  
  //Executor schema
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  private var _list = MutableLiveData<Event<MutableList<LDDownload>>>()
  var list: LiveData<Event<MutableList<LDDownload>>> = _list
  
  var currentDownloadList :MutableList<LDDownload> =  buildFakeLDDownloadList()

  //LookDown LiveData
  val ldDownload: LiveData<Event<LDDownload>> = Transformations.map(lookDown.ldDownloadLiveData) {
    Event(it)
  }
  
  
  //Workaround for Conflate
  private var workingJobList: MutableMap<String, Triple<ExecutorSchema, Job, LDDownload>> = mutableMapOf()
  
  
  fun buildList(context:Context){
    baseCoroutineScope.launch(dispatchers.io) {
      val list = LDDownloadUtils.checkFileExists(context, driver, folder, currentDownloadList)
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
  
  
  /**
   * To get current running Works
   */
  fun getCurrentWorks(lifecycleOwner: LifecycleOwner){
    baseCoroutineScope.launch(dispatchers.io) {
      lookDown.getCurrentWorks()?.forEach {workInfo ->
        AppLogger.log("$workInfo")
        updateServiceList(workInfo)
        observeWorkService(lifecycleOwner, workId = workInfo.id)
      }
    }
  }
  
  
  private fun updateServiceList(workInfo: WorkInfo) {
    val data = when(workInfo.state) {
      WorkInfo.State.RUNNING -> workInfo.progress
      else -> workInfo.outputData
    }
    data.getLDId()?.let { ldId ->
      if(serviceList[ldId] == null){
        AppLogger.log("Update Service List with LookDown ID ${data.getLDId()}")
        val ldDownload = currentDownloadList[ldId.toInt()]
        ldDownload.workId = workInfo.id  //don't forget to get WorkManager ID
        serviceList[ldId] = currentDownloadList[ldId.toInt()]
      }
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
          downloadFile(lifecycleOwner, download, withService, notificationId = position+1)
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
  
  @InternalCoroutinesApi
  private suspend fun downloadFile(lifecycleOwner: LifecycleOwner, ldDownload: LDDownload, withService:Boolean, notificationId:Int){
    if(ldDownload.validateInfoForDownloading()){
      if(withService) {
        val workId = lookDown.downloadAsService(ldDownload, notificationId = notificationId, notificationImportance = 4) // 4 = NotificationManager.IMPORTANCE_HIGH and pass notificationId null to let LookDown counter handle it
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
    AppLogger.log("Observe Work ID $workId")
    withContext(dispatchers.main){
      lookDown.getWorkInfoByLiveData(workId).observe(lifecycleOwner){ workInfo ->
        AppLogger.log("Received workInfo $workInfo")
        val data = when(workInfo.state) {
          WorkInfo.State.RUNNING -> workInfo.progress
          else -> workInfo.outputData
        }
        AppLogger.log("Received workInfo with id ${data.getLDId()}")
        
        val currentProgress = data.getLDProgress()
        AppLogger.log("Received workInfo with progress $currentProgress")
  
        val ldDownload = serviceList[data.getLDId()] ?: return@observe
        
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
  
  
}