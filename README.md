# LookDown

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.3xcool/lookdown/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.3xcool/lookdown)

![ic_launcher_round](https://user-images.githubusercontent.com/42469155/133940725-6474dfab-edca-4f6d-acd4-dcfcda37e4f4.png)


# About

Download Manager library using Coroutine, Flow and WorkManager.

Using other 3xCool libraries:
- Fileman for File Management;
- CoroExecutor for Coroutine executor schema (Queue, Conflate and Concurrency).

Status: BETA

# Contents

- [Overview](#overview)
- [Implementation](#implementation)
- [Create_LookDown_Object](#create-lookdown-object)
- [Classes](#classes)
- [Download](#download)
- [Download_as_service](#download-as-service)
- [File_Management](#file-management)

# Overview

See below a small overview what you can do with LookDown:

## Download with Resume:

With Resume the download will restart from where it stopped.




https://user-images.githubusercontent.com/42469155/133938746-65a39fef-dd7d-4235-8046-134bae5f700b.mp4


## Queue


https://user-images.githubusercontent.com/42469155/133938699-39a6a85f-ed13-4e50-a8cd-c7749a5343bb.mp4




## Concurrent



https://user-images.githubusercontent.com/42469155/133938731-9472f19f-ac67-471c-85dd-f74a6216a3bd.mp4



## Conflate



https://user-images.githubusercontent.com/42469155/133938766-c939b2b5-15ef-485f-b2fd-0fc1b1a6e4e7.mp4




## Foreground Service with WorkManager+Coroutine



https://user-images.githubusercontent.com/42469155/133941297-4f0466c8-2a34-4427-b9ea-83c48a1d998f.mp4




https://user-images.githubusercontent.com/42469155/133941299-790610c8-94ff-4ceb-9d56-589a9138276e.mp4



https://user-images.githubusercontent.com/42469155/133941303-f4c99c55-e02c-41fa-82e3-85b8a42f15e0.mp4



# Implementation

In build.gradle (app)
```kotlin
dependencies {
implementation 'com.3xcool:lookdown:$LATEST_VERSION'
}
```

# Create LookDown object

```kotlin
  fun buildLookDown(context:Context): LookDown {
    val builder = LookDown.Builder(context)
    return builder.apply {
      setChunkSize(4096)
      setFileExtension(LDGlobals.LD_VIDEO_MP4_EXT)  //you can change file extension for each download call
      setDriver(LDGlobals.LD_DEFAULT_DRIVER) //0 = Sandbox (Where the app is installed), 1 = Internal (Phone), 2 = SD Card
      setFolder(LDGlobals.LD_DEFAULT_FOLDER)
      setForceResume(true)  //check if server allow resume
      setTimeout(5000)
      setConnectTimeout(5000)
      setProgressRenderDelay(500L)  //very important to avoid to flaky issues in RecyclerView render, probably fixed in Jetpack Compose
      setLogTag("LookDown")
      activateLogs()
    }.build()
  }
 ``` 

# Classes

## LDDownload

Class that represents a LookDown file.
```kotlin
data class LDDownload(
  var id: String = UUID.randomUUID().toString(),
  var url: String? = null,
  var filename: String? = null,
  var fileExtension: String? = null,
  var file: File? = null,
  var fileSize: Long? = null,
  var downloadedBytes: Long? = null,
  var lastModified: String? = null,
  var progress: Int = 0,
  var state: LDDownloadState? = LDDownloadState.Empty,
  var feedback: String? = null,
  var title: String? = null,
  var workId: UUID? = null,
  var params: MutableMap<String, String>? = null,
)
```

## LDDownloadState

To handle LookDown file progress state.

```kotlin
sealed class LDDownloadState{
  object Empty : LDDownloadState()
  object Queued : LDDownloadState()
  object Downloading : LDDownloadState()
  object Paused : LDDownloadState()
  object Incomplete : LDDownloadState()
  object Downloaded : LDDownloadState()
  data class Error(val message: String) : LDDownloadState()
}
```

# Download

Pass LDDownload object or all the necessary parameters to download a file.

## Option 1 (with parameters)

```kotlin
lookDown.download(url= url,filename= filename)  //necessary parameters
lookDown.download(url= url,filename= filename, fileExtension= extension, resume = true, title="Take A Tour") 
```

## Option 2 (with LDDownload)

```kotlin
val ldDownload = LDDownload(id= i, url = takeatour, filename = "Take a Tour ${i+1}", fileExtension = ".mp4", title = "Filename ${i+1}")
lookDown.download(ldDownload) 
```

## Update LDDownload

Control UI events by updating LDDownload object with:

```kotlin
lookDown.updateLDDownload(ldDownload, forceUpdate = true)
```
By setting forceUpdate to false, it will update the LiveData only if the elapsed time has passed the "setProgressRenderDelay" value.


# Download as Service

## Download

Download as Service uses WorkManager + Coroutine to allow the download even when the app is not running.

Set notificationId to null to let LookDown counter handle it

For NotificationImportance use the same as NotificationManager values:
MIN = 1 / LOW = 2 / DEFAULT =3 / HIGH = 4

```kotlin
val workId = lookDown.downloadAsService(ldDownload, notificationId, notificationImportance = 4)
```
Important: downloadAsService returns the WorkManagerId (UUID) in order to observe worker progress. Use lookDown.getWorkInfoByLiveData().

## Observe Service

```kotlin
lookDown.getWorkInfoByLiveData(workId).observe(lifecycleOwner){ workInfo ->
//handle workInfo
}
```

## Cancel Download Service
```kotlin
lookDown.cancelDownloadService(workId)
```

## Current Works

Get WorkInfo list with the current downloads that are running as a service.
```kotlin
lookDown.getCurrentWorks()
```


# File Management

LookDown uses Fileman library under the hood:

### Get File

```kotlin
  fun getFile(filename: String, fileExtension: String = this.fileExtension, driver: Int? = this.driver , folder: String? = this.folder): File? {
    return Fileman.getFile(context, driver?:this.driver, folder ?:this.folder, filename + fileExtension)
  }
```

### Delete File

```kotlin
  fun deleteFile(filename: String, fileExtension: String=this.fileExtension, driver: Int = this.driver , folder: String = this.folder, context: Context?=null): Boolean {
    return Fileman.deleteFile(context ?: this.context, driver, folder, filename + fileExtension)
  }
```
