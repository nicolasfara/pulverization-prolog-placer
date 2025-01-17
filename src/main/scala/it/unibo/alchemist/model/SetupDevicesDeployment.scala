package it.unibo.alchemist.model

import it.unibo.alchemist.model.SetupDevicesDeployment.{APPLICATION_MOLECULE, ENERGY_SOURCE_DATA, ENERGY_SOURCE_FILE_NAME, INFRASTRUCTURAL_MOLECULE, MAIN_FILE_NAME, PROLOG_MAIN_FILE}
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.prolog.DeploymentGenerator.generateDeployment
import it.unibo.prolog.{Component, PlaceDevice, Placement}
import org.jpl7.{Atom, Query, Term, Variable}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala

class SetupDevicesDeployment[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {

  private val placementPattern = """on\(([^,]+),\s*([^,]+),\s*([^)]+)\)""".r
  private val componentPattern = """([a-z]+)(\d+)""".r

  override protected def executeBeforeUpdateDistribution(): Unit = {
    val mainFile = writePrologFilesIntoTempDirectory()
    val consultResult = new Query(
      "consult",
      Array[Term](
        new Atom(s"${mainFile.toAbsolutePath.toString}"),
      ),
    )
    println(s"Prolog file ${mainFile.toAbsolutePath.toString} consulted: ${consultResult.hasSolution}")
    // Query the knowledge base
    val queryResult = new Query(
      "placeAll",
      Array[Term](
        new Atom("heu"),
        new Variable("P"),
      ),
    )
    if (queryResult.hasSolution) {
      val solution = queryResult.nextSolution().get("P")
      val placements = parseSolution(solution)
      println(s"Placements: $placements")
    }
  }

  private def writePrologFilesIntoTempDirectory(): Path = {
    val applicationDevice = nodesContainingMolecule(APPLICATION_MOLECULE)
    val infrastructuralDevice = nodesContainingMolecule(INFRASTRUCTURAL_MOLECULE)
    val deployment = generateDeployment(environment, applicationDevice, infrastructuralDevice)
    val destinationDirectory = Files.createTempDirectory("prolog")
    val mainFile = Files.copy(PROLOG_MAIN_FILE, destinationDirectory.resolve(MAIN_FILE_NAME))
    Files.copy(ENERGY_SOURCE_DATA, destinationDirectory.resolve(ENERGY_SOURCE_FILE_NAME))
    Files.write(destinationDirectory.resolve("data.pl"), deployment.getBytes)
    mainFile
  }

  private def parseSolution(solution: Term): List[Placement] = {
    val placements = for {
      placementPattern(param1, param2, hw) <- placementPattern.findAllIn(solution.toString)
      componentPattern(component, cId) <- componentPattern.findAllIn(param1)
      componentPattern(device, dId) <- componentPattern.findAllIn(param2)
    } yield { Placement(Component(component, cId.toInt), PlaceDevice(device, dId.toInt), hw.toDouble) }
    placements.toList
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
