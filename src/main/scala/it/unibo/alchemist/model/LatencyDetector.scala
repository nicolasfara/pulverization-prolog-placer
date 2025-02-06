package it.unibo.alchemist.model

import it.unibo.alchemist.model.LatencyDetector.APPLICATION_MOLECULE
import it.unibo.alchemist.model.actions.AbstractLocalAction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.utils.AlchemistScafiUtils.getNodeProperty

import scala.jdk.CollectionConverters.IterableHasAsScala

class LatencyDetector[T, P <: Position[P]](
    environment: Environment[T, P],
    node: Node[T],
) extends AbstractLocalAction[T](node) {
  private lazy val allocatorProperty = getNodeProperty(node, classOf[AllocatorProperty[T, P]])

  override def cloneAction(node: Node[T], reaction: Reaction[T]): Action[T] = ???

  override def execute(): Unit = {
    if (node.contains(APPLICATION_MOLECULE)) {
      val interLatency = computeInterDeviceLatency()
      val intraLatency = computeIntraDeviceLatency()
      node.setConcentration(new SimpleMolecule("InterLatency"), interLatency.asInstanceOf[T])
      node.setConcentration(new SimpleMolecule("IntraLatency"), intraLatency.asInstanceOf[T])
    }
  }

  private def computeIntraDeviceLatency(): Double = {
    val devicesInvolvedInPulverization = allocatorProperty.getComponentsAllocation.values.toList
    devicesInvolvedInPulverization.map { offloadedNodeId =>
      val offloadedNode = environment.getNodeByID(offloadedNodeId)
      environment.getDistanceBetweenNodes(node, offloadedNode)
    }.sum / devicesInvolvedInPulverization.size
  }

  private def computeInterDeviceLatency(): Double = {
    val localCommunicationNodeId = allocatorProperty.getComponentsAllocation.find(_._1.contains("Communication")).get._2
    val neighborsCommunicationNodes = environment.getNeighborhood(node).asScala
      .filter(_.contains(APPLICATION_MOLECULE))
      .map { neighborNode =>
        val allocator = getNodeProperty(neighborNode, classOf[AllocatorProperty[T, P]])
        allocator.getComponentsAllocation.find(_._1.contains("Communication")).get._2
      }
      .map(environment.getNodeByID)
      .map { neighborNode =>
        val localCommunicationNode = environment.getNodeByID(localCommunicationNodeId)
        environment.getDistanceBetweenNodes(localCommunicationNode, neighborNode)
      }
    neighborsCommunicationNodes.sum / neighborsCommunicationNodes.size
  }
}

object LatencyDetector {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
}
