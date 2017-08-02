package com.github.opengrabeso.stravamat

import java.security.MessageDigest

import org.joda.time.{DateTime => ZonedDateTime}
import Util._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Success
import scala.xml.Elem

object StravamatUploader {

  def enumHandler(since: Option[String]): HttpResponse = {
    println("enum")


    val sinceDate = since.map { s =>
      val v = ZonedDateTime.parse(s)
      v.minusDays(1) // timezone may be wrong, to be sure we are not skipping too much, move one day behind
    }


    val listFiles = MoveslinkFiles.listFiles.toList
    // sort files by timestamp
    val wantedFiles = sinceDate.fold(listFiles)(since => listFiles.filter(MoveslinkFiles.timestampFromName(_).forall(_ > since)))

    val sortedFiles = wantedFiles.sortBy(MoveslinkFiles.timestampFromName)

    val response = <files>
      {sortedFiles.map { file =>
        <file>{file}</file>
      }}
    </files>
    ???
  }


  case class FileInfo(name: String, content: Option[Array[Byte]])

  // typically the same file is checked for digest and then read - avoid reading it twice
  var lastFile = Option.empty[FileInfo]

  def getCached(name: String): Option[Array[Byte]] = {
    lastFile.filter(_.name == name).fold {
      val ret = MoveslinkFiles.get(name)
      lastFile = Some(FileInfo(name, ret))
      ret
    }(_.content)
  }


  def getHandler(path: String): HttpResponse = {
    println(s"Get path $path")
    getCached(path).fold {
      ???
    } { f =>
      // send binary response
      ???
    }
  }

  // TODO: DRY with Main.digest
  private val md = MessageDigest.getInstance("SHA-256")

  def digest(bytes: Array[Byte]): String = {
    val digestBytes = (0:Byte) +: md.digest(bytes) // prepend 0 byte to avoid negative sign
    BigInt(digestBytes).toString(16)
  }

  def digest(str: String): String = digest(str.getBytes)

  def digestHandler(path: String): HttpResponse = {
    println(s"Get digest $path")
    getCached(path).fold {
      val response = <error>
        <message>No such file</message>
        <filename> {path} </filename>
      </error>
      ???
    } { f =>
      val response = <digest>
        <message>File found</message>
        <filename>{path}</filename>
        <value>{digest(f)}</value>
      </digest>
      ???
    }
  }



}
