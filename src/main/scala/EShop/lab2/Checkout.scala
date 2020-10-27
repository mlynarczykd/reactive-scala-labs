package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef): Props = Props(new Checkout())
}

class Checkout extends Actor {

  import context._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  def receive: Receive = LoggingReceive {
    case StartCheckout => context.become(selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)))
    case message => log.info(s"Received unknown message: $message")
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(_) => context.become(selectingPaymentMethod(timer))
    case ExpireCheckout => context.become(cancelled)
    case CancelCheckout => timer.cancel()
      context.become(cancelled)
    case message => log.info(s"Received unknown message: $message")
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(_) => timer.cancel()
      context.become(processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)))
    case ExpireCheckout => context.become(cancelled)
    case CancelCheckout => timer.cancel()
      context.become(cancelled)
    case message => log.info(s"Received unknown message: $message")
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case ConfirmPaymentReceived => timer.cancel()
      context.become(closed)
    case ExpirePayment => context.become(cancelled)
    case CancelCheckout => timer.cancel()
      context.become(cancelled)
    case message => log.info(s"Received unknown message: $message")
  }

  def cancelled: Receive = LoggingReceive {
    case message => log.info(s"Received unknown message: $message")
      context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case message => log.info(s"Received unknown message: $message")
      context.stop(self)
  }
}
