package com.xcool.lookdown.model

import java.io.File


/**
 * @author André Filgueiras on 28/11/2020
 */
data class LDDownload(
  var url: String? = null,
  val file: File? = null,
  val filename: String? = null,
  val fileSize: Long? = null,
  var bytesDownloaded: Long? = null,
  var lastModified: String? = null,
  var progress: Int = 0,
  var state: DownloadState?=DownloadState.Empty
){

}