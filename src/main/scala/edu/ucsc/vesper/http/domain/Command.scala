package edu.ucsc.vesper.http.domain

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
// the command to request some result
case class Command(
      inspect:      Option[Inspect]     = None,
      remove:       Option[Remove]      = None,
      rename:       Option[Rename]      = None,
      optimize:     Option[Optimize]    = None,
      format:       Option[Format]      = None,
      deduplicate:  Option[Deduplicate] = None,
      cleanup:      Option[Cleanup]     = None,
      publish:      Option[Publish]     = None,
      persist:      Option[Persist]     = None,
      find:         Option[Find]        = None,
      trim:         Option[Trim]        = None
)

object Command extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val requestFormats = jsonFormat11(Command.apply)
}
