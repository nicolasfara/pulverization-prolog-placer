package it.unibo.alchemist

class GreaterDistance extends MyAggregateProgram {

  override def main(): Any = {
    val distanceFromSource = inputFromComponent("it.unibo.alchemist.DensityEstimation", Double.PositiveInfinity)
    val result = distanceFromSource > 6.0
    writeEnv("distance", result)
    result
  }
}
