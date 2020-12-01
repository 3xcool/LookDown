package com.xcool.lookdown.model

import java.io.File
import java.util.*

/**
 * @author André Filgueiras on 28/11/2020
 */
data class LDDownload(
  var id: String? = UUID.randomUUID().toString(),
  var url: String? = null,
  var file: File? = null,
  var filename: String? = null,
  var fileSize: Long? = null,
  var bytesDownloaded: Long? = null,
  var lastModified: String? = null,
  var progress: Int = 0,
  var state: DownloadState?=DownloadState.Empty,
  var feedback: String?=null,
){

}