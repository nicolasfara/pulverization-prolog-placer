package it.unibo.alchemist.model

import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.utils.AlchemistScafiUtils.getNodeProperty
import it.unibo.prolog.{DeviceDeployment, Placement, PrologPlacerManager, SimulationParameters}
import org.apache.commons.math3.random.RandomGenerator

class PrologDeploymentUpdater[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
    random: RandomGenerator,
    isBaseline: Boolean,
    deploymentStrategy: String,
    maxRenewableEnergyApplication: Double,
    maxRenewableEnergyInfrastructural: Double,
    pueApplication: Double,
    pueInfrastructural: Double,
    availableHwApplication: Int,
    availableHwInfrastructural: Int,
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {

  private lazy val placerManager = new PrologPlacerManager[T, P](
    environment,
    random,
    deploymentStrategy,
    SimulationParameters(
      maxRenewableEnergyApplication,
      maxRenewableEnergyInfrastructural,
      pueApplication,
      pueInfrastructural,
      availableHwApplication,
      availableHwInfrastructural,
    ),
  )
  private var lastDeployment: List[DeviceDeployment] = _
  private var isExecuted = false

  override protected def executeBeforeUpdateDistribution(): Unit = {
    if (isBaseline && !isExecuted) {
      lastDeployment = placerManager.getNewDeployment
      lastDeployment.foreach { case d @ DeviceDeployment(id, _, _, placements) =>
        val currentNode = environment.getNodeByID(id)
        currentNode.setConcentration(new SimpleMolecule("Deployment"), d.asInstanceOf[T])
        placements.foreach(placeComponentPerDevice(_, currentNode))
      }
      isExecuted = true
    }
    placerManager.updateTopology()
    lastDeployment.foreach { deployment =>
      val footprint = placerManager.getFootprint(deployment)
      val node = environment.getNodeByID(deployment.deviceId)
      node.setConcentration(new SimpleMolecule("Carbon"), footprint.carbon.asInstanceOf[T])
      node.setConcentration(new SimpleMolecule("Energy"), footprint.energy.asInstanceOf[T])
    }
  }

  private def placeComponentPerDevice(placement: Placement, nodeDevice: Node[T]): Unit = {
    // TODO: fix hw allocation
    val Placement(component, device, hw) = placement
    val allocator = getNodeProperty(nodeDevice, classOf[AllocatorProperty[T, P]])
    allocator.setComponentsAllocation(Map(component.fqn -> device.id))
  }
}
