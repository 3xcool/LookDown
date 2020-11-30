package com.xcool.lookdown

import android.content.Context
import com.andrefilgs.fileman.FilemanDrivers
import com.xcool.lookdown.LookDownConstants.LD_CONNECTTIMEOUT
import com.xcool.lookdown.LookDownConstants.LD_DEFAULT_FOLDER
import com.xcool.lookdown.LookDownConstants.LD_TEMP_EXT
import com.xcool.lookdown.LookDownConstants.LD_TIMEOUT
import com.xcool.lookdownapp.utils.orDefault
import java.io.*
import java.net.HttpURLConnection
import java.net.URL


/**
 * @author André Filgueiras on 28/11/2020
 */
object LookDownUtil {
  
  var forceInterrupt = false
  
  fun download(context: Context, url: String, filename: String, fileExtension: String, folder: String? = LD_DEFAULT_FOLDER, resumeDownload:Boolean?=true): String {
    var input: InputStream? = null
    var output: OutputStream? = null
    var connection: HttpURLConnection? = null
    var file: File? = null
    var downloadedWithSuccess =false
    try {
      file = TempFileman.getFile(context, FilemanDrivers.Internal.type, folder ?: LD_DEFAULT_FOLDER, "$filename$fileExtension$LD_TEMP_EXT")!!
  
      val url = URL(url)
      connection = url.openConnection() as HttpURLConnection
  
      
      if(!file.exists()){
        file.createNewFile()
        output = FileOutputStream(file, false)
      }else if(!resumeDownload.orDefault()){
        //Download from the beginning even if file exists
        file.createNewFile()
        output = FileOutputStream(file, false)
      }else{
        //Resume Download
        //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
        // downloaded = (int) file.length();
        // lastModified = connection.getHeaderField("Last-Modified");
        // connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
        // connection.setRequestProperty("If-Range", lastModified);
        //            connection.setRequestProperty("If-Range", lastModified);
        //Append file
        output = FileOutputStream(file, true)
      }
  
      connection.readTimeout = LD_TIMEOUT
      connection.connectTimeout = LD_CONNECTTIMEOUT
      connection.useCaches = false
      connection.requestMethod = "GET" // GET, POST,HEAD,OPTIONS,PUT,DELETE,TRACE
      
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
      
      connection.doInput = true
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      connection.connect()
  
  
      // expect HTTP 200 OK or 206 Partial, so we don't mistakenly save error report
      // instead of the file
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        return ("Server returned HTTP " + connection.responseCode + " " + connection.responseMessage)
      }
  
      // this will be useful to display download percentage
      // might be -1: server did not report the length
      var fileLength = connection.contentLength.toLong()
      
      // download the file
      input = connection.inputStream
  
      val data = ByteArray(1024) //1024 = 57s / 1024*1024 = 75s / 1024*1024*3 = 108
      var total = file.length()
      var count: Int
      var flag = true
      while (input.read(data).also { count = it } != -1 && flag) {
        total += count.toLong()
        // publishing the progress....
        if (fileLength > 0){
          // only if total length is known
          // publishProgress((total * 100 / fileLength).toInt()) //method from asynctask
        }
        output.write(data, 0, count)
        
        // testing resume download
        if(file.length() > fileLength/2 && forceInterrupt){
          flag = false
        }
      }
      if(file.length() == fileLength){
        downloadedWithSuccess = true
      }
    }catch (e: Exception){
      return "failed"
    }finally {
      try {
        output?.close()
        input?.close()
        if(downloadedWithSuccess) TempFileman.removeTempExtension(file!!, LD_TEMP_EXT)
      } catch (ignored: IOException) {
      }
      connection?.disconnect()
    }
    return "ok"
  }
}