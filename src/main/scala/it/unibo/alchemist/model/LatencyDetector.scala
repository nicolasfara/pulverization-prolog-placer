package it.unibo.alchemist.model

import it.unibo.alchemist.model.actions.AbstractLocalAction
import it.unibo.alchemist.model.molecules.SimpleMolecule

class LatencyDetector[T, P <: Position[P]](
    environment: Environment[T, P],
    node: Node[T],
) extends AbstractLocalAction[T](node) {

  override def cloneAction(node: Node[T], reaction: Reaction[T]): Action[T] = ???

  override def execute(): Unit = {
    val latency = Double.PositiveInfinity
    node.setConcentration(new SimpleMolecule("Latency"), latency.asInstanceOf[T])
  }
}
