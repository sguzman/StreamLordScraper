package com.github.sguzman.scraper.stream.lord

import java.net.SocketTimeoutException

import io.circe.parser.decode
import io.circe.syntax._
import net.ruippeixotog.scalascraper.browser.{Browser, JsoupBrowser}
import scalaj.http.Http

import scala.collection.mutable
import scala.io.Source
import scala.util.{Failure, Success, Try}

object Init {
  type map = mutable.HashMap[String, String]

  val cache: map =
    Try(decode[map](Source.fromFile("./items.json").getLines.mkString("\n")).right.get) match {
      case Success(v) => v
      case Failure(_) =>
        write("./items.json", "{}")
        mutable.HashMap()
    }

  val httpCache: map =
    Try(decode[map](Source.fromFile("./items.data").getLines.mkString).right.get) match {
      case Success(v) => v
      case Failure(_) =>
        write("./items.data", "{}")
        mutable.HashMap()
    }

  def write(file: String, data: String): Unit = {
    import java.io._
    val pw = new PrintWriter(new File(file))
    pw.write(data)
    pw.close()
  }

  def write(file: String, data: Array[Byte]): Unit = {
    import java.io._
    val pw = new PrintWriter(new File(file))
    pw.write(data.map(_.toChar))
    pw.close()
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      write("./items.json", cache.asJson.spaces4)
      write("./items.data", httpCache.asJson.noSpaces)
    }
  })

  def getItemCache[A](key: String, dec: String => A): A = {
    dec(cache(key))
  }

  def getHttpCache[A](key: String, proc: Browser#DocumentType => String, dec: String => A, newKey: String => Unit = println): A = {
    val doc = JsoupBrowser().parseString(httpCache(key))

    if (!cache.contains(key)) {
      val value = proc(doc)
      cache.put(key, value)
      newKey(value)
    }

    getItemCache(key, dec)
  }

  def get[A](url: String, proc: Browser#DocumentType => String, dec: String => A, newKey: String => Unit = println): A = {
    def retry: String = Try(Http(url).asString) match {
      case Success(v) => v.body
      case Failure(e) => e match {
        case _: SocketTimeoutException => retry
        case _ => throw new Exception(url)
      }
    }
    httpCache.put(url, retry)
    getHttpCache(url, proc, dec)
  }

  def cascade[A](url: String, proc: Browser#DocumentType => String, dec: String => A, newKey: String => Unit = println): A = {
    if (cache.contains(url)) getItemCache(url, dec)
    else if (httpCache.contains(url)) getHttpCache(url, proc, dec)
    else get(url, proc, dec)
  }

  def removeHttp(s: List[String]): Unit = s.foreach(httpCache.remove)
}
