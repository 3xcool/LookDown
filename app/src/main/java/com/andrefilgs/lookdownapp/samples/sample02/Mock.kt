package com.andrefilgs.lookdownapp.samples.sample02

import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState

private val takeatour = "https://www.dropbox.com/s/exjzq4qhcpatylm/takeatour.mp4?dl=1"
// private val takeatour = "https://tekmoon.com/spaces/takeATour.mp4"

fun buildFakeLDDownloadList(): MutableList<LDDownload> {
  val mutableList = mutableListOf<LDDownload>()
  for (i in 1..15) {
    mutableList.add(
      LDDownload(id= i.toString(), url = takeatour, filename = "Take a Tour $i", fileExtension = ".mp4", progress = 0, state = LDDownloadState.Empty, title = "Filename $i"
    )
    )
  }
  return mutableList
}