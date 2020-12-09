package EShop.lab6

import EShop.lab5.{ProductCatalog, ProductCatalogJsonSupport, SearchService}
import akka.actor.ActorSystem
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success, Try}

object ProductCatalogServerInCluster {
  val host           = "localhost"
  val statsActorPath = "akka.tcp://ClusterCatalogRouters@127.0.0.1:2555/user/StatsActor"
}

class ProductCatalogServerInCluster(node: String) extends ProductCatalogJsonSupport {
  import ProductCatalogServerInCluster._

  private val config = ConfigFactory.load()
  private implicit val system: ActorSystem = ActorSystem(
    "ClusterCatalogRouters",
    config
      .getConfig(node)
      .withFallback(config.getConfig("cluster-default"))
  )
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private implicit val timeout: Timeout                           = 5.seconds
  private val logger                                              = system.log

  private val productCatalogs = system.actorOf(
    ClusterRouterPool(
      RoundRobinPool(0),
      ClusterRouterPoolSettings(totalInstances = 100, maxInstancesPerNode = 3, allowLocalRoutees = true)
    ).props(ProductCatalog.props(new SearchService())),
    name = "clusterCatalogRouter"
  )

  private val statsActor = system.actorSelection(statsActorPath)

  private lazy val routes =
    concat(
      path("stats") {
        get {
          val future = statsActor ? StatsActor.GetStats
          onSuccess(future) {
            case stats: Map[String, Int] => complete(stats)
          }
        }
      },
      pathPrefix("product") {
        parameters('brand, 'keywords) { (brand, keywords) =>
          get {
            val keywordsList = keywords.split(",").toList
            val future       = productCatalogs ? ProductCatalog.GetItems(brand, keywordsList)
            onSuccess(future) {
              case items: ProductCatalog.Items => complete(items)
            }
          }
        }
      }
    )

  def run(port: Int): Unit = {
    val binding = Http().newServerAt(host, port).bind(routes)

    binding.onComplete {
      case Success(bound) =>
        logger.info(
          s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/product"
        )
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

object ProductCatalogServerInClusterApp extends App {
  new ProductCatalogServerInCluster(Try(args(1)).getOrElse("cluster-default")).run(args(0).toInt)
}
