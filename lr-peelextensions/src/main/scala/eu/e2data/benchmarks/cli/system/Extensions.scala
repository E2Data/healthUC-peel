package eu.e2data.benchmarks.cli.system

import com.samskivert.mustache.Mustache
import org.peelframework.core.beans.system.Lifespan
import org.springframework.context.{ApplicationContext, ApplicationContextAware}
import org.springframework.context.annotation.{Bean, Configuration}

//@Configuration
class Extensions extends ApplicationContextAware {

  var ctx: ApplicationContext = null

  def setApplicationContext(ctx: ApplicationContext): Unit = {
    this.ctx = ctx
  }

  @Bean(name = Array("pdu-0.0.1"))
  def `pdu-0.0.1`: IccsPowerConsumption = new IccsPowerConsumption(
    version      = "0.0.1",
    configKey    = "pdu",
    lifespan     = Lifespan.RUN,
    mc           = ctx.getBean(classOf[Mustache.Compiler])
  )

}
