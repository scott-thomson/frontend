package controllers

import play.api.mvc.{ Action, Controller }
import play.api.libs.json._

// For calling web services asynchronously
import play.api.libs.ws._
import scala.concurrent.Future

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

  def submitClaim() = Action.async { implicit request =>

        val xmlString = request.body.asFormUrlEncoded.get("claimXml")(0)
        
    //    val dateString = request.body.asFormUrlEncoded.get("claimDate")(0)
    //    val xml = scala.xml.XML.lo(xmlString)
    //
    //    val world = World(new TestNinoToCis)
    //    val situation = CarersXmlSituation(world, xml)    
    //    
    //    val timeLine = Carers.findTimeLine(situation)
    //    val simplifiedTimeLine = Carers.simplifyTimeLine(timeLine)
    //    
    //    // filter time line to 3 years from start date
    //    val filteredTimeLine = simplifiedTimeLine.filter(_ match { case Carers.SimplifiedTimelineItem(d, ok, r) => d.isBefore(situation.claimStartDate().plusYears(3)) })

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    val form = Map("custxml" -> Seq(xmlString), "claimDate" -> Seq("2010-07-05"))

    val futureResult: Future[Response] = WS.url("http://atos-core.pcfapps.vsel-canopy.com/json").post(form)

    futureResult.map { response =>
      {
        println(response.body)
        Ok("Got back:" + (response.json).as[String] )
      }
    }.recover {
      case e: Exception =>
        val exceptionData = Map("error" -> Seq(e.getMessage))
        Ok("Unable to process " + e.getMessage())
      case _ => Ok("Unknown Exception")
      }

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


