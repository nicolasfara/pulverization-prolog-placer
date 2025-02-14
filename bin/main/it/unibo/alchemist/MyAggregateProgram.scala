package it.unibo.alchemist

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

abstract class MyAggregateProgram extends AggregateProgram with StandardSensors with ScafiAlchemistSupport with BuildingBlocks with FieldUtils {
  def senseOr[T](name: String, otherwise: T): T = node.getOrElse(name, otherwise)
  def writeEnv[T](name: String, value: T): Unit = node.put(name, value)

  def inputFromComponent[C](component: String, default: => C): C = {
    val slot = factory.path(Scope(component))
    val result = for {
      (_, export) <- vm.context.exports().find(_._1 == mid())
      value <- export.get[C](slot)
    } yield value
    result.getOrElse(default)
  }
}
