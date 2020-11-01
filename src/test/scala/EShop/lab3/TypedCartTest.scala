package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect.{Scheduled, Spawned}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._


class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._
  import TypedCartTest._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    testKit.run(AddItem(item1))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(cart1)
  }

  it should "be empty after adding and removing the same item" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()

    testKit.run(AddItem(item1))
    testKit.run(RemoveItem(item1))
    testKit.run(GetItems(inbox.ref))
    inbox.expectMessage(cart2)
  }

  it should "start checkout" in {
    val testKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[TypedOrderManager.Command]()

    testKit.run(AddItem(item1))
    testKit.run(StartCheckout(inbox.ref))

    testKit.expectEffect(Scheduled(5.seconds, testKit.ref, TypedCartActor.ExpireCart))
    testKit.expectEffectType[Spawned[TypedCheckout]]

    val childInbox = testKit.childInbox[TypedCheckout.Command]("CheckoutActor")
    childInbox.expectMessage(TypedCheckout.StartCheckout)
    inbox.expectMessage(_: TypedOrderManager.ConfirmCheckoutStarted)
  }

  it should "add item properly - async" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Any]()

    cartActor ! AddItem(item1)
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(cart1)
  }

  it should "be empty after adding and removing the same item - async" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Any]()

    cartActor ! AddItem(item1)
    cartActor ! RemoveItem(item1)
    cartActor ! GetItems(probe.ref)
    probe.expectMessage(cart2)
  }

  it should "start checkout - async" in {
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Any]()

    cartActor ! AddItem(item1)
    cartActor ! StartCheckout(probe.ref)
    probe.expectMessageType[TypedOrderManager.ConfirmCheckoutStarted]
  }
}

object TypedCartTest {
  val item1 = "Book1"
  val cart1: Cart = Cart.empty.addItem(item1)
  val cart2: Cart = Cart.empty
}