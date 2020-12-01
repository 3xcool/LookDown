package com.xcool.lookdownapp.app

import android.util.Log


/**
 * @author André Filgueiras on 30/11/2020
 */
internal object AppLogger {
  var showLogs = true
  var ldTag = "LookDown"
  
  fun log(msg:String){
    if(showLogs){
      Log.d(ldTag, msg)
    }
  }
}