package com.xcool.lookdownapp.utils

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*


abstract class BaseViewModel (
) :ViewModel(){
  
  private val baseParentJob: Job = SupervisorJob()
  
  private val coroutineExceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    GlobalScope.launch { println("Caught ${throwable.printStackTrace()}") }
  }
  
  val baseCoroutineScope = CoroutineScope(Dispatchers.Main + baseParentJob + coroutineExceptionHandler)
  
  fun cancelAllTasks() {
    baseParentJob.cancelChildren()  // must be supervisorJob because when you cancel the children you don't kill this job
  }
  
 

}