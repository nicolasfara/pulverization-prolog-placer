package it.unibo.alchemist.model.implementations.conditions

import it.unibo.alchemist.model.conditions.AbstractCondition
import it.unibo.alchemist.model.implementations.actions.RunScafiProgram
import it.unibo.alchemist.model.implementations.nodes.ScafiDevice
import it.unibo.alchemist.model._

class ScafiComputationalRoundComplete[T, P <: Position[P], Program <: RunScafiProgram[T, P]](
    val device: ScafiDevice[T],
    val program: RunScafiProgram[T, P],
    val clazz: Class[Program],
) extends AbstractCondition(device.getNode) {
  declareDependencyOn(program.asMolecule)

  override def cloneCondition(node: Node[T], reaction: Reaction[T]): Condition[T] = {
    SurrogateScafiIncarnation.runInScafiDeviceContext[T, Condition[T]](
      node,
      getClass.getSimpleName + " cannot get cloned on a node of type " + node.getClass.getSimpleName,
      device => {
        val possibleRefs: Iterable[Program] = SurrogateScafiIncarnation
          .allScafiProgramsForType[T, P](device.getNode, clazz)
          .map(_.asInstanceOf[Program])
        if (possibleRefs.size == 1) {
          new ScafiComputationalRoundComplete(device, possibleRefs.head, clazz)
        } else {
          throw new IllegalStateException(
            "There must be one and one only unconfigured " + classOf[Nothing].getSimpleName,
          )
        }
      },
    )
  }

  override def getContext = Context.LOCAL

  override def getPropensityContribution = if (isValid) 1 else 0

  override def isValid = program.isComputationalCycleComplete

  override def getNode = super.getNode

  override def toString = program.asMolecule.getName + " completed round"
}
