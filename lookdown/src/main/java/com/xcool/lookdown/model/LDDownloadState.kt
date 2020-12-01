package com.xcool.lookdown.model


/**
 * @author André Filgueiras on 25/11/2020
 */
sealed class LDDownloadState{
  object Empty : LDDownloadState()
  object Queued : LDDownloadState()
  object Downloading : LDDownloadState()
  object Paused : LDDownloadState()
  object Incomplete : LDDownloadState()
  object Downloaded : LDDownloadState()
  data class Error(val message: String) : LDDownloadState()
}
