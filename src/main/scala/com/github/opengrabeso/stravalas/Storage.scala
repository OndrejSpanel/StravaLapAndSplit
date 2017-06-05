package com.github.opengrabeso.stravalas

import java.io._
import java.nio.channels.Channels

import com.google.appengine.tools.cloudstorage._

import scala.reflect.ClassTag
import collection.JavaConverters._

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

  def output(filename: String, metadata: Seq[(String, String)]): OutputStream = {
    val instance = new GcsFileOptions.Builder()
    for (m <- metadata) {
      instance.addUserMetadata(m._1, m._2)
    }
    val channel = gcsService.createOrReplace(fileId(filename), instance.build)
    Channels.newOutputStream(channel)
  }

  def input(filename: String): InputStream = {
    val bufferSize = 1 * 1024 * 1024
    val readChannel = gcsService.openPrefetchingReadChannel(fileId(filename), 0, bufferSize)

    Channels.newInputStream(readChannel)
  }

  def store(filename: String, userId: String, obj: AnyRef, metadata: (String, String)*) = {
    //println(s"store '$filename' - '$userId'")
    val os = output(userFilename(filename, userId), metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj)
    oos.close()
  }

  def load[T : ClassTag](filename: String, userId: String): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    val is = input(userFilename(filename, userId))
    val ois = new ObjectInputStream(is)
    val read = ois.readObject()
    read match {
      case r: T => Some(r)
      case _ => None// handles readObject returning null as well
    }
  }

  def delete(filename: String, userId: String): Boolean = {
    val instance = new GcsFileOptions.Builder()
    val toDelete = userFilename(filename, userId)
    gcsService.delete(fileId(toDelete))
  }

  def enumerate(userId: String): Iterable[String] = {
    val prefix = userFilename("", userId)
    val options = new ListOptions.Builder().setPrefix(prefix).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    for (i <- list) yield {
      assert(i.getName.startsWith(prefix))
      val name = i.getName.drop(prefix.length)
      val m = try {
        val md = gcsService.getMetadata(new GcsFilename(bucket, i.getName))
        Some(md.getOptions.getUserMetadata)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          None
      }
      //println(s"enum '$name' - '$userId': md '$m'")
      name
    }
  }

  def check(userId: String, path: String, digest: String): Boolean = {

    val prefix = userFilename(path, userId)
    val options = new ListOptions.Builder().setPrefix(prefix).build()
    val found = gcsService.list(bucket, options).asScala.toIterable.headOption

    // there should be at most one result
    found.flatMap{i =>
      assert(i.getName.startsWith(prefix))
      val name = i.getName.drop(prefix.length)
      val m = try {
        val md = gcsService.getMetadata(new GcsFilename(bucket, i.getName))
        val userData = md.getOptions.getUserMetadata.asScala
        userData.get("digest").map(_ == digest)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          None
      }
      //println(s"enum '$name' - '$userId': md '$m'")
      m
    }.contains(true)
  }

}
