package it.unibo.prolog.model

import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.{Environment, Position}
import it.unibo.prolog._
import org.apache.commons.math3.random.RandomGenerator

import scala.jdk.CollectionConverters.IteratorHasAsScala

final case class PhysicalDevice[T, P <: Position[P]](
    id: Int,
    pos: P,
    env: Environment[T, P],
    random: RandomGenerator,
    kind: Kind = Application,
    appLevel: Boolean = true,
    simulationParameters: SimulationParameters,
) extends Prologable {
  import it.unibo.prolog.model.Constants._

  private val appDeviceMolecule = new SimpleMolecule("applicationDevice")
  private val infraDeviceMolecule = new SimpleMolecule("infrastructuralDevice")

  override val name: String = kind match {
    case Application     => s"robot$id"
    case Infrastructural => s"cloud$id"
  }

  override def toProlog: String = {
    val j = id
    val capabilities = if (appLevel) s"[($S_NAME$j, temperature)], [($A_NAME$j, thermostate)]" else "[], []"
    val totalHw =
      if (appLevel) simulationParameters.availableHwApplication else simulationParameters.availableHwInfrastructural
    val availableHw =
      if (appLevel) simulationParameters.availableHwApplication else simulationParameters.availableHwInfrastructural
    // e.g. energySourceMix(robot2,[(0.1,gas),(0.8,coal),(0.1,onshorewind)])
    val node = env.getNodeByID(id)
    val renewablePercentage = if (appLevel) {
      random.nextDouble() * simulationParameters.maxRenewableEnergyApplication
    } else {
      random.nextDouble() * simulationParameters.maxRenewableEnergyInfrastructural
    }
    val coalPercentage = 1 - renewablePercentage
    node.setConcentration(new SimpleMolecule("renewablePercentage"), renewablePercentage.asInstanceOf[T])
    node.setConcentration(new SimpleMolecule("coalPercentage"), coalPercentage.asInstanceOf[T])

    // power usage effectiveness
    val pue = if (appLevel) simulationParameters.pueApplication else simulationParameters.pueInfrastructural
    val currentNode = env.getNodeByID(id)
    val links = env
      .getNeighborhood(currentNode)
      .getNeighbors
      .iterator()
      .asScala
      .filter(_.getId != currentNode.getId)
      .map { neighbor =>
        val distance = env.getDistanceBetweenNodes(currentNode, neighbor)
        if (appLevel && neighbor.contains(appDeviceMolecule)) {
          s"link(robot$id, robot${neighbor.getId}, 10, $distance)."
        } else if (appLevel && neighbor.contains(infraDeviceMolecule)) {
          s"link(robot$id, cloud${neighbor.getId}, 10, $distance)."
        } else if (!appLevel && neighbor.contains(appDeviceMolecule)) {
          s"link(cloud$id, robot${neighbor.getId}, 100, $distance)."
        } else if (!appLevel && neighbor.contains(infraDeviceMolecule)) {
          s"link(cloud$id, cloud${neighbor.getId}, 100, $distance)."
        } else {
          throw new RuntimeException(s"Unknown device type for node ${neighbor.getId}")
        }
      }
    s"""
      |physicalDevice($name, $availableHw, $totalHw, $capabilities).
      |energySourceMix($name, [($coalPercentage,coal), ($renewablePercentage,solar)]).
      |pue($name, $pue).
      |${links.mkString("\n")}""".stripMargin
  }
}
