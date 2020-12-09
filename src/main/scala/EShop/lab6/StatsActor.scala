package EShop.lab6

import EShop.lab5.ProductCatalog
import EShop.lab6.StatsActor.GetStats
import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}

object StatsActor {
  sealed trait Command
  case object GetStats
}

class StatsActor extends Actor with ActorLogging {

  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(ProductCatalog.topic, self)

  override def receive: Receive = {
    case SubscribeAck(Subscribe(ProductCatalog.topic, None, `self`)) =>
      log.info("Subscribed")
      context.become(onMessage(Map.empty))
  }

  private def onMessage(stats: Map[String, Int]): Receive = {
    case ProductCatalog.GotQuery(_, actorRef) =>
      val queryNumber = stats.getOrElse(actorRef, 0) + 1
      val newStats    = stats + (actorRef -> queryNumber)
      context.become(onMessage(newStats))
    case GetStats => sender ! stats
  }
}
