package it.unibo.prolog

import it.unibo.alchemist.model.Position

final case class PhysicalDevice[P <: Position[P]](id: Int, pos: P, kind: Kind = Application, appLevel: Boolean = true) extends Prologable {
  import Constants._
  override val name: String = s"$kind$id"

  override def toProlog: String = {
    val j = id
    val capabilities = if (appLevel) s"[($S_NAME$j, temperature)], [($A_NAME$j, thermostate)]" else "[], []"
    val totalHw = if (appLevel) 8 else Int.MaxValue // cloud has infinite hw
    val availableHw = if (appLevel) totalHw else Int.MaxValue
    // e.g. energySourceMix(robot2,[(0.1,gas),(0.8,coal),(0.1,onshorewind)])
    val energySourceMix = if (appLevel) "[(0.4,coal), (0.6,solar)]" else "[(0.8,coal), (0.2,solar)]"
    val pue = if (appLevel) 1.2 else 1.3 // power usage effectiveness
    // energyConsumption(N, Load, EnergyPerLoad)
    // Rumba 1.4KWh - 0.12KWh
    val energyConsumption = if (appLevel) {
      s"energyConsumption($name, L, 1.4)."
    } else {
      val loadThreshold = 2
      val energyPerLoadLow = 0.12
      val energyPerLoadHigh = 0.20
      s"energyConsumption($name, L, $energyPerLoadLow) :- L < $loadThreshold." +
        s"energyConsumption($name, L, EpL) :- L >= $loadThreshold, " +
        s"EpL is $energyPerLoadLow + L*$energyPerLoadHigh, EpL =< $energyPerLoadHigh." +
        s"energyConsumption($name, L, $energyPerLoadHigh) :- L >= $loadThreshold, " +
        s"EpL is $energyPerLoadLow + L*$energyPerLoadHigh, EpL > $energyPerLoadHigh."
    }
    s"""
       |physicalDevice($name, $availableHw, $totalHw, $capabilities).
       |$energyConsumption
       |energySourceMix($name, $energySourceMix).
       |pue($name, $pue).
      """.stripMargin
  }
}
