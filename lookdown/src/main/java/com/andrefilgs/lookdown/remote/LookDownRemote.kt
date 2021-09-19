package com.andrefilgs.lookdown.remote

import com.andrefilgs.lookdown.domain.LDDownload
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.net.HttpURLConnection

internal abstract class LookDownRemote {
  
  abstract suspend fun setup(
    url: String,
    resume: Boolean,
    file: File,
    headers: Map<String, String>?
  ): HttpURLConnection?
  
  abstract suspend fun download(
    connection: HttpURLConnection,
    ldDownload: LDDownload,
    file: File,
    chunkSize: Int,
    resume: Boolean
  ): Flow<LDDownload>
}