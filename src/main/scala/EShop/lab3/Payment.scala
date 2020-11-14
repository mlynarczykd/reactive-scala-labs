package EShop.lab3

import EShop.lab2.Checkout
import EShop.lab3.Payment.DoPayment
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive

object Payment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object PaymentConfirmed extends Event

  def props(method: String, orderManager: ActorRef, checkout: ActorRef): Props =
    Props(new Payment(method, orderManager, checkout))
}

class Payment(
  method: String,
  orderManager: ActorRef,
  checkout: ActorRef
) extends Actor with ActorLogging {

  override def receive: Receive  = LoggingReceive {
    case DoPayment => orderManager ! OrderManager.ConfirmPaymentReceived
      checkout ! Checkout.ConfirmPaymentReceived
    case message => log.info(s"Received unknown message: $message")
  }

}
