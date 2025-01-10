package it.unibo.prolog

import it.unibo.alchemist.model.Position

final case class Device[P <: Position[P]](id: Int, physicalTwin: PhysicalDevice[P]) extends Prologable {
  import Constants._

  override val name: String = s"$DD_NAME$id"
  override def toProlog(): String = {
    val i = id
    /*
     * % knowledge(DId, HWReqs).
     * % behaviour(BId, HWReqs, LatToK).
     * % communication(CId, HWReqs, LatToK).
     * % sense(PhySense, HWReqs, LatToK).
     * % act(AId, HWReqs, LatToK).
     */
    val knowledgeHwReqs = 1
    val commHwReqs = 0.5
    val commLatencyToK = 150
    val behaviorHwReqs = 2
    val behaviorLatencyToK = 150
    val sensorHwReqs = 0.25
    val sensorLatencyToK = 25
    val actuatorHwReqs = 0.25
    val actuatorLatencyToK = 25
    s"""
       |digitalDevice($name, $K_NAME$i, [s$i, a$i, b$i, c$i]).
       |knowledge($K_NAME$i, $knowledgeHwReqs).
       |behaviour($B_NAME$i, $behaviorHwReqs, $behaviorLatencyToK).
       |communication($C_NAME$i, $commHwReqs, $commLatencyToK).
       |sense($S_NAME$i, $sensorHwReqs, $sensorLatencyToK).
       |act($A_NAME$i, $actuatorHwReqs, $actuatorLatencyToK).
    """.stripMargin
  }
}
