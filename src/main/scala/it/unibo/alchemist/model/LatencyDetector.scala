package it.unibo.alchemist.model

import it.unibo.alchemist.model.LatencyDetector.APPLICATION_MOLECULE
import it.unibo.alchemist.model.actions.AbstractLocalAction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.utils.AlchemistScafiUtils.getNodeProperty
import it.unibo.alchemist.util.Environments.INSTANCE.allShortestPaths
import kotlin.Pair

import scala.jdk.CollectionConverters.{IterableHasAsScala, MapHasAsScala}

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

  private def computeAllShortestPaths(): Map[(Node[T], Node[T]), Double] =
    allShortestPaths(
      environment,
      (node1: Node[T], node2: Node[T]) => {
        if (node1 == node2) 0.0
        else if (environment.getNeighborhood(node1).contains(node2)) environment.getDistanceBetweenNodes(node1, node2)
        else Double.PositiveInfinity
      },
    ).asScala.map(e => (e._1.getFirst, e._1.getSecond) -> e._2.doubleValue()).toMap

  private def computeIntraDeviceLatency(): Double = {
    val devicesInvolvedInPulverization = allocatorProperty.getComponentsAllocation.values.toList
    if (devicesInvolvedInPulverization.isEmpty) {
      return 0.0
    }
    devicesInvolvedInPulverization.map { offloadedNodeId =>
      val shortestPaths = computeAllShortestPaths()
      shortestPaths
        .getOrElse(
          (node, environment.getNodeByID(offloadedNodeId)),
          shortestPaths((environment.getNodeByID(offloadedNodeId), node)),
        )
        .doubleValue()
    }.sum / devicesInvolvedInPulverization.size
  }

  private def computeInterDeviceLatency(): Double = {
    val localCommunicationNodeId = allocatorProperty.getComponentsAllocation.find(_._1.contains("Communication")).get._2
    val neighborsNodes = environment.getNeighborhood(node).asScala.filter(_.contains(APPLICATION_MOLECULE))
    val neighborsCommunicationNodeIds = neighborsNodes.map { neighborNode =>
      val allocator = getNodeProperty(neighborNode, classOf[AllocatorProperty[T, P]])
      allocator.getComponentsAllocation.find(_._1.contains("Communication")).get._2
    }
    if (neighborsCommunicationNodeIds.isEmpty) {
      return Double.NaN
    }
    // Get Alchemist Node[T] from ID
    val communicationNode = environment.getNodeByID(localCommunicationNodeId)
    val neighborsCommunicationNodes = neighborsCommunicationNodeIds.map(environment.getNodeByID)
    neighborsCommunicationNodes.map { neighborNode =>
      val shortestPaths = computeAllShortestPaths()
      shortestPaths
        .getOrElse(
          (communicationNode, neighborNode),
          shortestPaths((neighborNode, communicationNode)),
        )
        .doubleValue()
    }.sum / neighborsCommunicationNodes.size

//    val localCommunicationNodeId = allocatorProperty.getComponentsAllocation.find(_._1.contains("Communication")).get._2
//    val neighborsCommunicationNodes = environment
//      .getNeighborhood(node)
//      .asScala
//      .filter(_.contains(APPLICATION_MOLECULE))
//      .map { neighborNode =>
//        val allocator = getNodeProperty(neighborNode, classOf[AllocatorProperty[T, P]])
//        allocator.getComponentsAllocation.find(_._1.contains("Communication")).get._2
//      }
//      .map(environment.getNodeByID)
//      .map { neighborNode =>
//        val localCommunicationNode = environment.getNodeByID(localCommunicationNodeId)
//        environment.getDistanceBetweenNodes(localCommunicationNode, neighborNode)
//      }
//    neighborsCommunicationNodes.sum / neighborsCommunicationNodes.size
  }
}

object LatencyDetector {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
}
