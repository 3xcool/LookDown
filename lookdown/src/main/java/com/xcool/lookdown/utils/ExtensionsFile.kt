package com.xcool.lookdown.utils


/**
 * @author André Filgueiras on 30/11/2020
 */


fun formatFileSize(size: Long): String? {
  var size = size
  var suffix: String? = null
  if (size >= 1024) {
    suffix = "KB"
    size /= 1024
    if (size >= 1024) {
      suffix = "MB"
      size /= 1024
    }
  }
  val resultBuffer = StringBuilder(size.toString())
  var commaOffset = resultBuffer.length - 3
  while (commaOffset > 0) {
    resultBuffer.insert(commaOffset, ',')
    commaOffset -= 3
  }
  if (suffix != null) resultBuffer.append(suffix)
  return resultBuffer.toString()
}