package fr.xebia.routes

import akka.http.javadsl.model.headers.ContentDisposition
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.ContentDispositionTypes.attachment
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.settings.RoutingSettings
import fr.xebia.model.{Category, Model, S3Model}
import spray.json.DefaultJsonProtocol

class VersionRoutes(implicit val s3Client: S3Model, val routingSettings: RoutingSettings)
  extends SprayJsonSupport with DefaultJsonProtocol {

  val notFound: Route = complete(StatusCodes.NotFound)
  val badRequest: Route = complete(StatusCodes.BadRequest)

  def myUserPassAuthenticator(credentials: Credentials): Option[String] =
    credentials match {
      case p@Credentials.Provided(id) if p.verify("p4ssw0rd") => Some(id)
      case _ => None
    }

  val securedRoute =
    Route.seal {
      authenticateBasic(realm = "secure site", myUserPassAuthenticator) { userName =>
        baseRoute
      }
    }

  val baseRoute: Route =
    redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
      path("versions") {
        get {
          complete(Model.listVersions())
        }
      } ~
        path("versions" / Segment / "data") { modelVersion =>
          get {
            findModel(modelVersion).map(model => {
              complete(model)
            }).getOrElse(notFound)
          }
        } ~
        path("versions" / Segment / "labels") { modelVersion =>
          parameters("category") { category =>
            optionalHeaderValueByName("accept") { headerValue =>
              val model = findModel(modelVersion)
              headerValue match {
                case Some(MediaTypes.`application/octet-stream`.value) =>
                  model
                    .flatMap((model: Model) => Model.labelFile(model, category))
                    .map(file => {
                      respondWithHeader(`Content-Disposition`(attachment, Map("filename" -> s"labels_$category.txt"))) {
                        getFromFile(file, ContentTypes.`application/octet-stream`)
                      }
                    }).getOrElse(notFound)
                case _ =>
                  model
                    .flatMap(Model.listLabels(_, category))
                    .map(complete(_))
                    .getOrElse(notFound)
              }
            }
          }
        } ~
        path("versions" / Segment / "categories") { modelVersion =>
          get {
            findModel(modelVersion).map(model => {
              complete(model.categories)
            }).getOrElse(notFound)
          }
        } ~
        path("versions" / Segment / "categories" / Segment) {
          (modelVersion, categoryId) => {
            get {
              findModel(modelVersion)
                .flatMap(model => {
                  findCategory(model, categoryId)
                })
                .map(category => {
                  complete(category)
                })
                .getOrElse(notFound)
            }
          }
        } ~
        path("versions" / Segment / "model") { modelVersion =>
          get {
            findModel(modelVersion)
              .map((model: Model) => {
                val modelFile = Model.toFile(model)
                respondWithHeader(`Content-Disposition`(attachment, Map("filename" -> "model.pb"))) {
                  getFromFile(
                    modelFile,
                    ContentType(
                      MediaType.applicationBinary("octet-stream", MediaType.NotCompressible)
                    )
                  )
                }
              }).getOrElse(notFound)
          }
        } ~
        get {
          badRequest
        }
    }

  private def jsonCategory(modelVersion: String) = {
    parameters("category") { category =>
      get {
        findModel(modelVersion)
          .flatMap(Model.listLabels(_, category))
          .map(complete(_))
          .getOrElse(notFound)
      }
    }
  }

  def findModel(modelVersion: String): Option[Model] = {
    Model(modelVersion)
  }

  def findCategory(model: Model, categoryId: String): Option[Category] = {
    model.categories.find(_.name == categoryId)
  }

}
