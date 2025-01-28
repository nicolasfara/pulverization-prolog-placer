package it.unibo.prolog

import it.unibo.alchemist.model.Position

final case class PhysicalDevice[P <: Position[P]](
    id: Int,
    pos: P,
    kind: Kind = Application,
    appLevel: Boolean = true,
    sourceMixApplication: List[Double],
    sourceMixInfrastructural: List[Double],
    pueApplication: Double,
    pueInfrastructural: Double,
) extends Prologable {
  import Constants._
  override val name: String = kind match {
    case Application     => s"robot$id"
    case Infrastructural => s"cloud$id"
  }

  override def toProlog: String = {
    val j = id
    val capabilities = if (appLevel) s"[($S_NAME$j, temperature)], [($A_NAME$j, thermostate)]" else "[], []"
    // TODO: prenderli da simulazione
    val totalHw = if (appLevel) 4 else 20 // cloud has infinite hw
    val availableHw = if (appLevel) totalHw else 20
    // -----
    // e.g. energySourceMix(robot2,[(0.1,gas),(0.8,coal),(0.1,onshorewind)])
    val energySourceMix = if (appLevel) {
      s"[(${sourceMixApplication.head},coal), (${sourceMixApplication.last},solar)]"
    } else {
      s"[(${sourceMixInfrastructural.head},coal), (${sourceMixInfrastructural.last},solar)]"
    }
    val pue = if (appLevel) pueApplication else pueInfrastructural // power usage effectiveness
    // energyConsumption(N, Load, EnergyPerLoad)
    // Rumba 1.4KWh - 0.12KWh
    val energyConsumption = if (appLevel) {
      s"energyConsumption($name, _, 1.4)."
    } else {
      val loadThreshold = 2
      val energyPerLoadLow = 0.12
      val energyPerLoadHigh = 0.20
      s"""
         |energyConsumption($name, L, 0.1) :- L < 10.
         |energyConsumption($name, L, 0.2) :- L >= 10, L < 40.
         |energyConsumption($name, L, 0.3) :- L >= 40.
         |""".stripMargin
    }
    s"""
      |physicalDevice($name, $availableHw, $totalHw, $capabilities).
      |$energyConsumption
      |energySourceMix($name, $energySourceMix).
      |pue($name, $pue).""".stripMargin
  }
}
