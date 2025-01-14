package it.unibo.prolog

import it.unibo.alchemist.model.{Environment, Node, Position}

import scala.jdk.CollectionConverters.IteratorHasAsScala

object DeploymentGenerator {
  def generateDeployment[T, P <: Position[P]](
      environment: Environment[T, P],
      applicationDevices: List[Node[T]],
      infrastructuralDevices: List[Node[T]],
  ): String = {
    val physicalApplicationDevices = applicationDevices
      .map { node => PhysicalDevice(node.getId, environment.getPosition(node), Application) }
    val physicalInfrastructuralDevices = infrastructuralDevices
      .map { node => PhysicalDevice(node.getId, environment.getPosition(node), Infrastructural, appLevel = false) }
    val digitalDevices = applicationDevices
      .map { node =>
        val physicalTwin = getPhysicalDeviceById(node.getId, physicalApplicationDevices)
          .getOrElse(throw new RuntimeException(s"Physical device not found for node ${node.getId}"))
        Device(node.getId, physicalTwin)
      }

    def physicalLinks(physicalDevice: PhysicalDevice[P]): Set[PhysicalDevice[P]] = {
      val node = environment.getNodeByID(physicalDevice.id)
      environment
        .getNeighborhood(node)
        .getNeighbors
        .iterator()
        .asScala
        .map { neighbor => getPhysicalDeviceById(neighbor.getId, physicalApplicationDevices) }
        .collect { case Some(neighbor) => neighbor }
        .toSet
    }

    def applicationLinks(device: Device[P]): Set[Device[P]] = {
      val node = environment.getNodeByID(device.id)
      environment
        .getNeighborhood(node)
        .getNeighbors
        .iterator()
        .asScala
        .map { neighbor => digitalDevices.find(_.id == neighbor.getId) }
        .collect { case Some(neighbor) => neighbor }
        .toSet
    }

    val prologProgram = new StringBuilder()
    prologProgram.append(
      """
        |:- discontiguous digitalDevice/3.
        |:- discontiguous knowledge/2.
        |:- discontiguous behaviour/3.
        |:- discontiguous communication/3.
        |:- discontiguous sense/3.
        |:- discontiguous act/3.
        |""".stripMargin
    )

    for {
      physicalDevice <- physicalApplicationDevices ++ physicalInfrastructuralDevices
    } {
      prologProgram.append(physicalDevice.toProlog)
      prologProgram.append(LINE_SEPARATOR)
      val physicalNeighbors = physicalLinks(physicalDevice)
      for {
        neighbor <- physicalNeighbors
      } {
        // Assume the latency is the distance between the two nodes in the simulation -- TODO: double check
        val latency =
          environment.getDistanceBetweenNodes(
            environment.getNodeByID(physicalDevice.id),
            environment.getNodeByID(neighbor.id),
          )
        // Different bandwidth based on the device kind -- TODO: double check constants
        val bandwidth = if (physicalDevice.appLevel) 10 else 100
        prologProgram.append(s"link(${physicalDevice.name}, ${neighbor.name}, $bandwidth, $latency).")
        prologProgram.append(LINE_SEPARATOR)
      }
    }

    for {
      device <- digitalDevices
    } {
      prologProgram.append(device.toProlog)
      prologProgram.append(LINE_SEPARATOR)
      // TODO: check the commented code
      /*
      val logicalNeighbors = applicationLinks(device)
      for {
        neighbor <- logicalNeighbors
      } {
        prologProgram.append(s"link(robot${device.id}, robot${neighbor.id}, 4, 50).") ++ LINE_SEPARATOR
      }
       */
    }
    prologProgram.toString()
  }

  private val LINE_SEPARATOR = scala.util.Properties.lineSeparator
  private def getPhysicalDeviceById[P <: Position[P]](id: Int, devices: List[PhysicalDevice[P]]): Option[PhysicalDevice[P]] = {
    devices.find(_.id == id)
  }
}
