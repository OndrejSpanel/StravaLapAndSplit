package com.github.opengrabeso.stravamat
package mapbox

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import scala.collection.mutable

object GetElevation {
  /* ScalaFromJS: 2017-10-23 09:41:46.284*/
  class TileCache {
    val tiles = mutable.Map.empty[(Long, Long, Long), BufferedImage]
    def tileImage(x: Long, y: Long, z: Long): BufferedImage = {

      tiles.getOrElseUpdate((x, y, z), {
        val domain = "https://api.mapbox.com/v4/"
        val source = s"""mapbox.terrain-rgb/$z/$x/$y.pngraw"""

        // request
        val pars = Map("access_token" -> Main.secret.mapboxToken)

        val request = RequestUtils.buildGetRequest(domain + source, pars)

        val response = request.execute().getContent

        // load PNG
        val image = ImageIO.read(response)

        image
      })
    }

    private def imageHeight(image: BufferedImage, x: Int, y: Int): Double = {
      val rgb = image.getRGB(x, y)

      val height = -10000 + (rgb & 0xffffff) * 0.1

      height
    }

    private def tileCoord(lon: Double, lat: Double): (Array[Long], Double, Double) = {
      val tf = TileBelt.pointToTileFraction(lon, lat, 16)
      val tile = tf.map(Math.floor(_).toLong)

      val xp = tf(0) - tile(0)
      val yp = tf(1) - tile(1)
      (tile, xp, yp)
    }

    def apply(lon: Double, lat: Double): Double = {
      // TODO: four point bilinear interpolation
      val (tile, xp, yp) = tileCoord(lon, lat)

      val image = tileImage(tile(0), tile(1), tile(2))

      val x = Math.floor(xp * image.getWidth).toInt
      val y = Math.floor(yp * image.getHeight).toInt

      imageHeight(image, x, y)
    }

    def possibleRange(lon: Double, lat: Double): (Double, Double) = {

      val (tile, xp, yp) = tileCoord(lon, lat)

      val image = tileImage(tile(0), tile(1), tile(2))

      val x = Math.floor(xp * image.getWidth).toInt
      val y = Math.floor(yp * image.getHeight).toInt

      // TODO: handle egde pixels correctly
      val candidates = Seq(imageHeight(image, x, y), imageHeight(image, x + 1 , y), imageHeight(image, x, y + 1), imageHeight(image, x + 1, y + 1))
      (candidates.min, candidates.max)
    }

  }

  def apply(lon: Double, lat: Double, cache: TileCache = new TileCache): Double = {
    cache(lon, lat)
  }
}


