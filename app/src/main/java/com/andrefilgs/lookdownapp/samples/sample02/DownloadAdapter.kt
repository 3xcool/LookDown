package com.andrefilgs.lookdownapp.samples.sample02

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.andrefilgs.lookdown.domain.LDDownload
import com.andrefilgs.lookdown.domain.LDDownloadState
import com.andrefilgs.lookdown.utils.formatFileSize
import com.andrefilgs.lookdownapp.databinding.ItemDownloadBinding


/**
 * @author André Filgueiras on 25/11/2020
 */
class DownloadAdapter (private val listener: DownloadListener) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>(){
  
  
  inner class DownloadViewHolder(val binding: ItemDownloadBinding): RecyclerView.ViewHolder(binding.root)
  
  private var downloadList: MutableList<LDDownload> = mutableListOf()
  
  fun updateDownloadList(list:MutableList<LDDownload>){
    this.downloadList.clear()
    this.downloadList.addAll(list)
    notifyDataSetChanged()
  }
  
  fun updateDownloadItemProgress(download: LDDownload, mPosition: Int?=null){
    val position = mPosition ?: downloadList.indexOf(download)
    if(position != -1){
      val item = downloadList.firstOrNull() {
        it.id == download.id
      }
      item?.let{
        item.progress = download.progress
        item.state = download.state
      }
      notifyItemChanged(position)
    }
  }
  
  
  
  
  
  override fun getItemCount(): Int {
    return downloadList.size
  }
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
    val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return DownloadViewHolder(binding)
  }
  
  private var onItemClickListener: ((LDDownload, Int) -> Unit)? = null
  
  override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
    val download = downloadList[position]
    
    with(holder){
      Glide.with(binding.root).load(download.getDownloadStateImage()).into(binding.itemDownIvAction)
      binding.itemDownTvTitle.text = download.title
      
      val progressMessage = if(download.downloadedBytes !=null && download.fileSize != null && download.downloadedBytes!! > 0 && download.fileSize!! > 0){
        "${download.progress}% (${formatFileSize(download.downloadedBytes!!)} / ${formatFileSize(download.fileSize!!)})"
      }else{
        "${download.progress}%"
      }
      binding.itemDownTvProgress.text = progressMessage
      binding.itemDownProgress.progress = download.progress
      binding.itemDownProgress.isIndeterminate = download.state == LDDownloadState.Queued
      binding.itemDownTvIndex.text = (position +1).toString()
      
      binding.itemDownIvAction.setOnClickListener {
        listener.onDownloadIconClick(download, position)
      }
      binding.root.setOnClickListener {
        onItemClickListener?.let { it(download, position) }
      }
    }
  }
  
  fun setOnItemClickListener(listener: (LDDownload, Int) -> Unit) {
    onItemClickListener = listener
  }
  

  interface DownloadListener{
    fun onDownloadIconClick(download: LDDownload, position: Int)
  }
  

  
}