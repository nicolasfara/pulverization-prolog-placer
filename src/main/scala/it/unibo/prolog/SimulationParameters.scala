package it.unibo.prolog

final case class SimulationParameters(
    energyMixApplication: List[Double],
    energyMixInfrastructural: List[Double],
    pueApplication: Double,
    pueInfrastructural: Double,
    availableHwApplication: Int,
    availableHwInfrastructural: Int,
)
