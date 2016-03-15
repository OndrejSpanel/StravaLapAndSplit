package net.suunto3rdparty

import java.io.File
import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

object Util {

  implicit class ZonedDateTimeOps(val time: ZonedDateTime) extends AnyVal with Ordered[ZonedDateTimeOps] {
    override def compare(that: ZonedDateTimeOps): Int = time.compareTo(that.time)

    def toLog: String = {
      val format = DateTimeFormatter.ofPattern("dd/MM HH:mm")
      format.format(time)
    }

    def toLogShort: String = {
      val format = DateTimeFormatter.ofPattern("HH:mm")
      format.format(time)
    }

    def toFileName: String = {
      val format = DateTimeFormatter.ofPattern("YYYY-MM-dd-HH-mm")
      format.format(time)
    }
  }

  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = new Ordering[ZonedDateTime] {
    override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
  }

  def timeToUTC(dateTime: ZonedDateTime) = {
    ZonedDateTime.ofInstant(dateTime.toInstant, ZoneOffset.UTC)
  }

  def kiloCaloriesFromKilojoules(kj: Double): Int = (kj / 4184).toInt

  def isWindows: Boolean = {
    val OS = System.getProperty("os.name").toLowerCase
    OS.contains("win")
  }
  def isMac: Boolean = {
    val OS = System.getProperty("os.name").toLowerCase
    OS.contains("mac")
  }
  def isUnix: Boolean = {
    val OS = System.getProperty("os.name").toLowerCase
    OS.contains("nix") || OS.contains("nux") || OS.contains("aix")
  }
  def getSuuntoHome: File = {
    if (Util.isWindows) {
      val appData = System.getenv("APPDATA")
      return new File(new File(appData), "Suunto")
    }
    if (Util.isMac) {
      val userHome = System.getProperty("user.home")
      return new File(new File(userHome), "Library/Application Support/Suunto/")
    }
    if (Util.isUnix) {
      val userHome = System.getProperty("user.home")
      return new File(new File(userHome), "Suunto")
    }
    throw new UnsupportedOperationException("Unknown operating system")
  }
}
