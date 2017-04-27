package com.github.opengrabeso.stravalas

import java.io.File

object MoveslinkFiles {

  private val suuntoHome = Util.getSuuntoHome

  val moveslinkFolder = "Moveslink"
  val moveslink2Folder = "Moveslink2"

  val getDataFolder: File = new File(suuntoHome, moveslinkFolder)
  val getData2Folder: File = new File(suuntoHome, moveslink2Folder)

  def placeInFolder(folder: String, filename: String): String = folder + "/" + filename

  def listQuestFiles: Set[String] = getDataFolder.list.toSet.filter(name => name.endsWith(".xml") && name.toLowerCase.startsWith("quest_"))

  def listMoveslink2Files: Set[String] = getData2Folder.list.toSet.filter(f => f.endsWith(".sml") || f.endsWith(".xml"))

  def listFiles: Set[String] = listQuestFiles.map(placeInFolder(moveslinkFolder, _)) ++ listQuestFiles.map(placeInFolder(moveslink2Folder, _))
}
