package com.andrefilgs.lookdown_android.remote

import com.andrefilgs.fileman.Fileman
import com.andrefilgs.lookdown_android.LDGlobals
import com.andrefilgs.lookdown_android.domain.LDDownload
import com.andrefilgs.lookdown_android.domain.LDDownloadState
import com.andrefilgs.lookdown_android.log.LDLogger
import com.andrefilgs.lookdown_android.utils.DispatcherProvider
import com.andrefilgs.lookdown_android.utils.StandardDispatchers
import com.andrefilgs.lookdown_android.utils.formatFileSize
import com.andrefilgs.lookdown_android.utils.orDefault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@ExperimentalCoroutinesApi
internal class LookDownRemoteImpl(
  private val logger: LDLogger,
  private val dispatcher :DispatcherProvider = StandardDispatchers(),
  private var timeout: Int,
  private var connectionTimeout: Int,
) :LookDownRemote() {
  
  override suspend fun setup(url:String, resume:Boolean, file: File, headers: Map<String, String>?): HttpURLConnection? {
    return kotlin.runCatching {
      val connection = URL(url).openConnection() as HttpURLConnection
  
      // connection.setRequestProperty("If-Range", lastModified);
      
      if(isResumeDownload(file, resume)) connection.setRequestProperty("Range", "bytes=" + file.length().toInt() + "-")
      connection.readTimeout = timeout
      connection.connectTimeout = connectionTimeout
      connection.useCaches = false
      connection.requestMethod = "GET"
  
      // connection.setRequestProperty("Accept-Encoding", "gzip"); //http://www.rgagnon.com/javadetails/java-HttpUrlConnection-with-GZIP-encoding.html
  
      connection.doInput = true // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      // connection.setDoOutput(true); //https://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
      
      headers?.let { map ->
        for ((key, value) in map) connection.setRequestProperty(key, value )
      }
      connection.connect()
      
      val httpResponse = getHttpMessage(connection)
      logger.log(httpResponse)

      // expect HTTP 200 OK or 206 Partial
      if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
        return null
      }
      connection
    }.getOrNull()
  }
  
  override suspend fun download(
    connection: HttpURLConnection,
    ldDownload: LDDownload,
    file: File,
    chunkSize: Int,
    resume: Boolean
  ):Flow<LDDownload>{
    
    var fileTotalLength = connection.contentLength.toLong() // might be -1: server did not report the length
    if (isResumeDownload(file, resume) && fileTotalLength>0) fileTotalLength += file.length()  //if is resumed the server will download only the difference, so we need to add to fileLength which are the bytes already downloaded

    ldDownload.fileSize = fileTotalLength
    ldDownload.lastModified = connection.getHeaderField("Last-Modified")

    val input = connection.inputStream // download the file
    val output = createOutputFileStream( file, resume )

    val data = ByteArray(chunkSize)
    var downloadedBytes = file.length()
    var chunk: Int
  
    return flow {
      kotlin.runCatching {
        ldDownload.state = LDDownloadState.Downloading
        // emit(ldDownload)
        logger.log("Downloading...")
        while (input!!.read(data).also { chunk = it } != -1) {
          downloadedBytes += chunk.toLong()
          ldDownload.downloadedBytes = downloadedBytes
          if (fileTotalLength > 0)  {
            // ldDownload.progress = (downloadedBytes * 100 / fileTotalLength).toInt()
            ldDownload.updateProgress((downloadedBytes * 100 / fileTotalLength).toInt())
          }
          // logger.log("Writing ${ldDownload.filename}. Current progress: ${ldDownload.progress}")
          output.write(data, 0, chunk)
          emit(ldDownload)
        }
        val resMessage = if(file.length() == fileTotalLength) "SUCCESS" else "INCOMPLETE"
        logger.log("${ldDownload.filename} download with: $resMessage (Downloaded Bytes: ${file.length()} | File Size: $fileTotalLength | Progress: ${ldDownload.progress})")
      }
    }.catch { e ->
      logger.log("Catch ${e.printStackTrace()}")
      ldDownload.state = LDDownloadState.Error("Catch: ${e.printStackTrace()}")
      emit(ldDownload)
      return@catch
    }.onCompletion {
      if (file.length() == fileTotalLength) ldDownload.state = LDDownloadState.Downloaded else ldDownload.state = LDDownloadState.Incomplete
      ldDownload.state?.let { logger.log("Finished download with state: ${it::class.java.simpleName}") }
      try {
        kotlin.runCatching {
          output.close()
          input?.close()
        }
        if (ldDownload.state == LDDownloadState.Downloaded) {
          logger.log("Removing .tmp file")
          Fileman.removeTempExtension(
            file,
            LDGlobals.LD_TEMP_EXT
          )
        }
      } catch (ignored: IOException) {
        logger.log("Error at removing .tmp file")
      }
      connection.disconnect()
      emit(ldDownload)
    }.flowOn(dispatcher.io)
  }
  
  
  
  
  private fun getHttpMessage(connection : HttpURLConnection): String {
    return "Server returned HTTP " + connection.responseCode + " " + connection.responseMessage
  }
  
  private fun isResumeDownload(file: File, resume: Boolean):Boolean{
    return file.exists() && resume.orDefault()
  }
  
  private fun createOutputFileStream(file: File, resume: Boolean): FileOutputStream {
    return if (isResumeDownload(file, resume)) {
      logger.log("File exists and download will be restarted from ${formatFileSize(file.length())}") //TO RESUME https://stackoverflow.com/questions/3428102/how-to-resume-an-interrupted-download-part-2
      FileOutputStream(file, true) //Append file
    } else {
      logger.log("Starting download from beginning")
      file.createNewFile()
      FileOutputStream(file, false)
    }
  }
  

  
}