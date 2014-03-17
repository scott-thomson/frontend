package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.Json
import play.api.Routes

import org.cddcore.engine.Engine
import org.junit.runner.RunWith
import org.cddcore.engine.tests.CddJunitRunner

case class Result(value: List[String])

object ClaimController extends Controller {

  implicit val fooWrites = Json.writes[Result]

  def submitClaim(claimXml: Int) = Action {implicit request =>   
    Ok(Json.toJson(Result(HelloWorld.engine(claimXml))))
  }

  def javascriptRoutes = Action { implicit request =>    
    Ok(
        Routes.javascriptRouter("jsRoutes")(
            routes.javascript.ClaimController.submitClaim
        )).as(JAVASCRIPT)
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
        expected(List("Hello World","Hello World")).
    build;

}