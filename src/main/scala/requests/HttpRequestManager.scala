package requests


import scala.concurrent.{ ExecutionContext, Future }

import task.{ Task, TaskManager }
import util.Extensions.getOrThrow
import util.Logging


object HttpRequestManager extends Logging:

  private given ec: ExecutionContext = ExecutionContext.global

  private def sendHttpRequest(
    request: HttpRequest
  ): Future[String] =
    val task = Task(
      responseTopic = "entrant-hub-http-request-results",
      payload = request,
    )
    val taskResultFuture = TaskManager.dispatchTaskAsync[HttpRequest, HttpResponse](task)

    taskResultFuture.map { taskResult =>
      val response = taskResult.result.getOrThrow("task result is empty")
      debug(s"request $request result received response $response")
      if response.isSuccess then
        response.body
      else
        val statusCode = response.statusCode
        throw RuntimeException(s"http response status code is $statusCode")
    }.recoverWith {
      case e =>
        Future.failed(RuntimeException(s"Request failed (request: $request): ${e.getMessage}", e))
    }
  end sendHttpRequest

  def getRequest(
    url: String,
    headers: Option[Map[String, String]] = None,
  ): Future[String] =
    val request =
      HttpRequest(method = HttpRequestMethod.GET, url = url, headers = headers, body = None)
    sendHttpRequest(request)

  def postRequest(
    url: String,
    headers: Option[Map[String, String]] = None,
    body: Option[String] = None,
  ): Future[String] =
    val request =
      HttpRequest(method = HttpRequestMethod.POST, url = url, headers = headers, body = body)
    sendHttpRequest(request)

  def postJsonRequest(
    url: String,
    headers: Option[Map[String, String]] = None,
    jsonBody: ujson.Obj = ujson.Obj(),
  ): Future[String] =
    val forcedHeaders = Map("Content-Type" -> "application/json")
    // Override "Content-Type" field even if it's given, because this is a Json Request.
    val finalHeaders = headers.getOrElse(Map()) ++ forcedHeaders
    val requestBody  = jsonBody.render()
    postRequest(url, headers = Some(finalHeaders), body = Some(requestBody))

end HttpRequestManager
