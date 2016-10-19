package com.github.opengrabeso.stravalas

import com.github.opengrabeso.stravalas.Handle.Method
import org.reflections.Reflections
import spark.{Request, Response, Route}
import spark.servlet.SparkApplication
import spark.Spark._

import scala.collection.JavaConverters._

object ServletRouting {
  def route(path: String)(handleFunc: (Request, Response) => AnyRef): Route = {
    new Route(path) {
      override def handle(request: Request, response: Response) = handleFunc(request, response)
    }
  }

}

class ServletRouting extends SparkApplication {

  import ServletRouting._

  def init() {
    // scan annotations, create routes as needed
    val reflections = new Reflections(getClass.getPackage.getName)

    val annotated = reflections.getTypesAnnotatedWith(classOf[Handle]).asScala.toSet

    def addPage(h: HtmlPage, a: Handle) = {
      val r = route(a.value) ((request, response) => h.apply(request))
      a.method match {
        case Method.Get => get(r)
        case Method.Put => put(r)
        case Method.Post => post(r)
        case Method.Delete => delete(r)
      }
    }

    import scala.reflect.runtime.{universe => ru}
    val rm = ru.runtimeMirror(getClass.getClassLoader)

    def getInstance[T](name: String)(implicit tt: ru.TypeTag[T]): T = {
      val moduleSym = rm.staticModule(name).asModule
      if (!(moduleSym.moduleClass.asClass.selfType <:< tt.tpe))
        throw new ClassCastException("Type " + moduleSym.fullName + " not subtype of " + tt.tpe.typeSymbol.fullName)
      val mm = rm.reflectModule(moduleSym.asModule)
      mm.instance.asInstanceOf[T]
    }

    annotated.foreach { t =>
      val a = t.getAnnotation(classOf[Handle])
      try {
        val h = getInstance[HtmlPage](t.getName)
        addPage(h, a)
      } catch {
        case _: ClassCastException =>
          // expected, classes for objects listed as well
      }
    }
  }

}
