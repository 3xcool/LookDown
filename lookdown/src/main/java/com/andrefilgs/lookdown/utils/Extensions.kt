package com.andrefilgs.lookdown.utils


/**
 * @author André Filgueiras on 30/11/2020
 */

fun Boolean?.orDefault(default: Boolean = false): Boolean {
  return this ?: default
}
