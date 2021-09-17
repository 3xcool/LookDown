package com.andrefilgs.lookdown_android.wmservice.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.andrefilgs.fileman.auxiliar.putEllapsedTime
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.LookDown
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import com.andrefilgs.lookdown_android.wmservice.utils.*
import kotlinx.coroutines.*
import com.xcool.lookdown.R
import kotlinx.coroutines.flow.collect

const val CHANNEL_ID_LD_DOWNLOAD = "CHANNEL_ID_LD_DOWNLOAD"


internal class LDDownloadWorker (
  private val context: Context,
  parameters: WorkerParameters,
) :  CoroutineWorker(context, parameters) {
  
  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val dispatchers = StandardDispatchers()
  
  @ExperimentalCoroutinesApi
  private val lookDown = LookDown.Builder(context).build()
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  override suspend fun doWork(): Result = coroutineScope{
    val initTime = getLDInitTime()
    val url = getLDUrl()
    val title = getLDTitle()
    val filename = getLDFilename()
    val fileExtension = getLDFileExtension()
    val progress = getLDProgress()
    val ldId = getLDId()
    val notificationId = getLDNotificationId()
    val notificationImportance = getLDNotificationImportance()
    val allowCancel = getLDAllowWorkCancel()
    val resume = getLDResume()
    val ldDownload = LDDownload(id= ldId!!, url=url, title= title, filename = filename,fileExtension=fileExtension, progress = progress, state = LDDownloadState.Queued)
    withContext(dispatchers.io){
      
      // Create a Notification channel if necessary
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createChannel(CHANNEL_ID_LD_DOWNLOAD, notificationImportance)
      }
      
      val msg = "Starting Download $url"
      setForeground(createForegroundInfo(notificationId, msg, progress,title=title, indeterminate = true, allowCancel =allowCancel ))
      val job = async {
        download(notificationId, ldDownload, resume = resume, allowCancel=allowCancel)
      }
  
      val jobRes = job.await()
      val deltaTime = System.currentTimeMillis() - initTime
      val outputData = Data.Builder().apply {
        putLDId(ldDownload.id)
        putLdProgress(ldDownload.progress)
        putLDOutputMsg("Download ${ldDownload.title} with success? $job after $deltaTime milliseconds")
        putEllapsedTime(deltaTime)
      }.build()
      
      if(jobRes) Result.success(outputData)
      else Result.failure(outputData)
    }
  }
  
  

  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  private suspend fun download(notificationId: Int, ldDownload: LDDownload, resume:Boolean, allowCancel: Boolean): Boolean {
    val res = lookDown.downloadWithFlow(ldDownload, resume = resume) ?: return false
    res.collect { ldDownloadProgress->
      val progress = ldDownloadProgress.progress
      setForeground(createForegroundInfo(notificationId,"Progress $progress%", progress, title = ldDownload.title, allowCancel = allowCancel))
      
      val output = Data.Builder().apply {
        putLDId(ldDownload.id)
        putLdProgress(progress)
        
      }.build()
      setProgressAsync(output)
    }
    return true
  }
  
  
  private fun createForegroundInfo(notificationId:Int, msg:String, progress: Int, title:String?=null, indeterminate:Boolean?=false, allowCancel:Boolean): ForegroundInfo {
    val id = CHANNEL_ID_LD_DOWNLOAD
    val mTitle = title ?: "LookDown"
    val cancel = context.getString(R.string.cancel)
    
    val notificationBuilder = NotificationCompat.Builder(context, id)
      .setContentTitle(mTitle)
      .setTicker(mTitle)
      .setContentText(msg)
      .setSmallIcon(R.drawable.ic_lookdownnotification)
      .setOngoing(true)
      .setProgress(100, progress, indeterminate?:false)
      .setOnlyAlertOnce(true)
    
    if(allowCancel){
      val intent = WorkManager.getInstance(context).createCancelPendingIntent(getId())
      notificationBuilder.apply {
        addAction(android.R.drawable.ic_delete, cancel, intent)
      }
    }
    
    return ForegroundInfo(notificationId, notificationBuilder.build())
  }
  
  
  
  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel(id: String, notificationImportance: Int) {
    val ldDownloadChannel = NotificationChannel(id, "Downloads", notificationImportance) //NotificationManager.IMPORTANCE_MIN
  
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
    
    ldDownloadChannel.apply {
      description = "LookDown download channel"
      enableLights(true)
      enableVibration(true)
      setSound(sound, attributes)
    }
    notificationManager.createNotificationChannel(ldDownloadChannel)
  }
  
  
}