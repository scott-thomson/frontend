package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.Json
import play.api.Routes

import org.cddcore.engine.Engine
import org.junit.runner.RunWith
import org.cddcore.engine.tests.CddJunitRunner

case class Message(value: String)

object MessageController extends Controller {

  implicit val fooWrites = Json.writes[Message]

  def getMessage = Action {
    Ok(Json.toJson(Message(HelloWorld.engine(2))))
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(Routes.javascriptRouter("jsRoutes")(routes.javascript.MessageController.getMessage)).as(JAVASCRIPT)
  }

}

@RunWith(classOf[CddJunitRunner])
object HelloWorld {

  val engine = Engine[Int, String]().
    useCase("Returns hello from CDD the requested number of times").    scenario(1, "Just once").
    expected("Hello from CDD").code((i: Int) => List.fill(i)("Hello from CDD").mkString(", ")).
    scenario(2,"Twice").
    expected("Hello from CDD, Hello from CDD").
    useCase("Returns hello santa when you ask for 5 times").
    scenario(5,"Santa Test").
    expected("Hello Santa").because((i: Int) => i==5).
    build;

}