package com.andrefilgs.lookdownapp.samples.sample02

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.xcool.coroexecutor.core.CoroUtil
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.LookDown
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import com.andrefilgs.lookdown_android.utils.orDefault
import com.andrefilgs.lookdown_android.wmservice.factory.LD_WORK_KEY_PROGRESS
import com.andrefilgs.lookdown_android.wmservice.factory.LD_WORK_KEY_PROGRESS_ID
import com.andrefilgs.lookdown_android.wmservice.utils.getLdId
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

//Transformations: https://proandroiddev.com/livedata-transformations-4f120ac046fc



@ExperimentalCoroutinesApi
@HiltViewModel
class DownloadViewModel @Inject constructor(
  private val lookDown: LookDown,
  executor: Executor,
  ): BaseViewModel(executor) {
  
  companion object{
    const val KEY_POSITION ="position"
    val driver = LDGlobals.LD_DEFAULT_DRIVER
    val folder = LDGlobals.LD_DEFAULT_FOLDER
  }
  
  private val dispatchers = StandardDispatchers()
  

  init {
    lookDown.pruneWork()
  }

 private val workDelay = 1000L
  
  //Executor schema
  private var _schema = MutableStateFlow<ExecutorSchema>(ExecutorSchema.Queue)
  var schema: StateFlow<ExecutorSchema> = _schema
  
  private var _list = MutableLiveData<Event<MutableList<LDDownload>>>()
  var list: LiveData<Event<MutableList<LDDownload>>> = _list
  

  //LookDown LiveData
  val ldDownload: LiveData<Event<LDDownload>> = Transformations.map(lookDown.ldDownloadLiveData) {
    Event(it)
  }
  
  val ldDownloadService: LiveData<Event<LDDownload>> = Transformations.map(lookDown.ldDownloadLiveDataService) {
    AppLogger.log("@@@ 4")
    Event(it)
  }
  
  
  //Turn around for Conflate
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
      AppLogger.log("@@@ Stopping service with id ${ldDownload.id} and workId: ${ldDownload.workId}")
      lookDown.cancelDownloadService(ldDownload.workId!!)
      serviceList.remove(ldDownload.id)
    }
    // serviceList.forEach { (k,v) ->
    //   if(serviceList[k]?.workId == download.id){
    //     AppLogger.log("@@@ Stopping service with id $k and workId: ${v.workId}")
    //     lookDown.cancelDownloadService(v.workId!!)
    //     serviceList.remove(k)
    //   }
    // }
  }
  
  
  fun stopAllServices(){
    serviceList.forEach { (k,v) ->
      lookDown.cancelDownloadService(v.workId!!)
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
        val workId = lookDown.downloadAsService(ldDownload)
        ldDownload.workId = workId
        // val uuid = UUID.fromString(ldDownload.id)
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
        AppLogger.log("Received workInfo with id ${workInfo.getLdId()}")
  
        val ldDownload = serviceList[workInfo.progress.getString(LD_WORK_KEY_PROGRESS_ID)] ?: return@observe
        AppLogger.log("Received workInfo with progress ${workInfo.progress.getInt(LD_WORK_KEY_PROGRESS, ldDownload.progress)}")
        
        if(workInfo.progress.getInt(LD_WORK_KEY_PROGRESS ,0) > 0){
          ldDownload.updateProgress(workInfo.progress.getInt(LD_WORK_KEY_PROGRESS, ldDownload.progress))
        }
        launch(schema.value){
          lookDown.updateLDDownload(ldDownload, forceUpdate = true)
        }
      }
    }
  }
  
  override fun onCleared() {
    lookDown.clearObservers() //ATTENTION //todo 10000
    super.onCleared()
  }
  
}