package com.andrefilgs.lookdown_android.wmservice.factory

import androidx.work.*
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.wmservice.utils.*
import com.andrefilgs.lookdown_android.wmservice.workers.LDDownloadWorker
import java.util.concurrent.TimeUnit


class LDWorkRequestFactory {
  
  companion object{
    const val WORK_TAG_DOWNLOAD = "WORK_TAG_DOWNLOAD"
    const val WORK_TAG_DOWNLOAD_PERIODICALLY = "WORK_TAG_DOWNLOAD_PERIODICALLY"
    
    
    private fun getDownloadConstraints(): Constraints {
      return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // .setRequiresStorageNotLow(true)
        .setRequiresBatteryNotLow(true)
        .build()
    }
  
    fun buildDownloadWorkerPeriodically(tag:String? = WORK_TAG_DOWNLOAD_PERIODICALLY): PeriodicWorkRequest {
      val inputData = Data.Builder().apply {
        putLDInitTime(System.currentTimeMillis())
      }
    
      return PeriodicWorkRequest.Builder(LDDownloadWorker::class.java, 16, TimeUnit.MINUTES)
        .setConstraints(getDownloadConstraints())
        .addTag(tag ?: WORK_TAG_DOWNLOAD)
        .setInputData(inputData.build())
        .build()
    }

  
    fun buildDownloadWorkerOneTime(ldDownload: LDDownload, notificationId:Int=0, resume:Boolean, tag:String = WORK_TAG_DOWNLOAD): OneTimeWorkRequest {
      val inputData = Data.Builder().apply {
        putLDInitTime(System.currentTimeMillis())
        putLDUrl(ldDownload.url!!)
        putLDFilename(ldDownload.filename!!)
        putLDTitle(ldDownload.title)
        putLDId(ldDownload.id)
        putLDNotificationId(notificationId)
        putLDResume(resume)
      }
      
      return OneTimeWorkRequest.Builder(LDDownloadWorker::class.java)
        .setConstraints(getDownloadConstraints())
        .addTag(tag)
        .setInputData(inputData.build())
        .build()
    }
  }
  
}