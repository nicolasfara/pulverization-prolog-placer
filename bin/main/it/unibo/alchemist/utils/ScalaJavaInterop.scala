package it.unibo.alchemist.utils

import it.unibo.alchemist.model._

import scala.jdk.CollectionConverters.IteratorHasAsScala

object ScalaJavaInterop {
  implicit class EnvironmentOps[P <: Position[P], T](val environment: Environment[T, _]) extends AnyVal {
    def getNodesAsScala: List[Node[T]] = environment.getNodes.iterator().asScala.toList
  }
}
