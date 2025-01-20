package it.unibo.prolog

sealed trait Component {
  val id: Int
  val fqn: String
}
case class Sensor(override val id: Int) extends Component {
  override val fqn: String = "it.unibo.alchemist.Sensors"
}
case class Actuator(override val id: Int) extends Component {
  override val fqn: String = "it.unibo.alchemist.Actuators"
}
case class Communication(override val id: Int) extends Component {
  override val fqn: String = "it.unibo.alchemist.Communication"
}
case class Knowledge(override val id: Int) extends Component {
  override val fqn: String = "it.unibo.alchemist.Knowledge"
}
case class Behavior(override val id: Int) extends Component {
  override val fqn: String = "it.unibo.alchemist.Behavior"
}
object Component {
  def apply(str: String, i: Int): Component = str match {
    case "s"  => Sensor(i)
    case "a"  => Actuator(i)
    case "kd" => Knowledge(i)
    case "b"  => Behavior(i)
    case "c"  => Communication(i)
    case _    => throw new IllegalArgumentException(s"Unknown device type: $str")
  }
}
final case class PlaceDevice(name: String, id: Int)
final case class Placement(component: Component, device: PlaceDevice, hardware: Double)
