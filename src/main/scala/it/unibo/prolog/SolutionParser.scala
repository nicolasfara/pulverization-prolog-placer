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
  def apply(componentRepr: String, id: Int): Component = componentRepr match {
    case "s"  => Sensor(id)
    case "a"  => Actuator(id)
    case "kd" => Knowledge(id)
    case "b"  => Behavior(id)
    case "c"  => Communication(id)
    case _    => throw new IllegalArgumentException(s"Unknown device type: $componentRepr")
  }
}
final case class PlaceDevice(name: String, id: Int) {
  override def toString: String = s"$name$id"
}
final case class Placement(component: Component, device: PlaceDevice, hardware: Double) {
  override def toString: String = {
    val cc = component match {
      case Sensor(_) => s"s${component.id}"
      case Actuator(_) => s"a${component.id}"
      case Communication(_) => s"c${component.id}"
      case Knowledge(_) => s"kd${component.id}"
      case Behavior(_) => s"b${component.id}"
    }
    s"on($cc, $device, $hardware)"
  }
}
final case class DeviceDeployment(deviceId: Int, carbon: Double, energy: Double, placements: List[Placement])
final case class Footprint(carbon: Double, energy: Double)
