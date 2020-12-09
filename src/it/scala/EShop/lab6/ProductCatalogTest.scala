package EShop.lab6

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class ProductCatalogTest extends Simulation {

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrls("http://localhost:9001", "http://localhost:9002", "http://localhost:9003")
    //.acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn: ScenarioBuilder = scenario("SearchSimulation")
    .feed(ssv(classOf[ProductCatalogTest].getResource("/data/search.csv").getPath.substring(1)).random)
    .exec(
      http("search")
        .get("/product?brand=${brand}&keywords=${keywords}")
    )
    .pause(5)

  setUp(
    scn.inject(
      incrementUsersPerSec(100)
        .times(5)
        .eachLevelLasting(30.seconds)
        .separatedByRampsLasting(5.seconds) // optional
        .startingFrom(10) // users per sec too!
      //rampUsers(20).during(1.minutes)
    )
  ).protocols(httpProtocol)
}