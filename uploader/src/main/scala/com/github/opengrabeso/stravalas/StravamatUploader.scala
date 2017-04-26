package com.github.opengrabeso.stravalas

import java.awt.Desktop
import java.io.File
import java.net.URL
import java.util.logging.Logger


object StravamatUploader extends App {

  private val log = Logger.getLogger(getClass.getName)

  private val suuntoHome = Util.getSuuntoHome


  val moveslinkFolder = "Moveslink"
  val moveslink2Folder = "Moveslink2"

  val getDataFolder: File = new File(suuntoHome, moveslinkFolder)
  val getData2Folder: File = new File(suuntoHome, moveslink2Folder)

  def placeInFolder(folder: String, filename: String): String = folder + "/" + filename

  private val uploadedFolderName = "/uploadedToStrava"

  private val uploadedFolder = new File(getDataFolder, uploadedFolderName)

  try {
    uploadedFolder.mkdir()
  } catch {
    case _ : SecurityException => // expected (can already exist)
  }

  def listQuestFiles: Set[String] = getDataFolder.list.toSet.filter(name => name.endsWith(".xml") && name.toLowerCase.startsWith("quest_"))

  def listMoveslink2Files: Set[String] = getData2Folder.list.toSet.filter(f => f.endsWith(".sml") || f.endsWith(".xml"))

  val offer = listQuestFiles.map(placeInFolder(moveslinkFolder, _)) ++ listQuestFiles.map(placeInFolder(moveslink2Folder, _))

  println(offer.mkString("\n"))

  // authenticate with the server
  // open a web browser
  val url = ???

  Desktop.getDesktop.browse(new URL(url).toURI)


}
