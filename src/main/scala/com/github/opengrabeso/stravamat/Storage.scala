package com.github.opengrabeso.stravamat

import java.io._
import java.net.{URLDecoder, URLEncoder}
import java.nio.channels.Channels

import collection.JavaConverters._
import com.google.appengine.tools.cloudstorage._
import org.apache.commons.io.IOUtils

import scala.reflect.ClassTag

object Storage extends FileStore {


  // from https://cloud.google.com/appengine/docs/java/googlecloudstorageclient/read-write-to-cloud-storage

  final val bucket = "stravamat.appspot.com"

  // full name combined - namespace, filename, user Id
  object FullName {
    def apply(namespace: String, filename: String, userId: String): FullName = {
      // user id needed so that files from different users are not conflicting
      FullName(userId + "/" + namespace + "/" + filename)
    }
    def withMetadata(namespace: String, filename: String, userId: String, metadata: Seq[(String, String)]): FullName = {
      // metadata stored as part of the filename are much quicker to access, filtering is done from them only
      FullName(userId + "/" + namespace + "/" + filename + metadataEncoded(metadata))
    }
  }

  case class FullName(name: String)

  private def fileId(filename: String) = new GcsFilename(bucket, filename)

  private def userFilename(namespace: String, filename: String, userId: String) = FullName.apply(namespace, filename, userId)

  def metadataEncoded(metadata: Seq[(String, String)]): String = {
    if (metadata.nonEmpty) {
      metadata.flatMap(kv => Seq(kv._1, kv._2)).map(URLEncoder.encode(_, "UTF-8")).mkString("//","/","")
    } else {
      ""
    }
  }

  def metadataFromFilename(filename: String): Map[String, String] = {
    val split = filename.split("//")
    if (split.size > 1) {
      val md = split(1).split("/")
      def decode(x: String) = URLDecoder.decode(x, "UTF-8")
      md.grouped(2).map {
        case Array(k, v) => decode(k) -> decode(v)
      }.toMap
    } else {
      Map.empty
    }
  }

  private final val gcsService = GcsServiceFactory.createGcsService(
    new RetryParams.Builder()
    .initialRetryDelayMillis(10)
    .retryMaxAttempts(10)
    .totalRetryPeriodMillis(15000)
    .build()
  )

  def output(filename: FullName, metadata: Seq[(String, String)]): OutputStream = {
    val instance = new GcsFileOptions.Builder()
    for (m <- metadata) {
      instance.addUserMetadata(m._1, m._2)
    }
    val channel = gcsService.createOrReplace(fileId(filename.name), instance.build)
    Channels.newOutputStream(channel)
  }

  def input(filename: FullName): InputStream = {
    val bufferSize = 1 * 1024 * 1024
    val readChannel = gcsService.openPrefetchingReadChannel(fileId(filename.name), 0, bufferSize)

    Channels.newInputStream(readChannel)
  }

  def store(name: FullName, obj: AnyRef, metadata: (String, String)*) = {
    //println(s"store '$filename' - '$userId'")
    val os = output(name, metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj)
    oos.close()
  }

  def store(
    namespace: String, filename: String, userId: String, obj1: AnyRef, obj2: AnyRef,
    metadata: Seq[(String, String)] = Seq.empty, priorityMetaData: Seq[(String, String)] = Seq.empty
  ) = {
    //println(s"store '$filename' - '$userId'")
    val os = output(FullName.withMetadata(namespace, filename, userId, priorityMetaData), metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj1)
    oos.writeObject(obj2)
    oos.close()
    os.close()
  }

  def getFullName(stage: String, filename: String, userId: String): FullName = {
    val prefix = FullName(stage, filename, userId)

    val options = new ListOptions.Builder().setPrefix(prefix.name).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    val matches = for (iCandidate <- list) yield {
      assert(iCandidate.getName.startsWith(prefix.name))
      iCandidate.getName
    }
    // multiple matches possible, because of -1 .. -N variants added
    // select only real matches
    val realMatches = matches.toList.filter { name =>
      name == prefix.name || name.startsWith(prefix.name + "//")
    }

    if (realMatches.size == 1) {
      FullName(realMatches.head)
    } else prefix
  }



  private def readSingleObject[T: ClassTag](ois: ObjectInputStream) = {
    try {
      val read = ois.readObject()
      read match {
        case Main.NoActivity => None
        case r: T => Some(r)
        case null => None
        case any =>
          val classTag = implicitly[ClassTag[T]]
          throw new InvalidClassException(s"Read class ${any.getClass.getName}, expected ${classTag.runtimeClass.getName}")
      }
    } catch {
      case ex: FileNotFoundException =>
        // reading a file which does not exist - return null
        None
    }
  }

  def loadRawName[T : ClassTag](filename: FullName): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    val is = input(filename)
    try {
      val ois = new ObjectInputStream(is)
      readSingleObject[T](ois)
    } finally {
      is.close()
    }

  }

  def load[T : ClassTag](fullName: FullName): Option[T] = {
    object FormatChanged {
      def unapply(arg: Exception): Option[Exception] = arg match {
        case _: java.io.InvalidClassException => Some(arg) // bad serialVersionUID
        case _: ClassNotFoundException => Some(arg) // class / package names changed
        case _: ClassCastException => Some(arg) // class changed (like Joda time -> java.time)
        case _ => None
      }
    }
    try {
      loadRawName(fullName)
    } catch {
      case _: FileNotFoundException =>
        None
      case FormatChanged(x) =>
        println(s"load error ${x.getMessage} - $fullName")
        gcsService.delete(fileId(fullName.name))
        None
      case x: Exception =>
        x.printStackTrace()
        None
    }
  }

  def load2nd[T : ClassTag](fullName: FullName): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    load[AnyRef, T](fullName).map(_._2)
  }

  def load[T1: ClassTag, T2: ClassTag](fullName: FullName): Option[(T1, T2)] = {
    val is = input(fullName)
    try {
      val ois = new ObjectInputStream(is)
      val obj1 = readSingleObject[T1](ois)
      obj1.flatMap { o1 =>
        val obj2 = readSingleObject[T2](ois)
        obj2.map(o2 => (o1, o2))
      }.orElse(None)
    } finally {
      is.close()
    }
  }

  def delete(toDelete: FullName): Boolean = {
    gcsService.delete(fileId(toDelete.name))
  }


  def enumerate(namespace: String, userId: String, filter: Option[String => Boolean] = None): Iterable[(FullName, String)] = {

    def filterByMetadata(name: String, filter: String => Boolean): Option[String] = {
      // filtering can be done only by "priority" (filename) metadata, accessing real metadata is too slow and brings almost no benefit
      Some(name).filter(filter)
    }

    val prefix = userFilename(namespace, "", userId)
    val options = new ListOptions.Builder().setPrefix(prefix.name).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    val actStream = for {
      iCandidate <- list
      iName <- filter.map(f => filterByMetadata(iCandidate.getName, f)).getOrElse(Some(iCandidate.getName))
    } yield {
      assert(iName.startsWith(prefix.name))
      FullName(iName) -> iName.drop(prefix.name.length)
    }
    actStream.toVector  // toVector to avoid debugging streams, we are always traversing all of them anyway
  }

  def enumerateAll(): Iterable[String] = {
    val prefix = ""
    val options = new ListOptions.Builder().setPrefix(prefix).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    for (i <- list) yield {
      assert(i.getName.startsWith(prefix))
      val name = i.getName.drop(prefix.length)
      name
    }
  }


  def check(namespace: String, userId: String, path: String, digest: String): Boolean = {

    val prefix = userFilename(namespace, path, userId)
    val options = new ListOptions.Builder().setPrefix(prefix.name).build()
    val found = gcsService.list(bucket, options).asScala.toIterable.headOption

    // there should be at most one result
    found.flatMap{i =>
      assert(i.getName.startsWith(prefix.name))
      val name = i.getName.drop(prefix.name.length)
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

  def updateMetadata(file: String, metadata: Seq[(String, String)]): Boolean = {
    val gcsFilename = new GcsFilename(bucket, file)
    val md = gcsService.getMetadata(gcsFilename)
    val userData = md.getOptions.getUserMetadata.asScala
    val matching = metadata.forall { case (key, name) =>
      userData.get(key).contains(name)
    }
    if (!matching) {
      val builder = new GcsFileOptions.Builder()
      for (m <- userData ++ metadata) {
        builder.addUserMetadata(m._1, m._2)
      }
      gcsService.update(gcsFilename, builder.build())
    }
    !matching
  }

  def move(oldName: String, newName: String) = {
    val gcsFilenameOld = fileId(oldName)
    val gcsFilenameNew = fileId(newName)

    // read metadata
    val in = input(FullName(oldName))

    val md = gcsService.getMetadata(gcsFilenameOld)
    val metadata = if (md != null) md.getOptions.getUserMetadata.asScala.toMap
    else Map.empty

    val instance = new GcsFileOptions.Builder()
    for (m <- metadata) {
      instance.addUserMetadata(m._1, m._2)
    }
    val channel = gcsService.createOrReplace(gcsFilenameNew, instance.build)
    val output = Channels.newOutputStream(channel)
    try {
      IOUtils.copy(in, output)
      gcsService.delete(gcsFilenameOld)
    } finally {
      output.close()
    }

  }

  type FileItem = ListItem

  def listAllItems(): Iterable[FileItem] = {
    val options = new ListOptions.Builder().setRecursive(true).build()
    val list = gcsService.list(bucket, options).asScala.toIterable
    list
  }

  def itemModified(item: FileItem) = Option(item.getLastModified)

  def deleteItem(item: FileItem) = gcsService.delete(new GcsFilename(bucket, item.getName))

  def itemName(item: FileItem): String = item.getName

}
