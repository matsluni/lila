package lila.hub
package actorApi

import play.api.libs.json._

case class SendTo(userId: String, message: JsObject)

object SendTo {

  def apply[A: Writes](userId: String, typ: String, data: A): SendTo =
    SendTo(userId, Json.obj("t" -> typ, "d" -> data))
}

case class SendTos[A: Writes](userIds: Set[String], message: A)

package captcha {
  case object AnyCaptcha
  case class GetCaptcha(id: String)
}

package lobby {
  case class TimelineEntry(rendered: String)
}