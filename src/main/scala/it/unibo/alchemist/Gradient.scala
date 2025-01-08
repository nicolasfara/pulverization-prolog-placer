package it.unibo.alchemist

class Gradient extends MyAggregateProgram {

  override def main(): Any = {
    val potential = classicGradient(mid() == 0)
    writeEnv("potential", potential)
    potential
  }
}
