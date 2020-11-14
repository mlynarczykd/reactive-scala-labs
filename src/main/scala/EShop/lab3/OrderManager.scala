package EShop.lab3

import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object OrderManager {

  sealed trait Command
  case class AddItem(id: String)                                               extends Command
  case class RemoveItem(id: String)                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef)                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef)                       extends Command
  case object ConfirmPaymentReceived                                           extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager extends Actor with ActorLogging {

  implicit val timeout: Timeout = 1.second
  implicit val dispatcher: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = uninitialized

  def uninitialized: Receive = {
    log.info("Initializing Order Manager")
    open(context.actorOf(CartActor.props(), "CartActor"))
  }

  def open(cartActor: ActorRef): Receive = LoggingReceive {
    case AddItem(id) => cartActor ! CartActor.AddItem(id)
      sender ! Done
    case RemoveItem(id) => cartActor ! CartActor.RemoveItem(id)
      sender ! Done
    case Buy => context.become(inCheckout(cartActor, sender))
    case message => log.info(s"Received unknown message: $message")
  }

  def inCheckout(cartActorRef: ActorRef, senderRef: ActorRef): Receive = {
//    val checkoutRef = Await.result((cartActorRef ? CartActor.StartCheckout)
//      .mapTo[ConfirmCheckoutStarted]
//      .map(_.checkoutRef), 1.second)
//    sender ! Done
//    inCheckout(checkoutRef)
//    log.debug("InCheckout num 1")
    cartActorRef ! CartActor.StartCheckout
    LoggingReceive {
      case ConfirmCheckoutStarted(checkoutRef) => senderRef ! Done
        context.become(inCheckout(checkoutRef))
      case message => log.info(s"Received unknown message: $message")
    }
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = LoggingReceive {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      context.become(inPayment(sender))
    case message => log.info(s"Received unknown message: $message")
  }

  def inPayment(senderRef: ActorRef): Receive = LoggingReceive {
    case ConfirmPaymentStarted(paymentRef) => senderRef ! Done
      context.become(inPayment(paymentRef, senderRef))
    case message => log.info(s"Received unknown message: $message")
  }

  def inPayment(paymentActorRef: ActorRef, senderRef: ActorRef): Receive = LoggingReceive {
    case Pay => paymentActorRef ! Payment.DoPayment
      context.become(inPayment(paymentActorRef, sender))
    case ConfirmPaymentReceived => senderRef ! Done
      context.become(finished)
    case message => log.info(s"Received unknown message: $message")
  }

  def finished: Receive = LoggingReceive {
    case _ => sender ! "order manager finished job"
  }
}
