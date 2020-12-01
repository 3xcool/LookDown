package com.xcool.lookdownapp.samples.sample02.model


/**
 * @author André Filgueiras on 25/11/2020
 */
sealed class DownloadState{
  object Empty : DownloadState()
  object Queued : DownloadState()
  object Downloading : DownloadState()
  object Paused : DownloadState()
  object Downloaded : DownloadState()
  data class Error(val message: String) : DownloadState()
}
