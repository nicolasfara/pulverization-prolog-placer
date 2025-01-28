package it.unibo.alchemist.model

import it.unibo.alchemist.model.SetupDevicesDeployment.{
  APPLICATION_MOLECULE,
  ENERGY_SOURCE_DATA,
  ENERGY_SOURCE_FILE_NAME,
  INFRASTRUCTURAL_MOLECULE,
  MAIN_FILE_NAME,
  PROLOG_MAIN_FILE,
}
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.utils.AlchemistScafiUtils.getNodeProperty
import it.unibo.prolog.DeploymentGenerator.generateDeployment
import it.unibo.prolog.{Component, DeviceDeployment, PlaceDevice, Placement}
import org.jpl7.{Atom, Query, Term, Variable}

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}

class SetupDevicesDeployment[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
    deploymentStrategy: String,
    energyMixApplication: java.util.List[Double],
    energyMixInfrastructural: java.util.List[Double],
    pueApplication: Double,
    pueInfrastructural: Double,
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {

  private lazy val placementPredicate = deploymentStrategy match {
    case "optimal"   => "opt"
    case "heuristic" => "heu"
  }
  private val placementPattern = """on\(([^,]+),\s*([^,]+),\s*([^)]+)\)""".r
  private val componentPattern = """([a-z]+)(\d+)""".r
  private val deviceSolutionPattern = """p\((\d+),\s*([\d.]+),\s*([\d.]+),\s*\[(.*?)]\)""".r

  override protected def executeBeforeUpdateDistribution(): Unit = {
    val deployment = getDeployment.getOrElse(throw new RuntimeException("Deployment not found"))
    val applicationDevices = parseSolution(deployment)
    applicationDevices.foreach { case DeviceDeployment(id, carbon, energy, placements) =>
      val currentNode = environment.getNodeByID(id)
      currentNode.setConcentration(new SimpleMolecule("Carbon"), carbon.asInstanceOf[T])
      currentNode.setConcentration(new SimpleMolecule("Energy"), energy.asInstanceOf[T])
      placements.foreach(placeComponentPerDevice(_, currentNode))
    }
  }

  private def getDeployment: Option[Term] = {
    val destinationDirectory = Files.createTempDirectory("prolog")
    val mainFile = Files.copy(PROLOG_MAIN_FILE, destinationDirectory.resolve(MAIN_FILE_NAME), StandardCopyOption.REPLACE_EXISTING)
    Files.copy(ENERGY_SOURCE_DATA, destinationDirectory.resolve(ENERGY_SOURCE_FILE_NAME), StandardCopyOption.REPLACE_EXISTING)
    writePrologFilesIntoTempDirectory(destinationDirectory)
    val consultResult = new Query(
      "consult",
      Array[Term](
        new Atom(s"${mainFile.toAbsolutePath.toString}"),
      ),
    )
    println(s"Prolog file ${mainFile.toAbsolutePath.toString} consulted: ${consultResult.hasSolution}")
    val queryResult = new Query(
      "placeAll",
      Array[Term](
        new Atom(placementPredicate),
        new Variable("P"),
      ),
    )
    if (queryResult.hasSolution) {
      Some(queryResult.nextSolution().get("P"))
    } else {
      None
    }
  }

  private def placeComponentPerDevice(placement: Placement, nodeDevice: Node[T]): Unit = {
    // TODO: fix hw allocation
    val Placement(component, device, hw) = placement
    val allocator = getNodeProperty(nodeDevice, classOf[AllocatorProperty[T, P]])
    allocator.setComponentsAllocation(Map(component.fqn -> device.id))
  }

  private def writePrologFilesIntoTempDirectory(destinationDirectory: Path): Unit = {
    val applicationDevice = nodesContainingMolecule(APPLICATION_MOLECULE)
    val infrastructuralDevice = nodesContainingMolecule(INFRASTRUCTURAL_MOLECULE)
    val deployment = generateDeployment(
      environment,
      applicationDevice,
      infrastructuralDevice,
      energyMixApplication.asScala.toList,
      energyMixInfrastructural.asScala.toList,
      pueApplication,
      pueInfrastructural,
    )
    Files.write(destinationDirectory.resolve("data.pl"), deployment.getBytes)
  }

  private def parseSolution(solution: Term): List[DeviceDeployment] = {
    (for {
      deviceSolutionPattern(_, carbon, energy, components) <- deviceSolutionPattern.findAllIn(solution.toString)
      componentsDeployment = placementPattern.findAllIn(components).toList.map {
        case placementPattern(componentPattern(component, cId), componentPattern(device, dId), hw) =>
          Placement(Component(component, cId.toInt), PlaceDevice(device, dId.toInt), hw.toDouble)
      }
    } yield DeviceDeployment(0, carbon.toDouble, energy.toDouble, componentsDeployment)).toList // TODO: fix hardcoded 0
  }

  private def nodesContainingMolecule(molecule: SimpleMolecule): List[Node[T]] =
    environment.getNodes.iterator().asScala.toList.filter(_.contains(molecule))
}

object SetupDevicesDeployment {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
  private val INFRASTRUCTURAL_MOLECULE = new SimpleMolecule("infrastructuralDevice")
  private val PROLOG_DIRECTORY = Path.of("src", "main", "resources", "prolog")
  private val MAIN_FILE_NAME = "main.pl"
  private val ENERGY_SOURCE_FILE_NAME = "energysourcedata.pl"
  private val PROLOG_MAIN_FILE = PROLOG_DIRECTORY.resolve(MAIN_FILE_NAME)
  private val ENERGY_SOURCE_DATA = PROLOG_DIRECTORY.resolve(ENERGY_SOURCE_FILE_NAME)
}
