import util.Logging


object App extends Logging:

  def main(args: Array[String]): Unit =
    try
//      core.Boot.initAllSchemas()
//      core.Boot.initData()
//      core.Boot.startAllBackgroundJobs()
      api.HttpServer.start()
      info("Application started successfully")
    catch
      case ex: Throwable =>
        error("Failed to start application", ex)
        sys.exit(1)

end App
