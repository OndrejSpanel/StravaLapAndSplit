package com.github.opengrabeso.stravalas

import java.io._
import java.nio.channels.Channels

import com.google.appengine.tools.cloudstorage.GcsFileOptions
import com.google.appengine.tools.cloudstorage.GcsFilename
import com.google.appengine.tools.cloudstorage.GcsServiceFactory
import com.google.appengine.tools.cloudstorage.RetryParams

import scala.reflect.ClassTag

object Storage {

  // from https://cloud.google.com/appengine/docs/java/googlecloudstorageclient/read-write-to-cloud-storage

  final val bucket = "stravamat.appspot.com"

  private def fileId(filename: String) = new GcsFilename(bucket, filename)

  // user id needed so that files from different users are not conflicting
  private def userFilename(filename: String, userId: String) = {
    userId + "/" + filename
  }

  private final val gcsService = GcsServiceFactory.createGcsService(
    new RetryParams.Builder()
    .initialRetryDelayMillis(10)
    .retryMaxAttempts(10)
    .totalRetryPeriodMillis(15000)
    .build()
  )

  def output(filename: String): OutputStream = {
    val instance = GcsFileOptions.getDefaultInstance
    val channel = gcsService.createOrReplace(fileId(filename), instance)
    Channels.newOutputStream(channel)
  }

  def input(filename: String): InputStream = {
    val bufferSize = 1 * 1024 * 1024
    val readChannel = gcsService.openPrefetchingReadChannel(fileId(filename), 0, bufferSize)

    Channels.newInputStream(readChannel)
  }

  def store(filename: String, userId: String, obj: AnyRef) = {
    val os = output(userFilename(filename, userId))
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj)
    oos.close()
  }

  def load[T : ClassTag](filename: String, userId: String) = {
    val is = input(userFilename(filename, userId))
    val ois = new ObjectInputStream(is)
    ois.readObject().asInstanceOf[T]
  }


}
