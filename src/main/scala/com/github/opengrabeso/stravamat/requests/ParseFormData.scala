package com.github.opengrabeso.stravamat
package requests

import java.io.InputStream

import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import spark.Request

trait FormDataItemReader {
  type Context
  def createContext: Context
  def readItem(ctx: Context, itemName: String, stream: InputStream): Unit
}

trait FormDataItemReaderDummy extends FormDataItemReader {
  type Context = Unit

  def createContext: Context = ()

  def readItem(ctx: Context, itemName: String, stream: InputStream) = {}
}

trait ParseFormDataGen[R] extends FormDataItemReader {
  def inputName: String
  def parse(v: String): R

  def activities(request: Request): (Vector[R], Context) = {
    val fif = new DiskFileItemFactory()
    fif.setSizeThreshold(1 * 1024) // we do not expect any files, only form parts

    val upload = new ServletFileUpload(fif)

    val items = upload.getItemIterator(request.raw)

    val itemsIterator = new Iterator[FileItemStream] {
      def hasNext = items.hasNext

      def next() = items.next
    }

    val ctx = createContext
    val ops = itemsIterator.flatMap { item =>
      if (item.isFormField) {
        // expect field name id={FileId}
        val IdPattern = (s"$inputName=(.*)").r
        // a single field upload-id
        val id = item.getFieldName match {
          case IdPattern(idText) =>
            Some(parse(idText))
          case _ =>
            val stream = item.openStream()
            try {
              readItem(ctx, item.getFieldName, stream)
            } finally {
              stream.close()
            }
            None
        }
        id
      } else {
        None
      }
    }.toVector
    (ops, ctx)
  }
}



trait ParseFormData extends ParseFormDataGen[FileId] with FormDataItemReaderDummy {
  def inputName: String = "id"
  def parse(v: String): FileId = FileId.parse(v)
}
