package controllers

import play.api.mvc.{ Action, Controller }
import play.api.libs.json.Json
import play.api.Routes
import org.cddcore.engine.Engine
import org.junit.runner.RunWith
import org.cddcore.engine.tests.CddJunitRunner
import scala.xml.XML
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Result(value: List[Carers.SimplifiedTimelineItem])

object ClaimController extends Controller {

  def submitClaim() = Action { implicit request =>

    val xmlString = request.body.asFormUrlEncoded.get("claimXml")(0)
    val dateString = request.body.asFormUrlEncoded.get("claimDate")(0)
    val xml = scala.xml.XML.loadString(xmlString)

    val world = World(new TestNinoToCis)
    val situation = CarersXmlSituation(world, xml)    
    
    val timeLine = Carers.findTimeLine(situation)
    val simplifiedTimeLine = Carers.simplifyTimeLine(timeLine)
    
    // filter time line to 3 years from start date
    val filteredTimeLine = simplifiedTimeLine.filter(_ match { case Carers.SimplifiedTimelineItem(d, ok, r) => d.isBefore(situation.claimStartDate().plusYears(3)) })

    Ok(Json.toJson(filteredTimeLine))
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


