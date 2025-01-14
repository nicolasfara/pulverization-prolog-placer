package it.unibo.alchemist.model

import it.unibo.alchemist.model.SetupDevicesDeployment.{APPLICATION_MOLECULE, DEPLOYMENT_PROLOG_FILE, INFRASTRUCTURAL_MOLECULE, PROLOG_MAIN_FILE}
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.prolog.DeploymentGenerator.generateDeployment
import org.jpl7.{Atom, Query, Term, Variable}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.IteratorHasAsScala

class SetupDevicesDeployment[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {
  private var executed = false

  override protected def executeBeforeUpdateDistribution(): Unit = {
    if (!executed) {
      val applicationDevice = nodesContainingMolecule(APPLICATION_MOLECULE)
      val infrastructuralDevice = nodesContainingMolecule(INFRASTRUCTURAL_MOLECULE)
      val deployment = generateDeployment(environment, applicationDevice, infrastructuralDevice)
      Files.write(DEPLOYMENT_PROLOG_FILE, deployment.getBytes)

      val consultResult = new Query(
        "consult",
        Array[Term](
          new Atom(s"${PROLOG_MAIN_FILE.toAbsolutePath.toString}"),
        ),
      )
      println(s"Prolog file ${PROLOG_MAIN_FILE.toAbsolutePath.toString} consulted: ${consultResult.hasSolution}")
      // Query the knowledge base
      val queryResult = new Query(
        "placeAll",
        Array[Term](
          new Atom("heu"),
          new Variable("T"),
        )
      )
      while (queryResult.hasMoreSolutions) {
        val solution = queryResult.nextSolution().get("P")
        if (solution != null) {
          println(s"Solution: $solution")
        }
      }
    }
    executed = true
  }

  private def nodesContainingMolecule(molecule: SimpleMolecule): List[Node[T]] =
    environment.getNodes.iterator().asScala.toList.filter(_.contains(molecule))
}

object SetupDevicesDeployment {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
  private val INFRASTRUCTURAL_MOLECULE = new SimpleMolecule("infrastructuralDevice")
  private val PROLOG_DIRECTORY = Path.of("src", "main", "resources", "prolog")
  private val DEPLOYMENT_PROLOG_FILE = PROLOG_DIRECTORY.resolve("data.pl")
  private val PROLOG_MAIN_FILE = PROLOG_DIRECTORY.resolve("main.pl")
}
