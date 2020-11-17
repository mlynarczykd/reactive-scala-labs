package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab3.TypedPayment.{DoPayment, Event, ReceivedPayment}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedPayment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object ReceivedPayment extends Event
}

class TypedPayment(
  method: String,
  orderManager: ActorRef[Event],
  checkout: ActorRef[TypedCheckout.Command]
) {

  def start: Behavior[TypedPayment.Command] = Behaviors.receive { (context, message) =>
    message match {
      case DoPayment => orderManager ! ReceivedPayment
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.same
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }
}
