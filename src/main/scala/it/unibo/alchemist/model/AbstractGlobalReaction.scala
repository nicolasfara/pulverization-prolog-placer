package it.unibo.alchemist.model

import org.danilopianini.util.{ListSet, ListSets}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import java.util

abstract class AbstractGlobalReaction[T, P <: Position[P]](
    val environment: Environment[T, P],
    distribution: TimeDistribution[T],
) extends GlobalReaction[T] {

  private val actions: util.List[Action[T]] = util.List.of()
  private val conditions: util.List[Condition[T]] = util.List.of()

  override def getActions: util.List[Action[T]] = actions

  override def setActions(list: util.List[_ <: Action[T]]): Unit = {
    actions.clear()
    actions.addAll(list)
  }

  override def setConditions(list: util.List[_ <: Condition[T]]): Unit = {
    conditions.clear()
    conditions.addAll(list)
  }

  override def execute(): Unit = {
    executeBeforeUpdateDistribution()
    distribution.update(getTimeDistribution.getNextOccurence, true, getRate, environment)
  }

  protected def executeBeforeUpdateDistribution(): Unit

  override def getConditions: util.List[Condition[T]] = conditions

  override def getInboundDependencies: ListSet[_ <: Dependency] = ListSets.emptyListSet()

  override def getOutboundDependencies: ListSet[_ <: Dependency] = ListSets.emptyListSet()

  override def getTimeDistribution: TimeDistribution[T] = distribution

  override def canExecute: Boolean = true

  override def initializationComplete(time: Time, environment: Environment[T, _]): Unit = {}

  override def update(time: Time, b: Boolean, environment: Environment[T, _]): Unit = {}

  override def compareTo(other: Actionable[T]): Int = getTau.compareTo(other.getTau)

  override def getRate: Double = distribution.getRate

  override def getTau: Time = distribution.getNextOccurence

  // Utility methods
  def nodes: List[Node[T]] = environment.getNodes.iterator().asScala.toList
}
