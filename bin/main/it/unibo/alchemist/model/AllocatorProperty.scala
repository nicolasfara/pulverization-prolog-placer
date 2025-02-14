package it.unibo.alchemist.model

import it.unibo.alchemist.model.AllocatorProperty.{AllocationException, ComponentId, UnknownComponentException}
import it.unibo.alchemist.model.implementations.actions.RunSurrogateScafiProgram
import it.unibo.alchemist.utils.AlchemistScafiUtils.getAlchemistActions

import scala.jdk.CollectionConverters.IteratorHasAsScala

class AllocatorProperty[T, P <: Position[P]](
    environment: Environment[T, P],
    node: Node[T],
    programDag: Map[ComponentId, Set[ComponentId]],
) extends NodeProperty[T] {

  private lazy val mid = node.getId
  private val availableComponents = programDag.keys.toSet ++ programDag.values.flatten.toSet
  private var componentsAllocation: Map[ComponentId, Int] = availableComponents
    .map(_ -> mid).toMap

  /** Set the components' allocation for this application device (node).
    *
    * This method updates the internal state of the components allocation for this node.
    */
  def setComponentsAllocation(newAllocation: Map[ComponentId, Int]): Unit = {
    checkComponentsValidity(newAllocation).foreach(throw _)
    // val neighborsNodes = environment.getNeighborhood(node).getNeighbors.iterator().asScala.toSet + node
    // checkAllocationValidity(newAllocation, neighborsNodes.map(_.getId)).foreach(throw _)
    removeFromRemote(componentsAllocation, newAllocation)
    componentsAllocation = componentsAllocation ++ newAllocation
  }

  private def removeFromRemote(oldAllocation: Map[ComponentId, Int], newAllocation: Map[ComponentId, Int]): Unit = {
    newAllocation.foreach { case (componentId, where) =>
      oldAllocation.get(componentId) match {
        case Some(oldEdgeServerID) if where != oldEdgeServerID =>
          getAlchemistActions(environment, oldEdgeServerID, classOf[RunSurrogateScafiProgram[T, P]])
            .find(_.programNameMolecule.getName == componentId)
            .foreach { v => v.removeSurrogateFor(node.getId) }
        case _ =>
      }
    }
  }

  /** Get the components' allocation for this application device (node).
    */
  def getComponentsAllocation: Map[ComponentId, Int] =
    componentsAllocation

  /** Check if the specified component is offloaded to an _infrastructural device_.
    *
    * Returns true if the component is offloaded to a different device, false otherwise.
    */
  def isOffloaded(componentId: ComponentId, ownerNode: Node[T]): Boolean =
    componentsAllocation.get(componentId).exists(_ != ownerNode.getId)

  /** Given a component id, return the device id where the component is allocated.
    *
    * Returns None if the component does not exist in the allocation.
    */
  def getDeviceIdForComponent(componentId: ComponentId): Option[Int] = componentsAllocation.get(componentId)

  /** Check if all the specified components are defined in the macro-program specification (components DAG)
    */
  private def checkComponentsValidity(
      allocation: Map[ComponentId, Int],
  ): Option[UnknownComponentException] = {
    val unknownComponents = allocation.keys.filterNot(availableComponents.contains).toList
    if (unknownComponents.nonEmpty) {
      Some(UnknownComponentException(s"Unknown components: ${unknownComponents.mkString(", ")}"))
    } else {
      None
    }
  }

  /** Return an AllocationException if the allocation is invalid, otherwise return None The allocation is invalid if the values in the allocation are
    * not in neighbors
    *
    * NB: this is a simplified version of the "forward chain" admitting only 1-hop neighbors!
    */
  private def checkAllocationValidity(
      allocation: Map[ComponentId, Int],
      neighbors: Set[Int],
  ): Option[AllocationException] = {
    val invalidComponents = allocation.filterNot { case (_, value) => neighbors.contains(value) }
    if (invalidComponents.nonEmpty) {
      Some(AllocationException(s"Invalid allocation for components: ${invalidComponents.mkString(", ")}"))
    } else {
      None
    }
  }

  override def getNode: Node[T] = node

  override def cloneOnNewNode(node: Node[T]): NodeProperty[T] = ???
}

object AllocatorProperty {
  type ComponentId = String
  final case class AllocationException(message: String) extends RuntimeException(message)
  final case class UnknownComponentException(message: String) extends RuntimeException(message)
}
