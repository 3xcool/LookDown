package com.xcool.lookdown

import com.andrefilgs.fileman.FilemanDrivers


/**
 * @author André Filgueiras on 30/11/2020
 */
object LDConstants {
  
  const val LD_TEMP_EXT = ".tmp"
  const val LD_VIDEO_EXT = ".mp4"
  const val LD_PNG_EXT = ".png"
  const val LD_JSON_EXT = ".json"
  
  const val LD_TIMEOUT = 5000
  const val LD_CONNECTTIMEOUT = 5000
  const val LD_DEFAULT_FOLDER = "/lookdown"
  val LD_DEFAULT_DRIVER = FilemanDrivers.Internal.type
  
  const val LD_CHUNK_SIZE = 1024
  
}