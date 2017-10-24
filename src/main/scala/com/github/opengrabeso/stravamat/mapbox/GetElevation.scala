package com.github.opengrabeso.stravamat
package mapbox

import java.util.concurrent.ThreadFactory

import com.google.code.appengine.awt.image.BufferedImage
import com.google.code.appengine.imageio.ImageIO

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}

object GetElevation {

  object DefaultThreadFactory {
    implicit object SimpleThreadFactory extends ThreadFactory {
      def newThread(runnable: Runnable) = new Thread(runnable)
    }

  }

  class TileCache {
    val tiles = mutable.Map.empty[(Long, Long, Long), BufferedImage]

    def tileImage(x: Long, y: Long, z: Long)(implicit threadFactory: ThreadFactory): BufferedImage = {

      tiles.getOrElseUpdate((x, y, z), {
        val timing = shared.Timing.start()
        val domain = "https://api.mapbox.com/v4/"
        val source = s"""mapbox.terrain-rgb/$z/$x/$y.pngraw"""

        // request
        val pars = Map("access_token" -> Main.secret.mapboxToken)

        val request = RequestUtils.buildGetRequest(domain + source, pars)

        val promise = Promise[BufferedImage]

        val runnable = new Runnable {
          def run() = {
            val response = request.execute().getContent
            // load PNG
            promise success ImageIO.read(response)
          }
        }

        threadFactory.newThread(runnable).start()

        val image = Await.result(promise.future, Duration.Inf)

        timing.logTime(s"Read image $source")

        image
      })
    }

    private def imageHeight(image: BufferedImage, x: Int, y: Int): Double = {
      val rgb = image.getRGB(x, y)

      val height = -10000 + (rgb & 0xffffff) * 0.1

      height
    }

    private def tileCoord(lon: Double, lat: Double): (Array[Long], Double, Double) = {
      // 16 is max. where neighbourghs have a different value
      // in Europe zoom 16 corresponds approx. 1 px ~ 1m
      val zoom = 13
      val tf = TileBelt.pointToTileFraction(lon, lat, zoom)
      val tile = tf.map(Math.floor(_).toLong)

      val xp = tf(0) - tile(0)
      val yp = tf(1) - tile(1)
      (tile, xp, yp)
    }

    def apply(lon: Double, lat: Double)(implicit threadFactory: ThreadFactory): Double = {
      // TODO: four point bilinear interpolation
      val (tile, xp, yp) = tileCoord(lon, lat)

      val image = tileImage(tile(0), tile(1), tile(2))

      val x = Math.floor(xp * image.getWidth).toInt
      val y = Math.floor(yp * image.getHeight).toInt

      imageHeight(image, x, y)
    }

    def possibleRange(lon: Double, lat: Double)(implicit threadFactory: ThreadFactory): (Double, Double) = {

      val (tile, xp, yp) = tileCoord(lon, lat)

      val image = tileImage(tile(0), tile(1), tile(2))

      val x = Math.floor(xp * image.getWidth).toInt
      val y = Math.floor(yp * image.getHeight).toInt

      // TODO: handle edge pixels correctly
      val x1 = (x + 1) min (image.getWidth - 1)
      val y1 = (y + 1) min (image.getHeight - 1)

      val candidates = Seq(imageHeight(image, x, y), imageHeight(image, x1 , y), imageHeight(image, x, y1), imageHeight(image, x1, y1))
      (candidates.min, candidates.max)
    }

  }

  def apply(lon: Double, lat: Double, cache: TileCache = new TileCache)(implicit threadFactory: ThreadFactory): Double = {
    cache(lon, lat)
  }
}


