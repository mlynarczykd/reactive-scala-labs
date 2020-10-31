package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps

import scala.concurrent.duration._
import EShop.lab3.TypedOrderManager

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, StartCheckout) => selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, SelectDeliveryMethod(_)) => selectingPaymentMethod(timer)
    case (_, ExpireCheckout) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, SelectPayment(_)) => timer.cancel()
      processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
    case (_, ExpireCheckout) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, ConfirmPaymentReceived) => timer.cancel()
      closed
    case (_, ExpirePayment) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.stopped
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.stopped
  }

}
