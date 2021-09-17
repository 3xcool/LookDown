package com.andrefilgs.lookdownapp.utils

import androidx.lifecycle.ViewModel
import com.xcool.coroexecutor.core.Executor
import com.xcool.coroexecutor.core.ExecutorSchema
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext



abstract class BaseViewModel (
  private val executor: Executor
) :ViewModel(){
  
  private val baseParentJob: Job = SupervisorJob()
  
  private val coroutineExceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    println("Caught ${throwable.printStackTrace()}")
  }
  
  val baseCoroutineScope = CoroutineScope(Dispatchers.Main + baseParentJob + coroutineExceptionHandler)
  
  fun cancelAllTasks() {
    baseParentJob.cancelChildren()  // must be supervisorJob because when you cancel the children you don't kill this job
  }
  
  fun <T> launch(
    thread: CoroutineContext,
    schema: ExecutorSchema,
    block: suspend CoroutineScope.() -> T
  ) {
    baseCoroutineScope.launch(context = thread) {
      executor.execute(schema) { block() }
    }
  }
  
  fun <T> launch(
    schema: ExecutorSchema,
    block: suspend CoroutineScope.() -> T
  ): Job {
    return baseCoroutineScope.launch {
      executor.execute(schema) { block() }
    }
  }
  
}