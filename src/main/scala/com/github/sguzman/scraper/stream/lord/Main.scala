package com.github.sguzman.scraper.stream.lord

import io.circe.parser.decode
import io.circe.syntax._
import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.elementList

object Main {
  def main(args: Array[String]): Unit = {
    val pages = 1 to 31
    val shows = pages.par
      .flatMap{a =>
        val url = s"http://www.streamlord.com/series.php?page=$a"
        def proc(doc: Browser#DocumentType): String = {
          doc.>>(elementList("#movie-grid-wrapper > ul > div > li > a[href]"))
            .map(a => a.attr("href"))
            .asJson.spaces4
        }

        def dec(s: String): List[String] = decode[List[String]](s).right.get

        Init.cascade(url, proc, dec)
      }

    val episodes = shows.flatMap{a =>
      val url = s"http://www.streamlord.com/$a"
      def proc(doc: Browser#DocumentType): String = {
        val items = doc.>?>(elementList("#season-wrapper > div > ul > li > div.content > div.playpic > a[href]"))
        if (items.isEmpty) {
          throw new Exception(a)
        }

        val hrefs = items.get.map(_.attr("href"))
        hrefs.asJson.spaces4
      }

      def dec(s: String): List[String] = decode[List[String]](s).right.get

      Init.cascade(url, proc, dec)
    }
  }
}
