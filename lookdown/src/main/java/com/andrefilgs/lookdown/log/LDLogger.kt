package com.andrefilgs.lookdown.log

import android.util.Log
import com.andrefilgs.lookdown.LDGlobals.LD_LOG_TAG


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