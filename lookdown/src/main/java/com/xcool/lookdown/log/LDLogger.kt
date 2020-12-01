package com.xcool.lookdown.log

import android.util.Log


/**
 * @author André Filgueiras on 30/11/2020
 */
object LDLogger {
  var showLogs = true  //todo 1000
  var ldTag = "LookDown"
  
  fun log(msg:String){
    if(showLogs){
      Log.d(ldTag, msg)
    }
  }
}