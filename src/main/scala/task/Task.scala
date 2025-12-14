package task


import java.time.Instant
import java.util.UUID

import upickle.default.ReadWriter

import util.Serde.instantReadWriter


/** Represents the current processing state of a task.
  */
enum TaskStatus derives ReadWriter:
  case Pending, Running, Succeeded, Failed


/** A generic task model that includes a typed '''metadata''' and '''payload'''.
  *
  * @param id
  *   A unique identifier for the task. Automatically generated if not provided.
  * @param timestamp
  *   The time the task was created. Defaults to the current time.
  * @param status
  *   The current status of the task (e.g., Pending, Running). Defaults to `Pending`.
  * @param retries
  *   Number of retry attempts remaining. Defaults to 3.
  * @param priority
  *   Optional field indicating task priority for scheduling or execution.
  * @param responseTopic
  *   Kafka topic where the task result will be sent.
  * @param payload
  *   The actual data or command to be processed by the task.
  * @tparam Payload
  *   The type of the payload this task wraps.
  */
case class Task[Payload](
  id: String = UUID.randomUUID().toString,
  timestamp: Instant = Instant.now(),
  status: TaskStatus = TaskStatus.Pending,
  retries: Int = 3,
  priority: Option[Int] = None,
  responseTopic: String,
  payload: Payload,
) derives ReadWriter


case class TaskResult[Result](
  id: String,
  timestamp: Instant,
  status: TaskStatus,
  result: Option[Result],
) derives ReadWriter
