package EShop.lab5

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, PrettyPrinter, RootJsonFormat}
import akka.pattern.ask
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val printer: PrettyPrinter.type = PrettyPrinter

  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI = json match {
      case JsString(url) => new URI(url)
      case _ => throw new RuntimeException("Parsing exception")
    }
  }

  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat: RootJsonFormat[ProductCatalog.Items] = jsonFormat1(ProductCatalog.Items)
}

private object ProductCatalogServer {
  val host = "localhost"
  val port = 8080
  val productCatalogActorPath = "akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog"
}

class ProductCatalogServer extends ProductCatalogJsonSupport {
  import ProductCatalogServer._

  private implicit val system: ActorSystem = ActorSystem("ProductCatalogServer")
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private implicit val timeout: Timeout = 5.seconds
  private val logger = system.log

  private lazy val productCatalogActor = system.actorSelection(productCatalogActorPath)

  private lazy val routes =
    pathPrefix("product") {
      parameters('brand, 'keywords) { (brand, keywords) =>
        get {
          val keywordsList = keywords.split(",").toList
          val future = productCatalogActor ? ProductCatalog.GetItems(brand, keywordsList)
          onSuccess(future) {
            case items: ProductCatalog.Items => complete(items)
          }
        }
      }
    }

  def run(): Unit = {
    val binding = Http().newServerAt(host, port).bind(routes)

    binding.onComplete {
      case Success(bound) =>
        logger.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/product")
        scala.io.StdIn.readLine
        binding.flatMap(_.unbind).onComplete(_ => system.terminate)
      case Failure(e) =>
        logger.error(s"Server could not start!")
        e.printStackTrace()
        system.terminate()
    }

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogServerApp extends App {
  new ProductCatalogServer().run()
}