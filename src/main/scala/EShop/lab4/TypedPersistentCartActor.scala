package EShop.lab4

import java.time.Instant
import java.time.temporal.ChronoUnit

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.TypedOrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class TypedPersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command], duration: FiniteDuration): Cancellable =
    context.scheduleOnce(duration, context.self, ExpireCart)

  private def getDuration(startTime: Instant, now: Instant): FiniteDuration =
    cartTimerDuration - ChronoUnit.MILLIS.between(startTime, now).milliseconds

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    ).receiveSignal {
      case (state, PostStop) =>
        state.timerOpt.foreach(_.cancel)
    }
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item, Some(Instant.now())))
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item)                            => Effect.persist(ItemAdded(item, None))
          case RemoveItem(item) if !cart.contains(item) => Effect.none
          case RemoveItem(_) if cart.size == 1          => Effect.persist(CartEmptied)
          case RemoveItem(item)                         => Effect.persist(ItemRemoved(item))
          case StartCheckout(orderManagerRef) =>
            val checkoutActor = context.spawn(new TypedCheckout(context.self).start, "CheckoutActor")
            Effect.persist(CheckoutStarted(checkoutActor)).thenRun { _ =>
              checkoutActor ! TypedCheckout.StartCheckout
              orderManagerRef ! TypedOrderManager.ConfirmCheckoutStarted(checkoutActor)
            }
          case ExpireCart => Effect.persist(CartExpired)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }

      case InCheckout(cart) =>
        command match {
          case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled(Instant.now()))
          case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case message =>
            context.log.info(s"Received unknown message: $message")
            Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    val cart                                        = state.cart
    lazy val now                                    = Instant.now()
    lazy val timer                                  = state.timerOpt.get
    def startTimer(startTime: Instant): Cancellable = scheduleTimer(context, getDuration(startTime, now))
    lazy val stopTimer: Unit                        = state.timerOpt.foreach(_.cancel)

    event match {
      case CheckoutStarted(_) => stopTimer
        InCheckout(cart)
      case ItemAdded(item, Some(startTime)) => NonEmpty(cart.addItem(item), startTimer(startTime))
      case ItemAdded(item, _)               => NonEmpty(cart.addItem(item), timer)
      case ItemRemoved(item)                => NonEmpty(cart.removeItem(item), timer)
      case CartEmptied | CartExpired =>
        stopTimer
        Empty
      case CheckoutClosed               => Empty
      case CheckoutCancelled(startTime) => NonEmpty(cart, startTimer(startTime))
    }
  }

}
