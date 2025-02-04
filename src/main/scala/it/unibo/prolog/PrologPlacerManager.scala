package it.unibo.prolog

import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.{Environment, Node, Position}
import it.unibo.prolog.DeploymentGenerator.generateDeployment
import it.unibo.prolog.PrologPlacerManager.{
  APPLICATION_MOLECULE,
  ENERGY_SOURCE_DATA,
  ENERGY_SOURCE_FILE_NAME,
  INFRASTRUCTURAL_MOLECULE,
  MAIN_FILE_NAME,
  PROLOG_MAIN_FILE,
}
import org.apache.commons.math3.random.RandomGenerator
import org.jpl7.{Atom, Query, Term, Variable}

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import scala.jdk.CollectionConverters.IteratorHasAsScala

class PrologPlacerManager[T, P <: Position[P]](
    environment: Environment[T, P],
    random: RandomGenerator,
    deploymentStrategy: String,
    simulationParameters: SimulationParameters,
) {
  private val solutionParser = new PrologSolutionParser()
  private val destinationDirectory = Files.createTempDirectory("prolog")
  private val mainFilePath = {
    Files.copy(
      ENERGY_SOURCE_DATA,
      destinationDirectory.resolve(ENERGY_SOURCE_FILE_NAME),
      StandardCopyOption.REPLACE_EXISTING,
    )

    Files.copy(
      PROLOG_MAIN_FILE,
      destinationDirectory.resolve(MAIN_FILE_NAME),
      StandardCopyOption.REPLACE_EXISTING,
    )
  }
  private lazy val placementPredicate = deploymentStrategy match {
    case "optimal"   => "opt"
    case "heuristic" => "heu"
  }
  private var isConsulted = false

  def updateTopology(): Unit = {
    val applicationDevice = nodesContainingMolecule(APPLICATION_MOLECULE)
    val infrastructuralDevice = nodesContainingMolecule(INFRASTRUCTURAL_MOLECULE)
    val deployment = generateDeployment(environment, random, applicationDevice, infrastructuralDevice, simulationParameters)
    val fileToWrite = destinationDirectory.resolve("data.pl")
    Files.write(fileToWrite, deployment.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def getNewDeployment: List[DeviceDeployment] = {
    updateTopology()
    if (!isConsulted) {
      val consultKnowledge = new Query("consult", Array[Term](new Atom(s"${mainFilePath.toAbsolutePath}")))
      require(consultKnowledge.hasSolution, "Cannot consult the knowledge base")
      isConsulted = true
    } else {
      val makeResult = new Query("make")
      require(makeResult.hasSolution, "Cannot make the knowledge base")
    }
    val queryResult = new Query("placeAll", Array[Term](new Atom(placementPredicate), new Variable("P")))
    require(queryResult.hasSolution, "Cannot find a solution for the deployment")
    val solution = queryResult.oneSolution().get("P")
    solutionParser.parseDeploymentSolution(solution)
  }

  def getFootprint(currentDeployment: DeviceDeployment): Footprint = {
    val placements = currentDeployment.placements.mkString(",")
    val queryResult = new Query(
      "footprint",
      Array[Term](
        Term.textToTerm(s"[$placements]"),
        new Variable("C"),
        new Variable("E"),
        Term.textToTerm("[]"),
      ),
    )
    require(queryResult.hasSolution, "Cannot find a solution for the footprint")
    val solution = queryResult.oneSolution()
    solutionParser.parseFootprint(solution.get("C"), solution.get("E"))
  }

  private def nodesContainingMolecule(molecule: SimpleMolecule): List[Node[T]] =
    environment.getNodes.iterator().asScala.toList.filter(_.contains(molecule))

  /** Utility class for parsing the Prolog solution. Extracts the overall deployment placements and the footprint.
    */
  private class PrologSolutionParser {
    private val placementPattern = """on\(([^,]+),\s*([^,]+),\s*([^)]+)\)""".r
    private val componentPattern = """([a-z]+)(\d+)""".r
    private val deviceSolutionPattern = """p\(([^,]+),\s*([\d.]+),\s*(\d+),\s*([\d.eE+-]+),\s*\[(.*?)]\)""".r

    /** {{{
      * on(c0, robot0, 0.5), on(b0, robot0, 2), on(a0, robot0, 0.25), on(s0, robot0, 0.25), on(kd0, robot0, 1),
      * on(c1, robot1, 0.5), on(b1, robot1, 2), on(a1, robot1, 0.25), on(s1, robot1, 0.25), on(kd1, robot0, 1),
      * }}}
      * @return
      *   the deployment footprint.
      */
    def parseFootprint(carbon: Term, energy: Term): Footprint =
      Footprint(carbon.doubleValue(), energy.doubleValue())

    def parseDeploymentSolution(solution: Term): List[DeviceDeployment] = {
      (for {
        deviceSolutionPattern(deviceId, carbon, _, energy, components) <- deviceSolutionPattern.findAllIn(solution.toString)
        id = componentPattern.findAllIn(deviceId).toList.head match {
          case componentPattern(_, id) => id.toInt
        }
        componentsDeployment = placementPattern.findAllIn(components).toList.map {
          case placementPattern(componentPattern(component, cId), componentPattern(device, dId), hw) =>
            Placement(Component(component, cId.toInt), PlaceDevice(device, dId.toInt), hw.toDouble)
        }
      } yield DeviceDeployment(id, carbon.toDouble, energy.toDouble, componentsDeployment)).toList
    }
  }
}

object PrologPlacerManager {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
  private val INFRASTRUCTURAL_MOLECULE = new SimpleMolecule("infrastructuralDevice")
  private val PROLOG_DIRECTORY = Path.of("src", "main", "resources", "prolog")
  private val MAIN_FILE_NAME = "main.pl"
  private val ENERGY_SOURCE_FILE_NAME = "energysourcedata.pl"
  private val PROLOG_MAIN_FILE = PROLOG_DIRECTORY.resolve(MAIN_FILE_NAME)
  private val ENERGY_SOURCE_DATA = PROLOG_DIRECTORY.resolve(ENERGY_SOURCE_FILE_NAME)
}
