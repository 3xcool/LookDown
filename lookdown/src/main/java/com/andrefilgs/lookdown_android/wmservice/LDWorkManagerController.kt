package com.andrefilgs.lookdown_android.wmservice

import android.app.NotificationManager
import androidx.work.Operation
import androidx.work.WorkManager
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.wmservice.factory.LDWorkRequestFactory
import com.google.common.util.concurrent.ListenableFuture
import java.util.*

internal class LDWorkManagerController(private val workManager: WorkManager) {
  
  private var notificationCounter = 0
  
  fun startDownload(ldDownload: LDDownload, resume: Boolean, notificationId: Int?, notificationImportance: Int?, allowCancel: Boolean?): UUID {
    val workRequest = LDWorkRequestFactory.buildDownloadWorkerOneTime(
      ldDownload = ldDownload, resume = resume, notificationId = notificationId ?: notificationCounter, notificationImportance = notificationImportance ?: NotificationManager.IMPORTANCE_MIN, allowCancel = allowCancel ?: true
    )
    workManager.let {
      it.beginWith(workRequest).enqueue()
    }
    if (notificationId == null) notificationCounter++
    return workRequest.id
  }
  
  fun getWorkManager(): WorkManager {
    return workManager
  }
  
  fun cancelAllWorks() {
    workManager.cancelAllWorkByTag(LDWorkRequestFactory.WORK_TAG_DOWNLOAD)
  }
  
  fun cancelWorkByTag(tag: String? = null) {
    workManager.cancelAllWorkByTag(tag ?: LDWorkRequestFactory.WORK_TAG_DOWNLOAD)
  }
  
  fun cancelWorkById(id: UUID): ListenableFuture<Operation.State.SUCCESS> {
    return workManager.cancelWorkById(id).result
  }
}