package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object TypedOrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[TypedPayment.Command])                        extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class TypedOrderManager {

  import TypedOrderManager._

  def start: Behavior[TypedOrderManager.Command] = uninitialized

  def uninitialized: Behavior[TypedOrderManager.Command] = Behaviors.setup { context =>
    context.log.info("Initializing OrderManager ")
    open(context.spawn(new TypedCartActor().start, "CartActor"))
  }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case AddItem(id, sender) => cartActor ! TypedCartActor.AddItem(id)
        sender ! Done
        Behaviors.same
      case RemoveItem(id, sender) => cartActor ! TypedCartActor.RemoveItem(id)
        sender ! Done
        Behaviors.same
      case Buy(sender) => inCheckout(cartActor, sender)
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] =
    Behaviors.setup { context =>
      cartActorRef ! TypedCartActor.StartCheckout(context.self)
      Behaviors.receiveMessage {
        case ConfirmCheckoutStarted(checkoutRef) =>
          senderRef ! Done
          inCheckout(checkoutRef)
        case message => context.log.info(s"Received unknown message: $message")
          Behaviors.same
      }
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
        checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
        checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)
        inPayment(sender)
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case ConfirmPaymentStarted(paymentRef) => senderRef ! Done
        inPayment(paymentRef, senderRef)
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def inPayment(
    paymentActorRef: ActorRef[TypedPayment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case Pay(sender) => paymentActorRef ! TypedPayment.DoPayment
        inPayment(paymentActorRef, sender)
      case ConfirmPaymentReceived => senderRef ! Done
        finished
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def finished: Behavior[TypedOrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case message => context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }
}
