package it.unibo.prolog

import it.unibo.alchemist.model.{Environment, Node, Position}

object Main {
  def generateDeployment[T, P <: Position[P]](
    environment: Environment[T, P],
    applicationDevices: List[Node[T]],
    infrastructuralDevices: List[Node[T]],
    fileName: String = "data.pl",
  ) = {

  }
}
