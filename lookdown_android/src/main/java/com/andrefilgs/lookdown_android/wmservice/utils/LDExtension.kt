package com.andrefilgs.lookdown_android.wmservice.utils

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.andrefilgs.lookdown_android.wmservice.factory.LDWorkRequestFactory

const val LD_KEY_URL="LD_KEY_URL"
const val LD_KEY_PROGRESS="LD_KEY_PROGRESS"
const val LD_KEY_FILENAME="LD_KEY_FILENAME"

fun ListenableWorker.getInitTime() = this.inputData.getLong(LDWorkRequestFactory.WORK_KEY_INITTIME, -1)
fun ListenableWorker.getLdUrl() = this.inputData.getString(LD_KEY_URL)
fun ListenableWorker.getLDProgress() = this.inputData.getInt(LD_KEY_PROGRESS,0)
fun ListenableWorker.getFilename() = this.inputData.getString(LD_KEY_FILENAME)


fun WorkInfo.getLdUrl(): String? = this.outputData.getString(LD_KEY_URL)
fun WorkInfo.getLDProgress(): Int = this.outputData.getInt(LD_KEY_PROGRESS,0)


//WorkManager have a limit of 10240KB
fun Data.Builder.putUrl(url: String) = this.putString(LD_KEY_URL, url)
fun Data.Builder.putFilename(value: String) = this.putString(LD_KEY_FILENAME, value)
fun Data.Builder.putLdProgress(value: Int) = this.putInt(LD_KEY_PROGRESS, value)