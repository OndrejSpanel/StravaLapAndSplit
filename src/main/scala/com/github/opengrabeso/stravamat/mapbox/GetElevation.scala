package com.github.opengrabeso.stravamat
package mapbox


object GetElevation {
  /* ScalaFromJS: 2017-10-23 09:41:46.284*/

  def apply(lon: Double, lat: Double): Double = {
    val tf = TileBelt.pointToTileFraction(lon, lat, 20)
    val tile = tf.map(Math.floor(_).toInt)
    val domain = "https://api.mapbox.com/v4/"
    val source = s"""mapbox.terrain-rgb/${tile(2)}/${tile(0)}/${tile(1)}.pngraw"""

    import javax.imageio.ImageIO
    // request
    val pars = Map("access_token" -> Main.secret.mapboxToken)

    val request = RequestUtils.buildGetRequest(domain + source, pars)

    val response = request.execute().getContent

    // load PNG
    val image = ImageIO.read(response)

    val xp = tf(0) - tile(0)
    val yp = tf(1) - tile(1)
    val x = Math.floor(xp * image.getWidth).toInt
    val y = Math.floor(yp * image.getHeight).toInt

    val rgb = image.getRGB(x, y)

    val R = (rgb >> 16) & 0xff
    val G = (rgb >> 8) & 0xff
    val B = (rgb >> 0) & 0xff
    val height = -10000 + (R * 256 * 256 + G * 256 + B) * 0.1

    height
  }
}


