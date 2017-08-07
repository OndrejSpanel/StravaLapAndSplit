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
  private def userFilename(namespace: String, filename: String, userId: String) = {
    userId + "/" + namespace + "/" + filename
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

  def store(namespace: String, filename: String, userId: String, obj: AnyRef, metadata: (String, String)*) = {
    //println(s"store '$filename' - '$userId'")
    val os = output(userFilename(namespace, filename, userId), metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj)
    oos.close()
  }

  def store(namespace: String, filename: String, userId: String, obj1: AnyRef, obj2: AnyRef, metadata: (String, String)*) = {
    //println(s"store '$filename' - '$userId'")
    val os = output(userFilename(namespace, filename, userId), metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj1)
    oos.writeObject(obj2)
    oos.close()
  }

  private def readSingleObject[T: ClassTag](is: InputStream) = {
    try {
      val ois = new ObjectInputStream(is)
      val read = ois.readObject()
      read match {
        case r: T => Some(r)
        case _ => None // handles readObject returning null as well
      }
    } catch {
      case ex: FileNotFoundException =>
        // reading a file which does not exist - return null
        None
    }
  }

  def load[T : ClassTag](namespace: String, filename: String, userId: String): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    val is = input(userFilename(namespace, filename, userId))
    try {
      readSingleObject[T](is)
    } finally {
      is.close()
    }
  }

  def load2nd[T : ClassTag](namespace: String, filename: String, userId: String): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    load[AnyRef, T](namespace, filename, userId).map(_._2)
  }

  def load[T1: ClassTag, T2: ClassTag](namespace: String, filename: String, userId: String): Option[(T1, T2)] = {
    val is = input(userFilename(namespace, filename, userId))
    try {
      val obj1 = readSingleObject[T1](is)
      obj1.flatMap { o1 =>
        val obj2 = readSingleObject[T2](is)
        obj2.map(o2 => (o1, o2))
      }.orElse(None)
    } finally {
      is.close()
    }
  }

  def delete(namespace: String, filename: String, userId: String): Boolean = {
    val toDelete = userFilename(namespace, filename, userId)
    gcsService.delete(fileId(toDelete))
  }

  def enumerate(namespace: String, userId: String): Iterable[String] = {
    val prefix = userFilename(namespace, "", userId)
    val options = new ListOptions.Builder().setPrefix(prefix).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    for (i <- list) yield {
      assert(i.getName.startsWith(prefix))
      val name = i.getName.drop(prefix.length)
      val m = try {
        val md = gcsService.getMetadata(new GcsFilename(bucket, i.getName))
        if (md != null) Some(md.getOptions.getUserMetadata)
        else None
      } catch {
        case e: Exception =>
          e.printStackTrace()
          None
      }
      //println(s"enum '$name' - '$userId': md '$m'")
      name
    }
  }

  def check(namespace: String, userId: String, path: String, digest: String): Boolean = {

    val prefix = userFilename(namespace, path, userId)
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
