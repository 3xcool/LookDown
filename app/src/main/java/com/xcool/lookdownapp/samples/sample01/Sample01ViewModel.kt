package com.xcool.lookdownapp.samples.sample01

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.xcool.lookdown.LookDownUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * @author André Filgueiras on 28/11/2020
 */
class Sample01ViewModel @ViewModelInject constructor(
  @ApplicationContext val context: Context,
  @Assisted private val state: SavedStateHandle
  ): ViewModel() {
  
  
  private val _loading : MutableLiveData<Boolean> = MutableLiveData()
  val loading : LiveData<Boolean> = _loading
  
  private val _feedback : MutableLiveData<String> = MutableLiveData()
  val feedback : LiveData<String> = _feedback
  
  fun download(url:String){
    viewModelScope.launch {
      _loading.value = true
      withContext(Dispatchers.IO){
        LookDownUtil.download(context, url, "takeatour", ".mp4", folder = null, true)
      }
      _loading.value = false
    }
    
  }
  
}