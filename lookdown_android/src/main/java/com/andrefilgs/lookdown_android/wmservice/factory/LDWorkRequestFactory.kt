package com.andrefilgs.lookdown_android.wmservice.factory

import androidx.work.*
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.wmservice.utils.putFilename
import com.andrefilgs.lookdown_android.wmservice.utils.putLDNotificationId
import com.andrefilgs.lookdown_android.wmservice.utils.putLdId
import com.andrefilgs.lookdown_android.wmservice.utils.putUrl
import com.andrefilgs.lookdown_android.wmservice.workers.LDDownloadWorker
import java.util.concurrent.TimeUnit

const val LD_KEY_PROGRESS = "LD_KEY_PROGRESS"
const val LD_KEY_DOWNLOAD_ID = "LD_KEY_DOWNLOAD_ID"

class LDWorkRequestFactory {
  
  companion object{
    const val WORK_TAG_FINAL = "WORK_TAG_FINAL"
    const val WORK_TAG_DOWNLOAD = "WORK_TAG_DOWNLOAD"
    const val WORK_TAG_DOWNLOAD_PERIODICALLY = "WORK_TAG_DOWNLOAD_PERIODICALLY"
    const val WORK_KEY_OUTPUT = "WORK_KEY_OUTPUT"
    const val WORK_KEY_FINAL = "WORK_KEY_FINAL"
    const val WORK_KEY_INIT_TIME = "WORK_KEY_INIT_TIME"
    
    
    private fun getDownloadConstraints(): Constraints {
      return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // .setRequiresStorageNotLow(true)
        .setRequiresBatteryNotLow(true)
        .build()
    }
  
    fun buildDownloadWorkerPeriodically(tag:String? = WORK_TAG_DOWNLOAD_PERIODICALLY): PeriodicWorkRequest {
      val inputData = Data.Builder()
      inputData.putLong(WORK_KEY_INIT_TIME, System.currentTimeMillis())
    
      return PeriodicWorkRequest.Builder(LDDownloadWorker::class.java, 16, TimeUnit.MINUTES)
        .setConstraints(getDownloadConstraints())
        .addTag(tag ?: WORK_TAG_DOWNLOAD)
        .setInputData(inputData.build())
        .build()
    }

  
    fun buildDownloadWorkerOneTime(ldDownload: LDDownload, notificationId:Int=0, tag:String = WORK_TAG_DOWNLOAD): OneTimeWorkRequest {
      val inputData = Data.Builder().apply {
        putLong(WORK_KEY_INIT_TIME, System.currentTimeMillis())
        putUrl(ldDownload.url!!)
        putFilename(ldDownload.filename!!)
        putLdId(ldDownload.id)
        putLDNotificationId(notificationId)
      }
      
      return OneTimeWorkRequest.Builder(LDDownloadWorker::class.java)
        .setConstraints(getDownloadConstraints())
        .addTag(tag)
        .setInputData(inputData.build())
        .build()
    }
  }
  
}