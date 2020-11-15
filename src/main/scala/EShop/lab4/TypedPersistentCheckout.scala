package EShop.lab4

import java.time.Instant
import java.time.temporal.ChronoUnit

import EShop.lab2.TypedCartActor
import EShop.lab3.{TypedOrderManager, TypedPayment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  private def scheduleTimer(context: ActorContext[Command], duration: FiniteDuration, command: Command): Cancellable =
    context.scheduleOnce(duration, context.self, command)

  private def getDuration(startTime: Instant, now: Instant): FiniteDuration =
    timerDuration - ChronoUnit.MILLIS.between(startTime, now).milliseconds

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      ).receiveSignal {
        case (state, PostStop) =>
          state.timerOpt.foreach(_.cancel)
      }
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout => Effect.persist(CheckoutStarted(Instant.now()))
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case SelectingDelivery(_) =>
        command match {
          case SelectDeliveryMethod(method) => Effect.persist(DeliveryMethodSelected(method))
          case ExpireCheckout               => Effect.persist(CheckoutCancelled)
          case CancelCheckout               => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case SelectPayment(payment, orderManagerRef) =>
            val paymentActor =
              context.spawn(new TypedPayment(payment, orderManagerRef, context.self).start, "PaymentActor")
            Effect
              .persist(PaymentStarted(paymentActor, Instant.now()))
              .thenRun(_ => orderManagerRef ! TypedOrderManager.ConfirmPaymentStarted(paymentActor))
          case ExpireCheckout => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case ProcessingPayment(_) =>
        command match {
          case ConfirmPaymentReceived =>
            Effect
              .persist(CheckoutClosed)
              .thenRun(_ => cartActor ! TypedCartActor.ConfirmCheckoutClosed)
          case ExpirePayment  => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case Cancelled =>
        command match {
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case Closed =>
        command match {
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    lazy val now             = Instant.now()
    lazy val stopTimer: Unit = state.timerOpt.foreach(_.cancel)
    lazy val timer           = state.timerOpt.get
    def startTimer(startTime: Instant, command: Command): Cancellable =
      scheduleTimer(context, getDuration(startTime, now), command)

    event match {
      case CheckoutStarted(startTime) => SelectingDelivery(startTimer(startTime, ExpireCheckout))
      case DeliveryMethodSelected(_)  => SelectingPaymentMethod(timer)
      case PaymentStarted(_, startTime) =>
        stopTimer
        ProcessingPayment(startTimer(startTime, ExpirePayment))
      case CheckoutClosed =>
        stopTimer
        Closed
      case CheckoutCancelled =>
        stopTimer
        Cancelled
    }
  }
}
