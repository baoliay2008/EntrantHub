package api


import scala.concurrent.duration.*
import scala.concurrent.{ Await, ExecutionContext }

import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http

import util.{ EnvConfig, Logging }


object HttpServer
    extends Logging:

  private given system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.empty,
    "entrant-hub-http-server",
  )
  private given ExecutionContext = system.executionContext

  def start(): Http.ServerBinding =
    val host   = EnvConfig.getRequired("HTTP_HOST")
    val port   = EnvConfig.getRequired("HTTP_PORT").toInt
    val routes = MainRouter.routes

    val bindingFuture = Http()
      .newServerAt(host, port)
      .bind(routes)

    val binding = Await.result(bindingFuture, 10.seconds) // Block here to ensure server starts
    info(s"Server running at http://$host:$port/")

    // Use Pekko's CoordinatedShutdown instead of manual shutdown hook
    val shutdown = CoordinatedShutdown(system)
    shutdown.addTask(
      CoordinatedShutdown.PhaseServiceUnbind,
      "http-server-unbind",
    ) { () =>
      info("Shutting down HTTP server...")
      binding.unbind().map { _ =>
        info("HTTP server stopped")
        org.apache.pekko.Done
      }
    }

    binding
  end start

end HttpServer
