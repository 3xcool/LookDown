package com.andrefilgs.lookdown_android.wmservice.utils

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.andrefilgs.lookdown_android.wmservice.factory.LDWorkRequestFactory
import com.andrefilgs.lookdown_android.wmservice.factory.LD_KEY_DOWNLOAD_ID

const val LD_KEY_URL="LD_KEY_URL"
const val LD_KEY_PROGRESS="LD_KEY_PROGRESS"
const val LD_KEY_FILENAME="LD_KEY_FILENAME"
const val LD_KEY_ID="LD_KEY_ID"
const val LD_KEY_NOTIFICATION_ID="LD_KEY_NOTIFICATION_ID"
const val LD_KEY_INIT_TIME="LD_KEY_INIT_TIME"
const val LD_KEY_ELAPSED_TIME="LD_KEY_ELAPSED_TIME"

fun ListenableWorker.getInitTime() = this.inputData.getLong(LDWorkRequestFactory.WORK_KEY_INIT_TIME, -1)
fun ListenableWorker.getLdUrl() = this.inputData.getString(LD_KEY_URL)
fun ListenableWorker.getLDProgress() = this.inputData.getInt(LD_KEY_PROGRESS,0)
fun ListenableWorker.getFilename() = this.inputData.getString(LD_KEY_FILENAME)
fun ListenableWorker.getLDId() = this.inputData.getString(LD_KEY_ID)
fun ListenableWorker.getLDNotificationId() = this.inputData.getInt(LD_KEY_NOTIFICATION_ID, 0)


fun Data.getLdId() = this.getString(LD_KEY_ID)
fun Data.getProgressLdId() = this.getString(LD_KEY_DOWNLOAD_ID)
fun Data.getOngoingProgress() = this.getString(LD_KEY_PROGRESS)

fun WorkInfo.getLdId(): String? = this.outputData.getString(LD_KEY_ID)
fun WorkInfo.getLdUrl(): String? = this.outputData.getString(LD_KEY_URL)
fun WorkInfo.getLDProgress(): Int = this.outputData.getInt(LD_KEY_PROGRESS,0)


//WorkManager have a limit of 10240KB
fun Data.Builder.putLDNotificationId(value: Int) = this.putInt(LD_KEY_NOTIFICATION_ID, value)
fun Data.Builder.putLdId(value: String) = this.putString(LD_KEY_ID, value)
fun Data.Builder.putUrl(value: String) = this.putString(LD_KEY_URL, value)
fun Data.Builder.putFilename(value: String) = this.putString(LD_KEY_FILENAME, value)
fun Data.Builder.putLdProgress(value: Int) = this.putInt(LD_KEY_PROGRESS, value)
fun Data.Builder.putInitTime(value: Long) = this.putLong(LD_KEY_INIT_TIME, value)
fun Data.Builder.putElapsedTime(value: Long) = this.putLong(LD_KEY_ELAPSED_TIME, value)