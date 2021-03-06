package lila.analyse

import org.joda.time.DateTime
import org.scala_tools.time.Imports._
import play.api.libs.json.Json

import lila.db.api._
import lila.db.Implicits._
import tube.analysisTube

private[analyse] object AnalysisRepo {

  type ID = String

  def done(id: ID, a: Analysis) = $update(
    $select(id),
    $set(Json.obj(
      "done" -> true,
      "encoded" -> a.encodeInfos
    )) ++ $unset("fail")
  )

  def fail(id: ID, err: Exception) = $update.field(id, "fail", err.getMessage)

  def progress(id: ID, userId: ID) = $update(
    $select(id),
    $set(Json.obj(
      "uid" -> userId,
      "done" -> false,
      "date" -> $date(DateTime.now)
    )) ++ $unset("fail"),
    upsert = true)

  def doneById(id: ID): Fu[Option[Analysis]] =
    $find.one($select(id) ++ Json.obj("done" -> true))

  def isDone(id: ID): Fu[Boolean] =
    $count.exists($select(id) ++ Json.obj("done" -> true))

  def userInProgress(uid: ID): Fu[Option[String]] = $primitive.one(
    Json.obj(
      "fail" -> $exists(false),
      "uid" -> uid,
      "done" -> false,
      "date" -> $gt($date(DateTime.now - 15.minutes))
    ),
    "_id")(_.asOpt[String])

  def count = $count($select.all)
}
