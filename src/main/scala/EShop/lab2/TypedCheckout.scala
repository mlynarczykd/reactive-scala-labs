package EShop.lab2

import EShop.lab3.TypedPayment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

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
  case class SelectPayment(payment: String, orderManagerCheckoutRef: ActorRef[Event],
                           orderManagerPaymentRef: ActorRef[TypedPayment.Event])                  extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[TypedPayment.Command]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case StartCheckout => selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case SelectDeliveryMethod(_) => selectingPaymentMethod(timer)
      case ExpireCheckout => cancelled
      case CancelCheckout => timer.cancel()
        cancelled
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, SelectPayment(payment, orderManagerCheckoutRef, orderManagerPaymentRef)) => timer.cancel()
      orderManagerCheckoutRef ! PaymentStarted(
        context.spawn(new TypedPayment(payment, orderManagerPaymentRef, context.self).start,
          "PaymentActor"))
      processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
    case (_, ExpireCheckout) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, ConfirmPaymentReceived) => timer.cancel()
      cartActor ! TypedCartActor.ConfirmCheckoutClosed
      closed
    case (_, ExpirePayment) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) => context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

}
