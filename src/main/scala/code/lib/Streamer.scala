package code.lib

import net.liftweb.http._
import net.liftweb.http.rest.RestHelper
import net.liftweb.common._
import net.liftweb.util.SecurityHelpers.randomString
import net.liftweb.util.StringHelpers.parseNumber
import scala.collection.mutable
import java.io.InputStream
import java.io.FileInputStream
import net.liftweb.http.StreamingResponse
import scala.Some

/**
 * @author ssb
 *         21/04/14
 */

object Streamer extends RestHelper {
  var videos = mutable.HashMap[String, Video]()

  //private val salt = SecurityHelpers.randomString(6)

  // intersperses the salt within the str then hashes it
  //private def hash(str: String) = SecurityHelpers.hash(str.mkString(salt)).substring(0, 3)

  def loadVideo(v: Video) {
    videos += ((randomString(3), v))
  }

  def has(id: String) = videos.contains(id)

  def title(id: String) = videos.get(id).map(_.title).getOrElse("Video not found")

  private def stream(req: Req, video: Video, filetype: String): Box[(List[(String, String)], InputStream, Long)] = {
    var content_type = ("", "")
    val file =
      if (filetype equals "mkv") {
        content_type = "Content-Type" -> "video/webm"
        video.mkv
      }
      else if (filetype equals "mp4") {
        content_type = "Content-Type" -> "video/mp4"
        video.mp4
      }
      else
        return Empty

    val range = req.header("Range")
    var start = 0L
    var end = 0L

    if (range.isDefined) {
      start = parseNumber(range.get.substring(range.get.indexOf("bytes=") + 6))

      if (range.get.endsWith("-"))
        end = file.length - 1
      else
        end = parseNumber(range.get.substring(range.get.indexOf("-") + 1))
    }
    else
      end = file.length - 1

    end = file.length - 1

    val headers =
      ("Connection" -> "close") ::
        ("Transfer-Encoding" -> "chunked") ::
        content_type ::
        ("Content-Range" -> ("bytes " + start.toString + "-" + end.toString + "/" + file.length.toString)) ::
        Nil

    val fis = new FileInputStream(file)
    fis.skip(start)

    println("Skipped " + start + " bytes")
    Full(headers, fis, end - start + 1)
  }

  serve {
    case req@Req(("mkv" :: id :: Nil), _, _) =>
      () => response(req, id, "mkv")
    case req@Req(("mp4" :: id :: Nil), _, _) =>
      () => response(req, id, "mp4")
  }

  implicit def option2box[T](o: Option[T]): Box[T] =
    o match {
      case Some(t: T) =>
        Full(t)
      case _ =>
        Empty
    }

  private def response(req: Req, id: String, filetype: String): Box[LiftResponse] = {
    val x = for {
      video <- videos.get(id)
      stream <- stream(req, video, filetype)
    } yield {
      val headers = stream._1
      val fis = stream._2
      val size = stream._3

      StreamingResponse(
        data = fis,
        onEnd = fis.close,
        size,
        headers,
        cookies = Nil,
        code = 206)
    }

    if (x.isDefined)
      Full(x.get)
    else
      Full(PlainTextResponse("error"))
  }
}