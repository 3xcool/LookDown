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
import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import com.andrefilgs.lookdown_android.wmservice.factory.LDWorkRequestFactory
import com.andrefilgs.lookdown_android.wmservice.utils.*
import kotlinx.coroutines.*
import com.xcool.lookdown.R
import kotlinx.coroutines.flow.collect

const val CHANNEL_ID_LD_DOWNLOAD = "CHANNEL_ID_LD_DOWNLOAD"

// @ExperimentalCoroutinesApi
// @HiltWorker
// internal class LDDownloadWorker @AssistedInject constructor(
//   @Assisted context: Context,
//   @Assisted parameters: WorkerParameters,
//   // private val lookDown: LookDown,
//   // private val gson : Gson,
//   // private val ldLogger: LDLogger
// ) :  CoroutineWorker(context, parameters) {

internal class LDDownloadWorker (
  private val context: Context,
  parameters: WorkerParameters,
  // private val lookDown: LookDown,
  // private val gson : Gson,
  // private val ldLogger: LDLogger
) :  CoroutineWorker(context, parameters) {
  
  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val dispatchers = StandardDispatchers()
  
  //todo 1000
  private val lookDown = LookDown.Builder(context).apply {
    setFileExtension(LDGlobals.LD_VIDEO_MP4_EXT)
    activateLogs()
  }.build()
  
  @ExperimentalCoroutinesApi
  @InternalCoroutinesApi
  override suspend fun doWork(): Result = coroutineScope{
    val initTime = getLDInitTime()
    val url = getLDUrl()
    val title = getLDTitle()
    val filename = getLDFilename()
    val progress = getLDProgress()
    val ldId = getLDId()
    val notificationId = getLDNotificationId()
    val resume = getLDResume()
    val ldDownload = LDDownload(id= ldId!!, url=url, title= title, filename = filename, progress = progress, state = LDDownloadState.Queued)
    withContext(dispatchers.io){
      // Create a Notification channel if necessary
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        createChannel(CHANNEL_ID_LD_DOWNLOAD)
      }
      
      val msg = "Starting Download $url"
      setForeground(createForegroundInfo(notificationId, msg, progress,title=title, indeterminate = true))
      val job = async {
        // mockDownload()
        download(notificationId, ldDownload, resume)
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
  private suspend fun download(notificationId: Int, ldDownload: LDDownload, resume:Boolean): Boolean {
    val res = lookDown.downloadWithFlow(ldDownload, resume = resume) ?: return false
    res.collect { ldDownloadProgress->
      val progress = ldDownloadProgress.progress
      setForeground(createForegroundInfo(notificationId,"Progress $progress%", progress, title = ldDownload.title))
      
      val output = Data.Builder().apply {
        putLDId(ldDownload.id)
        putLdProgress(progress)
        
      }.build()
      setProgressAsync(output)
    }
    return true
  }

  
  // private suspend fun download(inputUrl: String, outputFile: String) {
  private suspend fun mockDownload() {
    // Downloads a file and updates bytes read
    // Calls setForegroundInfo() periodically when it needs to update
    // the ongoing Notification
    val iterations = 10
    for (i in 1..iterations){
      delay(1000L)
      val progress =  i*100/iterations
      setForeground(createForegroundInfo(0, "Progress $progress%", progress))
      setProgressAsync(Data.Builder().putLdProgress(progress).build())
    }
  }
  
  
  //todo 1000 add parameters
  private fun createForegroundInfo(notificationId:Int, msg:String, progress: Int, title:String?=null, indeterminate:Boolean?=false): ForegroundInfo {
    val id = CHANNEL_ID_LD_DOWNLOAD
    val mTitle = title ?: "LookDown"
    val cancel = "Cancel"
    // This PendingIntent can be used to cancel the worker
    val intent = WorkManager.getInstance(context).createCancelPendingIntent(getId())
    
    val notification = NotificationCompat.Builder(context, id)
      .setContentTitle(mTitle)
      .setTicker(mTitle)
      .setContentText(msg)
      .setSmallIcon(R.drawable.ld_ic_download_24) //todo 1000
      .setOngoing(true)
      .setProgress(100, progress, indeterminate?:false)
      // Add the cancel action to the notification which can
      // be used to cancel the worker
      .setOnlyAlertOnce(true)
      .addAction(android.R.drawable.ic_delete, cancel, intent)
      .build()
    
    return ForegroundInfo(notificationId, notification)
  }
  
  
  
  @RequiresApi(Build.VERSION_CODES.O)
  private fun createChannel(id: String) {
    // val ldDownloadChannel = NotificationChannel(id, "Downloads", NotificationManager.IMPORTANCE_MIN )
    val ldDownloadChannel = NotificationChannel(id, "Downloads", NotificationManager.IMPORTANCE_HIGH ) //todo 1000 as parameter
  
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