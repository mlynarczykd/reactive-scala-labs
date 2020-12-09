package EShop.lab6

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object CatalogSubNodeApp extends App {

  private val config = ConfigFactory.load()
  private val system = ActorSystem(
    "ClusterCatalogRouters",
    config
      .getConfig("subnode")
      .withFallback(config.getConfig("cluster-default"))
  )

  system.actorOf(Props(new LoggerActor()), "LoggerActor")

  system.actorOf(Props(new StatsActor()), "StatsActor")

  Await.result(system.whenTerminated, Duration.Inf)
}
