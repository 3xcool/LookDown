package com.andrefilgs.lookdown_android

import com.andrefilgs.fileman.FilemanDrivers


/**
 * @author André Filgueiras on 30/11/2020
 */
object LDGlobals {
  const val LD_TEMP_EXT = ".tmp"
  const val LD_VIDEO_MP4_EXT = ".mp4"
  const val LD_PNG_EXT = ".png"
  const val LD_JSON_EXT = ".json"
  
  var LD_PROGRESS_RENDER_DELAY = 500L
  var LD_CHUNK_SIZE = 1024
  var LD_TIMEOUT = 5000
  var LD_CONNECT_TIMEOUT = 5000
  var LD_DEFAULT_FOLDER = "/lookdown"
  var LD_DEFAULT_DRIVER = FilemanDrivers.Internal.type //0 = SandBox, 1= Device, 2 = SD Card //
}