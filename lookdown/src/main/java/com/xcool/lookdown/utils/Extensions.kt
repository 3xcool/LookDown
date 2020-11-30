package com.xcool.lookdownapp.utils


/**
 * @author André Filgueiras on 30/11/2020
 */

fun Boolean?.orDefault(default: Boolean = false): Boolean {
  return this ?: default
}


fun String.subLua(start: Int, finish: Int? = null): String  {
  var _start = start - 1
  var _finish = finish
  
  if(start < 0) _start = start + this.length
  if(finish != null && finish < 0) _finish = finish + this.length + 1
  
  return if(_finish == null){
    this.substring(_start)
  }else{
    this.substring(_start, _finish)
  }
}

fun String.subKot(start: Int, finish: Int? = null): String  {
  var _start = start
  var _finish = finish
  
  if(start < 0) _start = start + this.length
  if(finish != null && finish < 0) _finish = finish + this.length
  
  return if(_finish == null){
    this.substring(_start)
  }else{
    this.substring(_start, _finish)
  }
}
