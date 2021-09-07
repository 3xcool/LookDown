package com.andrefilgs.lookdown_android.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers


//See TestDispatcher Class at Test folder
class StandardDispatchers : DispatcherProvider{
  override val main: CoroutineDispatcher
    get() = Dispatchers.Main
  override val io: CoroutineDispatcher
    get() = Dispatchers.IO
  override val default: CoroutineDispatcher
    get() = Dispatchers.Default
  override val unconfined: CoroutineDispatcher
    get() = Dispatchers.Unconfined
  
}



interface DispatcherProvider {
  val main :CoroutineDispatcher
  val io :CoroutineDispatcher
  val default :CoroutineDispatcher
  val unconfined :CoroutineDispatcher
}