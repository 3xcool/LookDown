package com.xcool.lookdown

import com.xcool.lookdown.log.LDLogger


/**
 * @author André Filgueiras on 28/11/2020
 */
class LookDown {
  
  
  
  companion object{
    fun activateLogs() { LDLogger.showLogs = true }
  
    fun deactivateLogs() { LDLogger.showLogs = false }
  }
  

}