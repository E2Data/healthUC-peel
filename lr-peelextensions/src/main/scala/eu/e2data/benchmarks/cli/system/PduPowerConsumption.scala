package eu.e2data.benchmarks.cli.system

import java.nio.file.{Files, Paths}

import com.samskivert.mustache.Mustache
import org.peelframework.core.beans.experiment.Experiment
import org.peelframework.core.beans.system.Lifespan.Lifespan
import org.peelframework.core.beans.system.System
import org.peelframework.core.config.{Model, SystemConfig}
import org.peelframework.core.util.shell

import scala.collection.JavaConverters._

class PduPowerConsumption(
                             version      : String,
                             configKey    : String,
                             lifespan     : Lifespan,
                             dependencies : Set[System] = Set(),
                             mc           : Mustache.Compiler) extends System("pdu", version, configKey, lifespan, dependencies, mc) {

  def enabled = config.getBoolean("system.pdu.enabled")

  def cluster = config.getString("system.pdu.cluster")

  var pid = 0;

  override def configuration() = SystemConfig(config, {

    List(

    )
  })


  override def beforeRun(run: Experiment.Run[System]): Unit = {
    if (enabled) {
      val masters = config.getStringList("system.pdu.config.masters").asScala.toSet
      val slaves = config.getStringList("system.pdu.config.slaves").asScala.toSet
      val hosts = masters.++(slaves).asJavaCollection

      val utilsPath = config.getString("app.path.utils")
      val logDir = Paths.get(run.home, "logs", name)
      var scriptName = "";
      if (cluster.equals("iccs")) {
        scriptName = "powerScript.py";
      } else if (cluster.equals("kmax")) {
        scriptName = "kmaxPowerScript.py"
      }
      if (scriptName.equals("")) {
        throw new IllegalStateException("Please provide cluster env for PDU monitoring");
      }
      if (!Files.exists(logDir)) {
        Files.createDirectories(logDir)
        logger.info(s"Ensuring log folder '$logDir' exists")
      }
      val nodes = String.join(" ", hosts)
      val processId = (shell !! s""" nohup python $utilsPath/pyscripts/$scriptName $logDir/pdu.log $nodes >/dev/null 2>/dev/null & echo $$! """).trim.toInt
      logger.info(s"Start PDU monitoring for nodes: $nodes with PID: $processId")
      pid = processId
    }
  }

  override def afterRun(run: Experiment.Run[System]): Unit = {
    if (enabled) {
      shell ! s"kill $pid"
      logger.info(s"Stop PDU monitoring process with PID: $pid")
    }
  }

  override def start(): Unit = {
    logger.info("Start System PDU monitoring");
  }

  override def stop(): Unit = {
    logger.info("Stop System PDU monitoring");
  }

  override def isRunning = {
    (shell ! s"""ps -p $pid""") == 0
  }

}