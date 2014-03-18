package controllers

import play.api.mvc.{ Action, Controller }
import play.api.libs.json.Json
import play.api.Routes
import org.cddcore.engine.Engine
import org.junit.runner.RunWith
import org.cddcore.engine.tests.CddJunitRunner
import scala.xml.XML

case class Result(value: List[String])

object ClaimController extends Controller {

  implicit val fooWrites = Json.writes[Result]

  def submitClaim() = Action { implicit request =>
    println(request.body)
    val xmlString = request.body.asFormUrlEncoded.get("claimXml")(0)
    val dateString = request.body.asFormUrlEncoded.get("claimDate")(0)
    val xml = scala.xml.XML.loadString(xmlString)
    val world = World(new TestNinoToCis)
    val situation = CarersXmlSituation(world, xml)
    val results = Carers.guardConditions(Xmls.asDate(dateString), situation)
    println("Results size is " + results.size)

    Ok("here is the results:" + results.mkString)
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.ClaimController.submitClaim)).as(JAVASCRIPT)
  }

}

@RunWith(classOf[CddJunitRunner])
object HelloWorld {

  val engine = Engine[Int, List[String]]().
    useCase("Returns hello world the requested number of times").
    scenario(1, "Just once").
    expected(List("Hello World")).
    code((i: Int) => List.fill(i)("Hello World")).
    scenario(2, "Two times").
    expected(List("Hello World", "Hello World")).
    build;

}


