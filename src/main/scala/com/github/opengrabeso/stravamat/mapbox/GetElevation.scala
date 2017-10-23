package com.github.opengrabeso.stravamat.mapbox

object GetElevation {
  /* ScalaFromJS: 2017-10-23 09:41:46.284*/

  def apply(lon: Double, lat: Double, cb: (Unit, Unit) => Any) = {
    val tf = TileBelt.pointToTileFraction(lon, lat, 20)
    val tile = tf.map(Math.floor)
    val domain = "https://api.mapbox.com/v4/"
    val source = """mapbox.terrain-rgb///.pngraw"""
    val url = """?access_token="""
    getPixels(url, (err, pixels) => {
      if (err) return cb(err)
      val xp = tf(0) - tile(0)
      val yp = tf(1) - tile(1)
      val x = Math.floor(xp * pixels.shape(0))
      val y = Math.floor(yp * pixels.shape(1))
      val R = pixels.get(x, y, 0)
      val G = pixels.get(x, y, 1)
      val B = pixels.get(x, y, 2)
      val height = -10000 + (R * 256 * 256 + G * 256 + B) * 0.1
      cb(null, height)
    })
  }
}


