package it.unibo.prolog

final case class SimulationParameters(
    maxRenewableEnergyApplication: Double,
    maxRenewableEnergyInfrastructural: Double,
    pueApplication: Double,
    pueInfrastructural: Double,
    availableHwApplication: Int,
    availableHwInfrastructural: Int,
    freeHwInfrastructural: Int,
)
