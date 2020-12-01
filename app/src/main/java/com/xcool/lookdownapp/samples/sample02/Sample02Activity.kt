package com.xcool.lookdownapp.samples.sample02

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.xcool.coroexecutor.core.ExecutorSchema
import com.xcool.lookdownapp.R
import com.xcool.lookdownapp.databinding.ActivitySample02Binding
import com.xcool.lookdownapp.samples.sample02.model.Download
import com.xcool.lookdownapp.samples.sample02.model.DownloadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@AndroidEntryPoint
class Sample02Activity : AppCompatActivity(), AdapterView.OnItemSelectedListener, DownloadAdapter.DownloadListener {
  
  private var _binding: ActivitySample02Binding? = null
  private val binding get() = _binding!!
  
  private lateinit var context: Context
  
  private val executorTypeNameList = buildExecutorOptions()
  
  private lateinit var downAdapter: DownloadAdapter
  
  @ExperimentalCoroutinesApi
  private val viewModel : DownloadViewModel by viewModels()
  
  
  
  @InternalCoroutinesApi
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // setContentView(R.layout.activity_sample02)
    _binding = ActivitySample02Binding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setSpinner()
    setupRecyclerView()
    setClickListeners()
    
    
    downAdapter.setOnItemClickListener { item, position ->
      toaster("${item.title} selected")
    }
    this.context = this
    subscribeObservers()
  }
  
  private fun toaster(msg:String){
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
  }
  
  private fun setClickListeners() {
    binding.downloadBtnStopAll.setOnClickListener {
      viewModel.stopAllDownload()
    }
  }
  
  
  private fun buildExecutorOptions() :List<String>{
    val list:MutableList<String> = mutableListOf()
    ExecutorSchema::class.sealedSubclasses.forEach { executor ->
      executor.simpleName?.let{ list.add(it)}
    }
    return list
  }
  
  private fun setupRecyclerView() {
    val context = this
    downAdapter = DownloadAdapter(this)
    binding.downloadRecyclerDownloads.apply {
      adapter = downAdapter
      layoutManager = LinearLayoutManager(context)
      // addOnScrollListener(this@ActivityDownload.scrollListener)
    }
    // downAdapter.differ.submitList(Download.buildFakeDownloadList())
    downAdapter.downloadList = Download.buildFakeDownloadList()
  }
  
  private fun setSpinner() {
    val arrayAdapter: ArrayAdapter<String> = ArrayAdapter(
      this,
      android.R.layout.simple_expandable_list_item_1,
      executorTypeNameList
    )  //simple_spinner_item
    arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    binding.downloadSpinnerExecutorType.adapter = arrayAdapter
    binding.downloadSpinnerExecutorType.onItemSelectedListener = this
    val position = getExecutorSchemaPosition(viewModel.schema.value)
    binding.downloadSpinnerExecutorType.setSelection(position)
  }
  
  private fun getExecutorSchemaPosition(schema: ExecutorSchema): Int {
    return executorTypeNameList.indexOf(schema::class.java.simpleName)
  }
  
  
  override fun onNothingSelected(p0: AdapterView<*>?) {
    //do nothing
  }
  
  override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, p3: Long) {
    //    val item = parent!!.getItemAtPosition(position).toString()
    val executorClass  = ExecutorSchema::class.sealedSubclasses.filter {
      it.isFinal && it.simpleName == executorTypeNameList[position]
    }
    executorClass[0].objectInstance?.let {
      viewModel.setExecutorSchema(it)
    }
  }
  
  override fun onDownloadIconClick(download: Download, position: Int) {
    when(download.getCurrentDownloadState()){
      DownloadState.Empty       -> viewModel.startDownload(download)
      DownloadState.Paused      -> viewModel.startDownload(download)
      DownloadState.Downloading -> viewModel.stopDownload(download)
      DownloadState.Downloaded  -> viewModel.deleteDownload(download)
      else                      -> Unit
    }
    
    
  }
  
  @InternalCoroutinesApi
  @ExperimentalCoroutinesApi
  private fun subscribeObservers(){
    viewModel.downloadInfo.observe(this, { event ->
      event.getContentIfNotHandled().let{ download ->
        download?.let{downAdapter.updateDownload(it)}
      }
    })
    
    
    // lifecycleScope.launchWhenStarted {
    //   viewModel.schema.collect {schema->
    //       toaster("Schema is ${schema::class.java.simpleName}")
    //     }
    
    // viewModel.downloadInfo.collect {download->
    //   download?.let{
    //     downAdapter.updateDownload(it)
    //   }
    // }
    // }
  }
  
  
}