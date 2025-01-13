package it.unibo.alchemist.model

import it.unibo.alchemist.model.SetupDevicesDeployment.{APPLICATION_MOLECULE, INFRASTRUCTURAL_MOLECULE}
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.prolog.DeploymentGenerator.generateDeployment

import java.nio.file.Files
import scala.jdk.CollectionConverters.IteratorHasAsScala

class SetupDevicesDeployment[T, P <: Position[P]](
    environment: Environment[T, P],
    timeDistribution: TimeDistribution[T],
) extends AbstractGlobalReaction[T, P](environment, timeDistribution) {

  override protected def executeBeforeUpdateDistribution(): Unit = {
    val applicationDevice = nodesContainingMolecule(APPLICATION_MOLECULE)
    val infrastructuralDevice = nodesContainingMolecule(INFRASTRUCTURAL_MOLECULE)
    val deployment = generateDeployment(environment, applicationDevice, infrastructuralDevice)
    val deploymentPrologFile = Files.createTempFile("deployment", ".pl")
    Files.write(deploymentPrologFile, deployment.getBytes)
    println(s"Deployment written to ${deploymentPrologFile.toAbsolutePath}")
  }

  private def nodesContainingMolecule(molecule: SimpleMolecule): List[Node[T]] =
    environment.getNodes.iterator().asScala.toList.filter(_.contains(molecule))
}

object SetupDevicesDeployment {
  private val APPLICATION_MOLECULE = new SimpleMolecule("applicationDevice")
  private val INFRASTRUCTURAL_MOLECULE = new SimpleMolecule("infrastructuralDevice")
}
