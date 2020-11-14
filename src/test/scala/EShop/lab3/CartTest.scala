package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.pattern.ask
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with AnyFlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import CartActor._
  import CartTest._

  implicit val timeout: Timeout = 1.second

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val actorRef = TestActorRef[CartActor]

    actorRef ! AddItem(item1)
    val cart = (actorRef ? GetItems).mapTo[Cart].futureValue
    cart.size shouldBe 1
    cart.items shouldBe Seq(item1)
  }

  it should "be empty after adding and removing the same item" in {
    val actorRef = TestActorRef[CartActor]

    actorRef ! AddItem(item1)
    actorRef ! RemoveItem(item1)
    val cart = (actorRef ? GetItems).mapTo[Cart].futureValue
    cart.size shouldBe 0
  }

  it should "start checkout" in {
    val actorRef = TestActorRef[CartActor]

    actorRef ! AddItem(item1)
    actorRef ! StartCheckout

    expectMsgType[OrderManager.ConfirmCheckoutStarted]
  }
}

object CartTest {
  val item1 = "Book1"
}
