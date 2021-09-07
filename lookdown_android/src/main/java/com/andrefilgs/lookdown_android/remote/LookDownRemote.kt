package com.andrefilgs.lookdown_android.remote

import com.andrefilgs.lookdown_android.domain.LDDownload
import kotlinx.coroutines.flow.Flow
import java.io.File

abstract class LookDownRemote {
  
  abstract suspend fun setup(
    url: String,
    resume: Boolean,
    file: File,
    headers: Map<String, String>?
  ): Pair<Boolean, String>
  
  abstract suspend fun download(
    ldDownload: LDDownload,
    file: File,
    chunkSize: Int,
    resume: Boolean
  ): Flow<LDDownload>
}