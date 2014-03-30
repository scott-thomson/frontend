package controllers

import play.api.mvc.{ Action, Controller }
import play.api.libs.json._

// For calling web services asynchronously
import play.api.libs.ws._
import scala.concurrent.Future

import play.api.Routes

import scala.xml.XML
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants

import play.api.libs.json._
import play.api.libs.functional.syntax._

object ClaimController extends Controller {

  def submitClaim() = Action.async { implicit request =>

    val xmlString = request.body.asFormUrlEncoded.get("claimXml")(0)
        
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    val form = Map("custxml" -> Seq(xmlString), "claimDate" -> Seq("2010-07-05"))

    val futureResult: Future[Response] = WS.url("http://carers-root.pcfapps.vsel-canopy.com/json").post(form)

    futureResult.map { response =>
      {
//        println(response.body)
        Ok(response.body)
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


