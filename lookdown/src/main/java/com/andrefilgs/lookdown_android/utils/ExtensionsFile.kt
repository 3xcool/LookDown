package com.andrefilgs.lookdown_android.utils


/**
 * @author André Filgueiras on 30/11/2020
 */


fun formatFileSize(size: Long): String {
  var mSize = size
  var suffix: String? = null
  if (mSize >= 1024) {
    suffix = "KB"
    mSize /= 1024
    if (mSize >= 1024) {
      suffix = "MB"
      mSize /= 1024
    }
  }
  val resultBuffer = StringBuilder(mSize.toString())
  var commaOffset = resultBuffer.length - 3
  while (commaOffset > 0) {
    resultBuffer.insert(commaOffset, ',')
    commaOffset -= 3
  }
  if (suffix != null) resultBuffer.append(suffix)
  return resultBuffer.toString()
}