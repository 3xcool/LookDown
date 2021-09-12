package com.andrefilgs.lookdown_android.log

import android.util.Log
import com.andrefilgs.lookdown_android.LDGlobals.LD_LOG_TAG
import javax.inject.Inject


/**
 * @author André Filgueiras on 30/11/2020
 */
internal class LDLogger (
  var showLogs :Boolean = false,
  var tag :String= LD_LOG_TAG
){
  
  fun log(msg:String){
    if(showLogs){
      Log.d(tag, msg)
    }
  }
}