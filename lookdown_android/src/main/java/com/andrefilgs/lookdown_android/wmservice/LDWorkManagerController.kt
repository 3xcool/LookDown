package com.andrefilgs.lookdown_android.wmservice

import androidx.work.WorkManager
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.wmservice.factory.LDWorkRequestFactory
import java.util.*

internal class LDWorkManagerController ( private val workManager: WorkManager, private val ldLogger:LDLogger) {
  
  
  fun startDownload(ldDownload: LDDownload): UUID {
    val workRequest = LDWorkRequestFactory.buildDownloadWorkerOneTime(ldDownload = ldDownload)
    workManager.let{
      ldLogger.log("Starting worker")
      it.beginWith(workRequest).enqueue()
    }
    return workRequest.id
  }
  
  fun cancelWork(tag:String?=null){
    workManager.cancelAllWorkByTag(tag ?: LDWorkRequestFactory.WORK_TAG_DOWNLOAD)
  }
}