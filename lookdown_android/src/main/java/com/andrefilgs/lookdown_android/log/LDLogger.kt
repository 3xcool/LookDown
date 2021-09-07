package com.andrefilgs.lookdown_android.log

import android.util.Log


/**
 * @author André Filgueiras on 30/11/2020
 */
internal object LDLogger {
  var showLogs = false
  var ldTag = "LookDown"
  
  fun log(msg:String){
    if(showLogs){
      Log.d(ldTag, msg)
    }
  }
}