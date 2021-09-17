package com.andrefilgs.lookdown_android.wmservice.utils

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo

const val LD_KEY_ID="LD_KEY_ID"
const val LD_KEY_NOTIFICATION_ID="LD_KEY_NOTIFICATION_ID"
const val LD_KEY_TITLE="LD_KEY_TITLE"
const val LD_KEY_FILENAME="LD_KEY_FILENAME"
const val LD_KEY_FILE_EXTENSION="LD_KEY_FILE_EXTENSION"
const val LD_KEY_PROGRESS="LD_KEY_PROGRESS"
const val LD_KEY_URL="LD_KEY_URL"
const val LD_KEY_RESUME="LD_KEY_RESUME"
const val LD_KEY_INIT_TIME="LD_KEY_INIT_TIME"
const val LD_KEY_ELAPSED_TIME="LD_KEY_ELAPSED_TIME"
const val LD_KEY_OUTPUT_MSG="LD_KEY_OUTPUT_MSG"
const val LD_KEY_ALLOW_CANCEL="LD_KEY_ALLOW_CANCEL"
const val LD_KEY_NOTIFICATION_IMPORTANCE="LD_KEY_NOTIFICATION_IMPORTANCE"

fun ListenableWorker.getLDId() = this.inputData.getString(LD_KEY_ID)
fun ListenableWorker.getLDNotificationId() = this.inputData.getInt(LD_KEY_NOTIFICATION_ID, -1)
fun ListenableWorker.getLDTitle() = this.inputData.getString(LD_KEY_TITLE)
fun ListenableWorker.getLDFilename() = this.inputData.getString(LD_KEY_FILENAME)
fun ListenableWorker.getLDFileExtension() = this.inputData.getString(LD_KEY_FILE_EXTENSION)
fun ListenableWorker.getLDProgress(defaultValue: Int=0) = this.inputData.getInt(LD_KEY_PROGRESS,defaultValue)
fun ListenableWorker.getLDUrl() = this.inputData.getString(LD_KEY_URL)
fun ListenableWorker.getLDResume() = this.inputData.getBoolean(LD_KEY_RESUME, false)
fun ListenableWorker.getLDInitTime() = this.inputData.getLong(LD_KEY_INIT_TIME, -1)
fun ListenableWorker.getLDAllowWorkCancel() = this.inputData.getBoolean(LD_KEY_ALLOW_CANCEL, true)
fun ListenableWorker.getLDNotificationImportance(defaultValue: Int=1) = this.inputData.getInt(LD_KEY_NOTIFICATION_IMPORTANCE,defaultValue) //1 = IMPORTANCE_MIN


fun Data.getLDId() = this.getString(LD_KEY_ID)
fun Data.getLDNotificationId() = this.getInt(LD_KEY_NOTIFICATION_ID, -1)
fun Data.getLDFilename() = this.getString(LD_KEY_FILENAME)
fun Data.getLDTitle() = this.getString(LD_KEY_TITLE)
fun Data.getLDProgress(defaultValue:Int=0) = this.getInt(LD_KEY_PROGRESS, defaultValue)
fun Data.getLDUrl() = this.getString(LD_KEY_URL)
fun Data.getLDInitTime(): Long = this.getLong(LD_KEY_INIT_TIME, -1L)
fun Data.getLDElapsedTime(): Long = this.getLong(LD_KEY_ELAPSED_TIME, -1L)
fun Data.getLDOutputMsg() = this.getString(LD_KEY_OUTPUT_MSG)



// fun WorkInfo.getLDId(): String? = this.outputData.getString(LD_KEY_ID)
// fun WorkInfo.getLDProgress(defaultValue:Int=0): Int = this.outputData.getInt(LD_KEY_PROGRESS,defaultValue)
// fun WorkInfo.getLDUrl(): String? = this.outputData.getString(LD_KEY_URL)
// fun WorkInfo.getLDInitTime(): Long = this.outputData.getLong(LD_KEY_INIT_TIME, 0L)
// fun WorkInfo.getLDElapsedTime(): Long = this.outputData.getLong(LD_KEY_ELAPSED_TIME, 0L)


//WorkManager have a limit of 10240KB
fun Data.Builder.putLDId(value: String) = this.putString(LD_KEY_ID, value)
fun Data.Builder.putLDNotificationId(value: Int) = this.putInt(LD_KEY_NOTIFICATION_ID, value)
fun Data.Builder.putLDTitle(value: String?) = this.putString(LD_KEY_TITLE, value)
fun Data.Builder.putLDFilename(value: String) = this.putString(LD_KEY_FILENAME, value)
fun Data.Builder.putLDFileExtension(value: String) = this.putString(LD_KEY_FILE_EXTENSION, value)
fun Data.Builder.putLdProgress(value: Int) = this.putInt(LD_KEY_PROGRESS, value)
fun Data.Builder.putLDUrl(value: String) = this.putString(LD_KEY_URL, value)
fun Data.Builder.putLDResume(value: Boolean) = this.putBoolean(LD_KEY_RESUME, value)
fun Data.Builder.putLDInitTime(value: Long) = this.putLong(LD_KEY_INIT_TIME, value)
fun Data.Builder.putLDElapsedTime(value: Long) = this.putLong(LD_KEY_ELAPSED_TIME, value)
fun Data.Builder.putLDOutputMsg(value: String) = this.putString(LD_KEY_OUTPUT_MSG, value)
fun Data.Builder.putLDAllowWorkCancel(value: Boolean) = this.putBoolean(LD_KEY_ALLOW_CANCEL, value)
fun Data.Builder.putLdNotificationImportance(value: Int) = this.putInt(LD_KEY_NOTIFICATION_IMPORTANCE, value)
