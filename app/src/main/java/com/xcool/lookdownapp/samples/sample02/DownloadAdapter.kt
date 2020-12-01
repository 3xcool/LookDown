package com.xcool.lookdownapp.samples.sample02

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xcool.lookdownapp.databinding.ItemDownloadBinding
import com.xcool.lookdownapp.samples.sample02.model.Download


/**
 * @author André Filgueiras on 25/11/2020
 */
class DownloadAdapter (private val listener: DownloadListener) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {
  
  
  inner class DownloadViewHolder(val binding: ItemDownloadBinding): RecyclerView.ViewHolder(binding.root)
  
  private val differCallback = object : DiffUtil.ItemCallback<Download>() {
    override fun areItemsTheSame(oldItem: Download, newItem: Download): Boolean {
      return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: Download, newItem: Download): Boolean {
      return oldItem == newItem
    }
  }
  
  val differ = AsyncListDiffer(this, differCallback)
  
  var downloadList: List<Download>
    get() = differ.currentList
    set(value) = differ.submitList(value)
  
  
  fun updateDownload(download: Download){
    downloadList.firstOrNull() {
      it.id == download.id
    }
    val position = downloadList.indexOf(download)
    notifyItemChanged(position)
  }
  
  override fun getItemCount(): Int {
    // return differ.currentList.size
    return downloadList.size
  }
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
    val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return DownloadViewHolder(binding)
  }
  
  private var onItemClickListener: ((Download, Int) -> Unit)? = null
  
  override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
    // val download = differ.currentList[position]
    val download = downloadList[position]
    
    with(holder){
      Glide.with(binding.root).load(download.getDownloadStateImage()).into(binding.itemDownIvAction)
      binding.itemDownTvTitle.text = download.title
      binding.itemDownTvProgress.text = "${download.progress}%"
      binding.itemDownProgress.progress = download.progress
      binding.itemDownTvIndex.text = (position +1).toString()
      
      binding.itemDownIvAction.setOnClickListener {
        listener.onDownloadIconClick(download, position)
      }
      binding.root.setOnClickListener {
        onItemClickListener?.let { it(download, position) }
      }
    }
  }
  
  fun setOnItemClickListener(listener: (Download, Int) -> Unit) {
    onItemClickListener = listener
  }
  

  interface DownloadListener{
    fun onDownloadIconClick(download: Download, position: Int)
  }
  
}