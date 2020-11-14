package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.Cancellable
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._
  import TypedCheckoutTest._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartActor = testKit.createTestProbe[TypedCartActor.Command]()
    val orderManager = testKit.createTestProbe[TypedOrderManager.Command]()
    val probe = testKit.createTestProbe[String]()

    val checkoutActor = testKit.spawn(new TypedCheckout(cartActor.ref) {
      override def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] =
        Behaviors.setup(_ => {
          val result = super.selectingDelivery(timer)
          probe.ref ! selectingDeliveryMsg
          result
        })

      override def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] =
        Behaviors.setup(_ => {
          probe.ref ! selectingPaymentMethodMsg
          super.selectingPaymentMethod(timer)
        })

      override def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] =
        Behaviors.setup(_ => {
          probe.ref ! processingPaymentMsg
          super.processingPayment(timer)
        })

    }.start )

    checkoutActor ! StartCheckout
    probe.expectMessage(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod("Courier")
    probe.expectMessage(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment("Transfer", orderManager.ref)
    probe.expectMessage(processingPaymentMsg)

    orderManager.expectMessageType[TypedOrderManager.ConfirmPaymentStarted]

    checkoutActor ! ConfirmPaymentReceived

    cartActor.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }

}

object TypedCheckoutTest {
  val selectingDeliveryMsg      = "selectingDelivery"
  val selectingPaymentMethodMsg = "selectingPaymentMethod"
  val processingPaymentMsg      = "processingPayment"
}