package com.github.opengrabeso.stravamat.mapbox

object TileBelt {
  /* ScalaFromJS: 2017-10-23 09:41:46.284*/
  // from NPM @mapbox / tilebelt

  val d2r = Math.PI / 180
  val r2d = 180 / Math.PI
  /**
    * Get the bbox of a tile
    *
    * @name tileToBBOX
    * @param {Array<number>} tile
    * @returns {Array<number>} bbox
    * @example
    * var bbox = tileToBBOX([5, 10, 10])
    * //=bbox
    */

  def tileToBBOX(tile: Array[Double]): Array[Double] = {
    val e = tile2lon(tile(0) + 1, tile(2))
    val w = tile2lon(tile(0), tile(2))
    val s = tile2lat(tile(1) + 1, tile(2))
    val n = tile2lat(tile(1), tile(2))
    Array(w, s, e, n)
  }
  /**
    * Get a geojson representation of a tile
    *
    * @name tileToGeoJSON
    * @param {Array<number>} tile
    * @returns {Feature<Polygon>}
    * @example
    * var poly = tileToGeoJSON([5, 10, 10])
    * //=poly
    */

  def tileToGeoJSON(tile: Array[Double]) = {
    val bbox = tileToBBOX(tile)
    object poly {
      var `type` = "Polygon"
      var coordinates = Array(Array(Array(bbox(0), bbox(1)), Array(bbox(0), bbox(3)), Array(bbox(2), bbox(3)), Array(bbox(2), bbox(1)), Array(bbox(0), bbox(1))))
    }
    poly
  }

  def tile2lon(x: Double, z: Double) = {
    x / Math.pow(2, z) * 360 - 180
  }

  def tile2lat(y: Double, z: Double) = {
    val n = Math.PI - 2 * Math.PI * y / Math.pow(2, z)
    r2d * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))
  }
  /**
    * Get the tile for a point at a specified zoom level
    *
    * @name pointToTile
    * @param {number} lon
    * @param {number} lat
    * @param {number} z
    * @returns {Array<number>} tile
    * @example
    * var tile = pointToTile(1, 1, 20)
    * //=tile
    */

  def pointToTile(lon: Double, lat: Double, z: Double) = {
    val tile = pointToTileFraction(lon, lat, z)
    tile(0) = Math.floor(tile(0))
    tile(1) = Math.floor(tile(1))
    tile
  }
  /**
    * Get the 4 tiles one zoom level higher
    *
    * @name getChildren
    * @param {Array<number>} tile
    * @returns {Array<Array<number>>} tiles
    * @example
    * var tiles = getChildren([5, 10, 10])
    * //=tiles
    */

  def getChildren(tile: Array[Double]) = {
    Array(Array(tile(0) * 2, tile(1) * 2, tile(2) + 1), Array(tile(0) * 2 + 1, tile(1) * 2, tile(2) + 1), Array(tile(0) * 2 + 1, tile(1) * 2 + 1, tile(2) + 1), Array(tile(0) * 2, tile(1) * 2 + 1, tile(2) + 1))
  }
  /**
    * Get the tile one zoom level lower
    *
    * @name getParent
    * @param {Array<number>} tile
    * @returns {Array<number>} tile
    * @example
    * var tile = getParent([5, 10, 10])
    * //=tile
    */

  def getParent(tile: Array[Int]) = {
    // top left
    if (tile(0) % 2 == 0 && tile(1) % 2 == 0) {
      return Array(tile(0) / 2, tile(1) / 2, tile(2) - 1)
    }
    // bottom left
    if (tile(0) % 2 == 0 && !tile(1) % 2 == 0) {
      return Array(tile(0) / 2, (tile(1) - 1) / 2, tile(2) - 1)
    }
    // top right
    if (!tile(0) % 2 == 0 && tile(1) % 2 == 0) {
      return Array((tile(0) - 1) / 2, tile(1) / 2, tile(2) - 1)
    }
    // bottom right
    Array((tile(0) - 1) / 2, (tile(1) - 1) / 2, tile(2) - 1)
  }

  def getSiblings(tile: Array[Int]) = {
    getChildren(getParent(tile))
  }
  /**
    * Get the 3 sibling tiles for a tile
    *
    * @name getSiblings
    * @param {Array<number>} tile
    * @returns {Array<Array<number>>} tiles
    * @example
    * var tiles = getSiblings([5, 10, 10])
    * //=tiles
    */

  def hasSiblings(tile: Array[Int], tiles: Array[Unit]): Boolean = {
    val siblings = getSiblings(tile)
    for (i <- 0 until siblings.length) {
      if (!hasTile(tiles, siblings(i))) return false
    }
    true
  }
  /**
    * Check to see if an array of tiles contains a particular tile
    *
    * @name hasTile
    * @param {Array<Array<number>>} tiles
    * @param {Array<number>} tile
    * @returns {boolean}
    * @example
    * var tiles = [
    *     [0, 0, 5],
    *     [0, 1, 5],
    *     [1, 1, 5],
    *     [1, 0, 5]
    * ]
    * hasTile(tiles, [0, 0, 5])
    * //=boolean
    */

  def hasTile(tiles: Array[Unit], tile: Array[Int]) = {
    for (i <- 0 until tiles.length) {
      if (tilesEqual(tiles(i), tile)) return true
    }
    false
  }
  /**
    * Check to see if two tiles are the same
    *
    * @name tilesEqual
    * @param {Array<number>} tile1
    * @param {Array<number>} tile2
    * @returns {boolean}
    * @example
    * tilesEqual([0, 1, 5], [0, 0, 5])
    * //=boolean
    */

  def tilesEqual(tile1: Array[Int], tile2: Array[Int]) = {
    tile1(0) == tile2(0) && tile1(1) == tile2(1) && tile1(2) == tile2(2)
  }
  /**
    * Get the quadkey for a tile
    *
    * @name tileToQuadkey
    * @param {Array<number>} tile
    * @returns {string} quadkey
    * @example
    * var quadkey = tileToQuadkey([0, 1, 5])
    * //=quadkey
    */

  def tileToQuadkey(tile: Array[Int]) = {
    var index = ""
    for (z <- tile(2) until 0 by -1) {
      var b = 0
      val mask = 1 << (z - 1)
      if ((tile(0) & mask) != 0) b += 1
      if ((tile(1) & mask) != 0) b += 2
      index += b.toString()
    }
    index
  }
  /**
    * Get the tile for a quadkey
    *
    * @name quadkeyToTile
    * @param {string} quadkey
    * @returns {Array<number>} tile
    * @example
    * var tile = quadkeyToTile('00001033')
    * //=tile
    */

  def quadkeyToTile(quadkey: Array[Int]) = {
    var x = 0
    var y = 0
    val z = quadkey.length
    for (i <- z until 0 by -1) {
      val mask = 1 << (i - 1)
      val q = +quadkey(z - i)
      if (q == 1) x |= mask
      if (q == 2) y |= mask
      if (q == 3) {
        x |= mask
        y |= mask
      }
    }
    Array(x, y, z)
  }
  /**
    * Get the smallest tile to cover a bbox
    *
    * @name bboxToTile
    * @param {Array<number>} bbox
    * @returns {Array<number>} tile
    * @example
    * var tile = bboxToTile([ -178, 84, -177, 85 ])
    * //=tile
    */

  def bboxToTile(bboxCoords: Array[Double]) = {
    val min = pointToTile(bboxCoords(0), bboxCoords(1), 32)
    val max = pointToTile(bboxCoords(2), bboxCoords(3), 32)
    val bbox = Array(min(0), min(1), max(0), max(1))
    val z = getBboxZoom(bbox)
    if (z == 0) return Array(0, 0, 0)
    val x = bbox(0) >> (32 - z)
    val y = bbox(1) >> (32 - z)
    Array(x, y, z)
  }

  def getBboxZoom(bbox: Array[Double]) = {
    val MAX_ZOOM = 28
    for (z <- 0 until MAX_ZOOM) {
      val mask = 1 << (32 - (z + 1))
      if ((bbox(0) & mask) != (bbox(2) & mask) || (bbox(1) & mask) != (bbox(3) & mask)) {
        return z
      }
    }
    MAX_ZOOM
  }

  def pointToTileFraction(lon: Double, lat: Double, z: Double) = {
    val sin = Math.sin(lat * d2r)
    val z2 = Math.pow(2, z)
    val x = z2 * (lon / 360 + 0.5)
    val y = z2 * (0.5 - 0.25 * Math.log((1 + sin) / (1 - sin)) / Math.PI)
    Array(x, y, z)
  }

}
