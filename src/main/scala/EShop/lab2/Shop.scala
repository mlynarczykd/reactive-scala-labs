package EShop.lab2

import EShop.lab2.CartActor._
import EShop.lab2.Checkout._
import akka.actor.{ActorSystem, Props}

object Shop extends App {

  val system = ActorSystem("lab2")
  val cart = system.actorOf(Props[CartActor], "cart")
  val checkout = system.actorOf(Props[Checkout], "checkout")

  cart ! AddItem("Book1")
  cart ! AddItem("Book2")
  cart ! RemoveItem("Book2")
  cart ! CartActor.StartCheckout

  checkout ! Checkout.StartCheckout
  checkout ! SelectDeliveryMethod("Courier")
  checkout ! SelectPayment("Transfer")
  checkout ! ConfirmPaymentReceived

  cart ! ConfirmCheckoutClosed

  system.terminate()

}
