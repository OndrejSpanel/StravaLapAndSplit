package com.github.opengrabeso.stravalas

import java.io.File

object MoveslinkFiles {

  private val suuntoHome = Util.getSuuntoHome

  private val moveslinkFolder = "Moveslink"
  private val moveslink2Folder = "Moveslink2"

  private val getDataFolder: File = new File(suuntoHome, moveslinkFolder)
  private val getData2Folder: File = new File(suuntoHome, moveslink2Folder)

  private def placeInFolder(folder: String, filename: String): String = folder + "/" + filename

  def listQuestFiles: Set[String] = getDataFolder.list.toSet.filter(name => name.endsWith(".xml") && name.toLowerCase.startsWith("quest_"))

  def listMoveslink2Files: Set[String] = getData2Folder.list.toSet.filter(f => f.endsWith(".sml") || f.endsWith(".xml"))

  def listFiles: Set[String] = listQuestFiles.map(placeInFolder(moveslinkFolder, _)) ++ listMoveslink2Files.map(placeInFolder(moveslink2Folder, _))
}
