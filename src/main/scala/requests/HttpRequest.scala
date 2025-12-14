package requests

import upickle.default.ReadWriter


enum HttpRequestMethod derives ReadWriter:
  case GET, POST, PUT, DELETE


case class HttpRequest(
  method: HttpRequestMethod,
  url: String,
  headers: Option[Map[String, String]] = None,
  body: Option[String] = None,
) derives ReadWriter:

  /** The default `toString` implementation does not add a space after the URL, making it harder to
    * click directly. This override ensures proper formatting for a clickable output.
    *
    * @return
    *   A string representation of the HttpRequest instance.
    */
  override def toString: String =
    s"HttpRequest(method: $method | url: $url | headers: $headers | body: $body )"

end HttpRequest


case class HttpResponse(
  statusCode: Int,
  headers: Option[Map[String, String]] = None,
  body: String,
) derives ReadWriter:

  // Status code categories
  /** True if status is in the 2xx range (successful) */
  def isSuccess: Boolean = statusCode >= 200 && statusCode < 300

  /** True if status is in the 3xx range (redirection) */
  def isRedirect: Boolean = statusCode >= 300 && statusCode < 400

  /** True if status is in the 4xx or 5xx range */
  def isError: Boolean = statusCode >= 400

  /** True if status is in the 4xx range (client error) */
  def isClientError: Boolean = statusCode >= 400 && statusCode < 500

  /** True if status is in the 5xx range (server error) */
  def isServerError: Boolean = statusCode >= 500 && statusCode < 600

end HttpResponse
