package api.common


import upickle.default.{ Reader, Writer, read, write }

import org.apache.pekko.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import org.apache.pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, MediaTypes }
import org.apache.pekko.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }


transparent trait JsonMarshalling:

  given [T: Writer]: ToEntityMarshaller[T] =
    Marshaller.withFixedContentType(ContentTypes.`application/json`): obj =>
      HttpEntity(ContentTypes.`application/json`, write(obj))

  given [T: Reader]: FromEntityUnmarshaller[T] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map(data => read[T](data))
