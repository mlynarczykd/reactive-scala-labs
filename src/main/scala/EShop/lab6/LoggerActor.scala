package EShop.lab6

import EShop.lab5.ProductCatalog
import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}

class LoggerActor extends Actor with ActorLogging {

  private val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(ProductCatalog.topic, self)

  override def receive: Receive = {
    case query: ProductCatalog.GotQuery                              => log.info(query.toString)
    case SubscribeAck(Subscribe(ProductCatalog.topic, None, `self`)) => log.info("Subscribed")
  }
}
