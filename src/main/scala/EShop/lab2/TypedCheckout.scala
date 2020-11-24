package EShop.lab2

import java.time.Instant
import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.implicits.catsSyntaxOptionId

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[TypedOrderManager.Command]) extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckoutClosed                                                             extends Event
  case class PaymentStarted(payment: ActorRef[TypedPayment.Command], startTime: Instant) extends Event
  case class CheckoutStarted(startTime: Instant)                                         extends Event
  case object CheckoutCancelled                                                          extends Event
  case class DeliveryMethodSelected(method: String)                                      extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(timer.some)
  case class SelectingPaymentMethod(timer: Cancellable) extends State(timer.some)
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(timer.some)
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
      case message =>
        context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case SelectDeliveryMethod(_) => selectingPaymentMethod(timer)
      case ExpireCheckout          => cancelled
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case message =>
        context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, SelectPayment(payment, orderManagerRef)) =>
      timer.cancel()
      orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(
        context.spawn(new TypedPayment(payment, orderManagerRef, context.self).start, "PaymentActor")
      )
      processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
    case (_, ExpireCheckout) => cancelled
    case (_, CancelCheckout) =>
      timer.cancel()
      cancelled
    case (context, message) =>
      context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, ConfirmPaymentReceived) =>
      timer.cancel()
      cartActor ! TypedCartActor.ConfirmCheckoutClosed
      closed
    case (_, ExpirePayment) => cancelled
    case (_, CancelCheckout) =>
      timer.cancel()
      cancelled
    case (context, message) =>
      context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) =>
      context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, message) =>
      context.log.info(s"Received unknown message: $message")
      Behaviors.same
  }

}
