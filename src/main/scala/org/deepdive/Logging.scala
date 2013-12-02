package org.deepdive

import org.deepdive.context.Context
import akka.event.{Logging => AkkaLogging}

/* Logs through the default actor system */
trait Logging {
  lazy val log = AkkaLogging.getLogger(Context.system, this)
}