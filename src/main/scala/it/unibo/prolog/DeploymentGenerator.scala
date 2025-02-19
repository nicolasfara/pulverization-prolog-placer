package it.unibo.prolog

import it.unibo.alchemist.model.{Environment, Node, Position}
import it.unibo.prolog.model.{Application, Device, Infrastructural, PhysicalDevice}
import org.apache.commons.math3.random.RandomGenerator

object DeploymentGenerator {
  def generateDeployment[T, P <: Position[P]](
      environment: Environment[T, P],
      random: RandomGenerator,
      applicationDevices: List[Node[T]],
      infrastructuralDevices: List[Node[T]],
      simulationParameters: SimulationParameters,
  ): String = {
    val physicalApplicationDevices = applicationDevices
      .map { node =>
        PhysicalDevice(
          node.getId,
          environment.getPosition(node),
          environment,
          random,
          Application,
          appLevel = true,
          simulationParameters,
        )
      }
    val physicalInfrastructuralDevices = infrastructuralDevices
      .map { node =>
        PhysicalDevice(
          node.getId,
          environment.getPosition(node),
          environment,
          random,
          Infrastructural,
          appLevel = false,
          simulationParameters,
        )
      }
    val digitalDevices = applicationDevices
      .map { node =>
        val physicalTwin = getPhysicalDeviceById(node.getId, physicalApplicationDevices)
          .getOrElse(throw new RuntimeException(s"Physical device not found for node ${node.getId}"))
        Device(node.getId, physicalTwin)
      }

    val prologProgram = new StringBuilder()
    prologProgram.append(
      """
        |:- discontiguous physicalDevice/5.
        |:- discontiguous energyConsumption/3.
        |:- discontiguous pue/2.
        |:- discontiguous energySourceMix/2.
        |:- discontiguous link/4.
        |:- discontiguous digitalDevice/3.
        |:- discontiguous knowledge/2.
        |:- discontiguous behaviour/3.
        |:- discontiguous communication/3.
        |:- discontiguous sense/3.
        |:- discontiguous act/3.
        |""".stripMargin,
    )

    for {
      physicalDevice <- physicalApplicationDevices ++ physicalInfrastructuralDevices
    } {
      prologProgram.append(physicalDevice.toProlog)
      prologProgram.append(LINE_SEPARATOR)
    }

    prologProgram.append(s"""
        |energyConsumption(cloud0, L, 0.05) :- L < 0.01.
        |energyConsumption(cloud0, L, 0.2) :- L >= 0.01, L < 0.02.
        |energyConsumption(cloud0, L, 0.250) :- L >= 0.02.
        |
        |energyConsumption(N, L, 0.1) :- dif(N,cloud0), L < 10.
        |energyConsumption(N, L, 0.2) :- dif(N,cloud0), L >= 10, L < 40.
        |energyConsumption(N, L, 0.3) :- dif(N, cloud0), L >= 40.
        |""".stripMargin)

    for {
      device <- digitalDevices
    } {
      prologProgram.append(device.toProlog)
      prologProgram.append(LINE_SEPARATOR)
    }
    prologProgram.toString()
  }

  private val LINE_SEPARATOR = scala.util.Properties.lineSeparator
  private def getPhysicalDeviceById[T, P <: Position[P]](id: Int, devices: List[PhysicalDevice[T, P]]): Option[PhysicalDevice[T, P]] = {
    devices.find(_.id == id)
  }
}
