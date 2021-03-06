/*
 * Copyright (C) 2011-2013 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.util

import akka.event.{ LogSource, Logging, LoggingAdapter }
import akka.actor._

/**
 * A LoggingAdapter that can be implicitly supplied from an implicitly available
 * ActorRefFactory (i.e. ActorSystem or ActorContext).
 * Also, it supports optional reformating of ActorPath strings from slash-separated
 * to dot-separated, which opens them up to the hierarchy-based logger configuration
 * of frameworks like logback or log4j.
 */
trait LoggingContext extends LoggingAdapter

object LoggingContext extends LoggingContextLowerOrderImplicit1 {
  implicit def fromAdapter(implicit la: LoggingAdapter) = new LoggingContext {
    def isErrorEnabled = la.isErrorEnabled
    def isWarningEnabled = la.isWarningEnabled
    def isInfoEnabled = la.isInfoEnabled
    def isDebugEnabled = la.isDebugEnabled

    protected def notifyError(message: String): Unit = { la.error(message) }
    protected def notifyError(cause: Throwable, message: String): Unit = { la.error(cause, message) }
    protected def notifyWarning(message: String): Unit = { la.warning(message) }
    protected def notifyInfo(message: String): Unit = { la.info(message) }
    protected def notifyDebug(message: String): Unit = { la.debug(message) }
  }
}

case class SprayNamedLogSource(name: String, source: Any)

object SprayNamedLogSource {
  implicit val fromSprayNamedLogSource: LogSource[SprayNamedLogSource] = new LogSource[SprayNamedLogSource] {
    def genString(s: SprayNamedLogSource) = s.name
    override def getClazz(s: SprayNamedLogSource): Class[_] = s.source.getClass
  }
}

private[util] sealed abstract class LoggingContextLowerOrderImplicit1 extends LoggingContextLowerOrderImplicit2 {
  this: LoggingContext.type ⇒

  import SprayNamedLogSource.fromSprayNamedLogSource

  implicit def fromActorRefFactory(implicit refFactory: ActorRefFactory) =
    refFactory match {
      case x: ActorSystem  ⇒ fromActorSystem(x)
      case x: ActorContext ⇒ fromActorContext(x)
    }

  def fromActorSystem(system: ActorSystem) = fromAdapter(system.log)

  def fromActorContext(context: ActorContext) = fromAdapter {
    val system = context.system
    val actorRef = context.self
    val path = actorRef.path.toString
    val settings = UtilSettings(system)
    val name = if (settings.logActorPathsWithDots) {
      val fixedPath = path.substring(7).replace('/', '.') // drop the `akka://` prefix and replace slashes
      if (settings.logActorSystemName) system.toString + '.' + fixedPath else fixedPath
    } else path
    val logSource = SprayNamedLogSource(name, actorRef)

    if (settings.logActorSystemName) Logging(system, logSource) else Logging(system.eventStream, logSource)
  }
}

private[util] sealed abstract class LoggingContextLowerOrderImplicit2 {
  this: LoggingContext.type ⇒

  implicit val NoLogging = fromAdapter(akka.event.NoLogging)
}

/**
 * Trait that can be mixed into an Actor to easily obtain a reference to a spray-level logger,
 * which is available under the name "log".
 */
trait SprayActorLogging { this: Actor ⇒
  val log: LoggingAdapter = LoggingContext.fromActorContext(context)
}