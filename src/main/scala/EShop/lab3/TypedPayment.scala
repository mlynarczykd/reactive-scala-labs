package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab3.TypedPayment.DoPayment
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedPayment {

  sealed trait Command
  case object DoPayment extends Command
}

class TypedPayment(
  method: String,
  orderManager: ActorRef[TypedOrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  def start: Behavior[TypedPayment.Command] = Behaviors.receive { (context, message) =>
    message match {
      case DoPayment => orderManager ! TypedOrderManager.ConfirmPaymentReceived
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.same
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }
}
