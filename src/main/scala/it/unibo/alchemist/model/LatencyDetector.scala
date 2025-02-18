package it.unibo.alchemist.model

import it.unibo.alchemist.model.LatencyDetector.APPLICATION_MOLECULE
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.utils.AlchemistScafiUtils.getNodeProperty
import it.unibo.alchemist.util.Environments.INSTANCE.allShortestPaths
import it.unibo.alchemist.util.Environments.UndirectedEdge

import scala.jdk.CollectionConverters.{IterableHasAsScala, IteratorHasAsScala, MapHasAsScala}

class LatencyDetector[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {
  private def computeAllShortestPaths(): Map[UndirectedEdge[T], Double] =
    allShortestPaths(
      environment,
      (node1: Node[T], node2: Node[T]) => {
        if (node1 == node2) 0.0
        else if (environment.getNeighborhood(node1).contains(node2)) environment.getDistanceBetweenNodes(node1, node2)
        else Double.PositiveInfinity
      },
    ).asScala.map { case (edge, distance) => edge -> distance.doubleValue() }.toMap

  override protected def executeBeforeUpdateDistribution(): Unit = {
    val currentPaths = computeAllShortestPaths()
    computeInterDeviceLatency(currentPaths)
    computeIntraDeviceLatency(currentPaths)
  }

  private def computeIntraDeviceLatency(currentPaths: Map[UndirectedEdge[T], Double]): Unit = {
    environment.getNodes.asScala
      .filter(_.contains(APPLICATION_MOLECULE))
      .map(node => node -> getNodeProperty(node, classOf[AllocatorProperty[T, P]]))
      .foreach { case (node, allocator) =>
        val knowledgeDevice = allocator
          .getDeviceIdForComponent("it.unibo.alchemist.Knowledge")
          .getOrElse(throw new RuntimeException(s"Knowledge component not found for $node"))
        val behaviorDevice = allocator
          .getDeviceIdForComponent("it.unibo.alchemist.Behavior")
          .getOrElse(throw new RuntimeException(s"Behavior component not found for $node"))
        val communicationDevice = allocator
          .getDeviceIdForComponent("it.unibo.alchemist.Communication")
          .getOrElse(throw new RuntimeException(s"Communication component not found for $node"))
        val offloadedComponents = List(knowledgeDevice, behaviorDevice, communicationDevice)
          .map(environment.getNodeByID)
          .filter(_.getId != node.getId)
        val averageLatency = if (offloadedComponents.isEmpty) {
          0.0
        } else {
          offloadedComponents.map { offloadedNode =>
            currentPaths(new UndirectedEdge(node, offloadedNode))
          }.sum / offloadedComponents.size
        }
        node.setConcentration(new SimpleMolecule("IntraLatency"), averageLatency.asInstanceOf[T])
      }
  }

  private def computeInterDeviceLatency(currentPaths: Map[UndirectedEdge[T], Double]): Unit = {
    val communicationComponents = environment.getNodes
      .iterator()
      .asScala
      .filter(_.contains(APPLICATION_MOLECULE))
      .map(node => node -> getNodeProperty(node, classOf[AllocatorProperty[T, P]]))
      .map { case (node, allocator) =>
        val commNode = environment.getNodeByID(
          allocator
            .getDeviceIdForComponent("it.unibo.alchemist.Communication")
            .getOrElse(throw new RuntimeException(s"Communication component not found for $node")),
        )
        val commNeighbors = environment
          .getNeighborhood(node)
          .asScala
          .filter(_.contains(APPLICATION_MOLECULE))
          .map(getNodeProperty(_, classOf[AllocatorProperty[T, P]]))
          .map(
            _.getDeviceIdForComponent("it.unibo.alchemist.Communication")
              .getOrElse(throw new RuntimeException(s"Communication component not found for $node")),
          )
          .map(environment.getNodeByID)
        node -> commNeighbors.map(neighbor => currentPaths(new UndirectedEdge(commNode, neighbor))).sum / commNeighbors.size
      }
    communicationComponents.foreach { case (node, avgLatency) =>
      node.setConcentration(new SimpleMolecule("InterLatency"), avgLatency.asInstanceOf[T])
    }
  }
}

object LatencyDetector {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
}
