package eu.e2data.benchmarks.cli.command

import java.lang.{System => Sys}

import anorm.SqlParser._
import anorm._
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.peelframework.core.cli.command.Command
import org.peelframework.core.config.loadConfig
import org.peelframework.core.results.DB
import org.peelframework.core.util.console._
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/** Query the database for the runtimes of a particular experiment. */
@Service("query:runtimes:csv")
class QueryCsvRuntimes extends Command {

  import scala.language.postfixOps

  override val name = "query:runtimes:csv"

  override val help = "query the database for the csv runtimes of a particular experiment."

  override def register(parser: Subparser) = {
    // options
    parser.addArgument("--connection")
      .`type`(classOf[String])
      .dest("app.db.connection")
      .metavar("ID")
      .help("database config name (default: `default`)")
    // arguments
    parser.addArgument("suite")
      .`type`(classOf[String])
      .dest("app.suite.name")
      .metavar("SUITE")
      .help("experiments suite to run")

    // option defaults
    parser.setDefault("app.db.connection", "default")
  }

  override def configure(ns: Namespace) = {
    // set ns options and arguments to system properties
    Sys.setProperty("app.db.connection", ns.getString("app.db.connection"))
    Sys.setProperty("app.suite.name", ns.getString("app.suite.name"))
  }

  override def run(context: ApplicationContext) = {
    logger.info(s"Querying runtime csv results for suite '${Sys.getProperty("app.suite.name")}' from '${Sys.getProperty("app.db.connection")}'")

    // load application configuration
    implicit val config = loadConfig()

    // create database connection
    val connName = Sys.getProperty("app.db.connection")
    implicit val conn = DB.getConnection(connName)

    val suite = config.getString("app.suite.name")

    try {
      // Create an SQL query
      val runtimes = SQL(
          """
          |SELECT   e.name                                  as name        ,
          |         r.run                                   as run         ,
          |         r.time                                  as runtime_ms
          |FROM     experiment                              as e           ,
          |         experiment_run                          as r
          |WHERE    e.id    = r.experiment_id
          |AND      e.suite = {suite}
          |ORDER BY e.suite, e.name
          """.stripMargin.trim
        )
        .on("suite" -> suite)
        .as({
          get[String] ("name")        ~
          get[Int]    ("run")    ~
          get[Long]    ("runtime_ms") map {
            case name ~ run ~ runtime_ms => (name.split("-")(0).replaceAll("[^0-9]", "").replaceFirst("^0+(?!$)", ""),
              name.split("-")(1).replaceFirst("^0+(?!$)", ""),
              run,
              runtime_ms)
          }
        } * )


      System.out.println("Worker,TaskSlots,Run,Runtime_ms")
      for ((worker, taskSlot, run, runtimeMs) <- runtimes) {
        System.out.println("%s,%s,%s,%s".format(worker, taskSlot, run, runtimeMs))
      }
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error while querying runtime csv results for suite '$suite'".red)
        throw e
    } finally {
      logger.info(s"Closing connection to database '$connName'")
      conn.close()
      logger.info("#" * 60)
    }
  }
}
