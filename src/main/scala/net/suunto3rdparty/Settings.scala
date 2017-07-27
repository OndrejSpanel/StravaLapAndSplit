package net.suunto3rdparty

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

import moveslink.MovesLinkUploader
import resource._


object Settings {

  @SerialVersionUID(10)
  case class SettingsStorage(questTimeOffset: Int, maxHR: Int)

  private val settingsFile = "suuntoToStrava.cfg"

  // TODO: GAE compatible settings (Cloud storage or cookie)
  private var props: Properties = new Properties()
  /*
  private val file = new File(MovesLinkUploader.getDataFolder, settingsFile)
  for (f <- managed(new FileInputStream(file))) {
    props = new Properties()
    props.load(f)
  }
  */

  var questTimeOffset: Int = props.getProperty("questTimeOffset", "0").toInt
  var maxHR: Int = props.getProperty("maxHR", "240").toInt

  def save(newMaxHR: Option[Int], newQuestTimeOffset: Option[Int]): Unit = {
    newMaxHR.foreach { v =>
      props.setProperty("maxHR", v.toString)
      questTimeOffset = v;
    }
    newQuestTimeOffset.foreach { v =>
      props.setProperty("questTimeOffset", v.toString)
      maxHR = v;
    }
    /*
    for (f <- managed(new FileOutputStream(file))) {
      props.store(f, "SuuntoToStrava configuration")
      Console.println("Settings saved")
    }
    */
  }
}


