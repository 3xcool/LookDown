package com.andrefilgs.lookdown_android.wmservice.factory

import androidx.work.*
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.wmservice.utils.putFilename
import com.andrefilgs.lookdown_android.wmservice.utils.putUrl
import com.andrefilgs.lookdown_android.wmservice.workers.LDDownloadWorker
import java.util.concurrent.TimeUnit

const val LD_WORK_KEY_PROGRESS = "LD_WORK_KEY_PROGRESS"
const val LD_WORK_KEY_STATE = "LD_WORK_KEY_STATE"

class LDWorkRequestFactory {
  
  companion object{
    const val WORK_TAG_FINAL = "WORK_TAG_FINAL"
    const val WORK_TAG_DOWNLOAD = "WORK_TAG_DOWNLOAD"
    const val WORK_TAG_DOWNLOAD_PERIODICALLY = "WORK_TAG_DOWNLOAD_PERIODICALLY"
    const val WORK_KEY_INITTIME = "WORK_KEY_INITTIME"
    const val WORK_KEY_OUTPUT = "WORK_KEY_OUTPUT"

    const val WORK_KEY_FINAL = "WORK_KEY_FINAL"
    
    
    private fun getDownloadConstraints(): Constraints {
      return Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // .setRequiresStorageNotLow(true)
        .setRequiresBatteryNotLow(true)
        .build()
    }
  
    fun buildDownloadWorkerPeriodically(tag:String? = WORK_TAG_DOWNLOAD_PERIODICALLY): PeriodicWorkRequest {
      val inputData = Data.Builder()
      inputData.putLong(WORK_KEY_INITTIME, System.currentTimeMillis())
    
      return PeriodicWorkRequest.Builder(LDDownloadWorker::class.java, 15, TimeUnit.MINUTES)
        .setConstraints(getDownloadConstraints())
        .addTag(tag ?: WORK_TAG_DOWNLOAD)
        .setInputData(inputData.build())
        .build()
    }

  
    fun buildDownloadWorkerOneTime(ldDownload: LDDownload, tag:String? = WORK_TAG_DOWNLOAD): OneTimeWorkRequest {
      val inputData = Data.Builder()
      inputData.putLong(WORK_KEY_INITTIME, System.currentTimeMillis())
      inputData.putUrl(ldDownload.url!!)
      inputData.putFilename(ldDownload.filename!!)
      
      return OneTimeWorkRequest.Builder(LDDownloadWorker::class.java)
        .setConstraints(getDownloadConstraints())
        .addTag(tag!!)
        .setInputData(inputData.build())
        .build()
    }
  
    // fun buildFinalWorker(): OneTimeWorkRequest {
    //   val inputData = Data.Builder()
    //   inputData.putLong(WORK_KEY_INITTIME, System.currentTimeMillis())
    //
    //   inputData.putString(WORK_KEY_FINAL, "WORK_KEY_FINAL")
    //   return OneTimeWorkRequest.Builder(FinalWorker::class.java)
    //     .addTag(WORK_TAG_FINAL)
    //     .setInputData(inputData.build())
    //     .build()
    // }
   
    
  }
  
}