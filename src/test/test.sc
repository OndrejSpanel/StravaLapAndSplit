object SimpleApp {
  def app = new {
    val a = 0
    val b = 1
  }

  def logApp: Unit = {
    println(app.a)
    app.b
  }

  logApp
}