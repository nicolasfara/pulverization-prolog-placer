package it.unibo.alchemist.model.implementations.actions

import it.unibo.alchemist.model.actions.AbstractLocalAction
import it.unibo.alchemist.model.implementations.actions.RunScafiProgram.NeighborData
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{CONTEXT, EXPORT, ID, Path, _}
import it.unibo.alchemist.model.{Action, Dependency, Environment, Molecule, Node, Position, Reaction, Time => AlchemistTime, _}
import it.unibo.alchemist.scala.PimpMyAlchemist._
import it.unibo.alchemist.utils.AlchemistScafiUtils.{alchemistTimeToNanos, buildContext}
import org.apache.commons.math3.random.RandomGenerator
import org.apache.commons.math3.util.FastMath
import org.kaikikm.threadresloader.ResourceLoader

import scala.collection.mutable
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IteratorHasAsScala}
import scala.util.{Failure, Try}

sealed abstract class RunScafiProgram[T, P <: Position[P]](node: Node[T]) extends AbstractLocalAction[T](node) {
  def asMolecule: Molecule = new SimpleMolecule(getClass.getSimpleName)
  def isComputationalCycleComplete: Boolean
  def programNameMolecule: Molecule
  def programDag: Map[String, Set[String]]
  def prepareForComputationalCycle(): Unit
}

object RunScafiProgram {
  case class NeighborData[P <: Position[P]](exportData: EXPORT, position: P, executionTime: AlchemistTime)

  implicit class RichMap[K, V](map: Map[K, V]) {
    def mapValuesStrict[T](f: V => T): Map[K, T] = map.map(tp => tp._1 -> f(tp._2))
  }
}

final class RunApplicationScafiProgram[T, P <: Position[P]](
    environment: Environment[T, P],
    val node: Node[T],
    reaction: Reaction[T],
    randomGenerator: RandomGenerator,
    programName: String,
    retentionTime: Double,
    programDagMapping: Map[String, Set[String]] = Map.empty,
) extends RunScafiProgram[T, P](node) {

  def this(
      environment: Environment[T, P],
      node: Node[T],
      reaction: Reaction[T],
      randomGenerator: RandomGenerator,
      programName: String,
  ) = this(environment, node, reaction, randomGenerator, programName, FastMath.nextUp(1.0 / reaction.getTimeDistribution.getRate))

  declareDependencyTo(Dependency.EVERY_MOLECULE)

  import RunScafiProgram.NeighborData
  val program = ResourceLoader
    .classForName(programName)
    .getDeclaredConstructor()
    .newInstance()
    .asInstanceOf[CONTEXT => EXPORT]
  override val programNameMolecule = new SimpleMolecule(programName)
  lazy val nodeManager = new SimpleNodeManager(node)
  private var completed = false

  // --------------------- Modularization-related properties
  override val programDag = programDagMapping
  private val applicationNeighborsCache = collection.mutable.Set[ID]()
  private var neighborhoodManager: Map[ID, NeighborData[P]] = Map()
  private val inputFromComponents = collection.mutable.Map[ID, mutable.Buffer[(Path, T)]]()
  private lazy val allocator: AllocatorProperty[T, P] = node.getProperties.asScala
    .find(_.isInstanceOf[AllocatorProperty[T, P]])
    .map(_.asInstanceOf[AllocatorProperty[T, P]])
    .getOrElse(throw new IllegalStateException(s"`AllocatorProperty` not found for node ${node.getId}"))

  override def cloneAction(node: Node[T], reaction: Reaction[T]) =
    new RunApplicationScafiProgram(environment, node, reaction, randomGenerator, programName, retentionTime)

  override def execute(): Unit = {
    import scala.jdk.CollectionConverters._
    val position: P = environment.getPosition(node)
    // NB: We assume it.unibo.alchemist.model.Time = DoubleTime
    //     and that its "time unit" is seconds, and then we get NANOSECONDS
    val alchemistCurrentTime = Try(environment.getSimulation)
      .map(_.getTime)
      .orElse(Failure(new IllegalStateException("The simulation is uninitialized (did you serialize the environment?)")))
      .get
    val currentTime: Long = alchemistTimeToNanos(alchemistCurrentTime)
    manageRetentionMessages(alchemistCurrentTime)

    // ----- Create context
    // Add self node to the neighborhood manager
    neighborhoodManager = neighborhoodManager.updatedWith(node.getId) {
      case value @ Some(_) => value
      case None            => Some(NeighborData(factory.emptyExport(), position, Double.NaN))
    }
    val deltaTime: Long =
      currentTime - neighborhoodManager.get(node.getId).map(d => alchemistTimeToNanos(d.executionTime)).getOrElse(0L)
    val localSensors = node.getContents.asScala.map { case (k, v) => k.getName -> v }
    val neighborhoodSensors = scala.collection.mutable.Map[CNAME, Map[ID, Any]]()
    val exports: Iterable[(ID, EXPORT)] = neighborhoodManager.view.mapValues(_.exportData)
    val context = buildContext(
      environment,
      exports,
      localSensors.toMap,
      neighborhoodSensors,
      alchemistCurrentTime,
      deltaTime,
      currentTime,
      position,
      node,
      neighborhoodManager,
      nodeManager,
      randomGenerator,
    )

    mergeInputFromComponentsWithExport()

    // ----- Check if the program is offloaded to a surrogate or not
    if (isOffloadedToSurrogate) {
      val surrogateDeviceId = allocator
        .getDeviceIdForComponent(programName)
        .getOrElse(throw new IllegalStateException("The program is offloaded to a surrogate, but the target device is not found"))
      val surrogateNode = environment.getNodeByID(surrogateDeviceId)
      // Throw an exception if the surrogate is not in the neighborhood!
      if (!isInNeighbors(surrogateNode))
        throw new IllegalStateException(s"Surrogate $surrogateDeviceId is not in the neighborhood of node ${node.getId}")
      val surrogateProgram = SurrogateScafiIncarnation
        .allScafiProgramsForType(surrogateNode, classOf[RunSurrogateScafiProgram[T, P]])
        .map(_.asInstanceOf[RunSurrogateScafiProgram[T, P]])
        .find(_.programNameMolecule == programNameMolecule)
        .getOrElse(throw new IllegalStateException(s"Unable to find `RunSurrogateScafiProgram` for the node $surrogateDeviceId"))
      surrogateProgram.setSurrogateFor(node.getId)
      surrogateProgram.setContextFor(node.getId, context)
    } else {
      // Execute normal program since is executed locally
      val computed = program(context)
      val toSend = NeighborData(computed, position, alchemistCurrentTime)
      neighborhoodManager = neighborhoodManager + (node.getId -> toSend)
    }
    // Write the output of the program to the node
    for {
      programResult <- neighborhoodManager.get(node.getId)
      result <- programResult.exportData.get[T](factory.emptyPath())
    } node.setConcentration(programNameMolecule, result)
    completed = true
  }

  private def isInNeighbors(surrogateNode: Node[T]): Boolean = {
    val neighborsNodes = environment.getNeighborhood(node).getNeighbors.iterator().asScala.map(_.getId).toSet
    neighborsNodes.contains(surrogateNode.getId)
  }

  /** Returns the application network neighborhood of the current node.
    *
    * It caches the result to avoid recomputing it multiple times.
    */
  private def currentApplicativeNeighborhood: Set[ID] = {
    val neighborsNodes = environment.getNeighborhood(node).getNeighbors.iterator().asScala.map(_.getId).toSet
    val (alreadyCached, unknownNeighbors) = neighborsNodes.partition(applicationNeighborsCache.contains)
    val newApplicationNeighbors = unknownNeighbors
      .map(id => {
        SurrogateScafiIncarnation
          .allScafiProgramsForType(environment.getNodeByID(id), classOf[RunApplicationScafiProgram[T, P]])
          .map(_.asInstanceOf[RunApplicationScafiProgram[T, P]])
          .find(_.programNameMolecule == programNameMolecule)
      })
      .collect { case Some(program) => program }
      .map(_.node.getId)
    applicationNeighborsCache.addAll(newApplicationNeighbors)
    alreadyCached ++ newApplicationNeighbors
  }

  def sendExport(id: ID, exportData: NeighborData[P]): Unit = {
    neighborhoodManager += id -> exportData
  }

  def getExport(id: ID): Option[NeighborData[P]] = neighborhoodManager.get(id)

  def isComputationalCycleComplete: Boolean = completed

  override def prepareForComputationalCycle(): Unit = completed = false

  def feedInputFromNode(node: ID, value: (Path, T)): Unit = {
    inputFromComponents.get(node) match {
      case Some(inputs) =>
        val newInputs = inputs.filter(!_._1.matches(value._1))
        inputFromComponents += node -> (newInputs += value)
      case None => inputFromComponents += node -> mutable.Buffer(value)
    }
  }

  def generateComponentOutputField(): (Path, Option[T]) = {
    val path = factory.path(Scope(programName))
    val result = neighborhoodManager(node.getId).exportData.get[T](factory.emptyPath())
    path -> result
  }

  private def isOffloadedToSurrogate: Boolean = allocator.isOffloaded(programName, node)

  private def mergeInputFromComponentsWithExport(): Unit = {
    for {
      (nodeId, inputs) <- inputFromComponents
      export <- neighborhoodManager.get(nodeId).map(_.exportData)
      (path, value) <- inputs
    } export.put(path, value)
  }

  private def manageRetentionMessages(currentTime: AlchemistTime): Unit = {
    neighborhoodManager = neighborhoodManager.filter { case (id, data) =>
      id == node.getId || data.executionTime >= currentTime - retentionTime
    }
  }
}

final class RunSurrogateScafiProgram[T, P <: Position[P]](
    environment: Environment[T, P],
    val node: Node[T],
    reaction: Reaction[T],
    randomGenerator: RandomGenerator,
    programName: String,
    retentionTime: Double,
    programDagMapping: Map[String, Set[String]] = Map.empty,
) extends RunScafiProgram[T, P](node) {

  def this(
      environment: Environment[T, P],
      node: Node[T],
      reaction: Reaction[T],
      randomGenerator: RandomGenerator,
      programName: String,
  ) = this(environment, node, reaction, randomGenerator, programName, FastMath.nextUp(1.0 / reaction.getTimeDistribution.getRate))

  private var completed = false
  declareDependencyTo(Dependency.EVERY_MOLECULE)

  val program = ResourceLoader
    .classForName(programName)
    .getDeclaredConstructor()
    .newInstance()
    .asInstanceOf[CONTEXT => EXPORT]
  override val programNameMolecule = new SimpleMolecule(programName)

  // --------------------- Modularization-related properties
  override val programDag = programDagMapping
  private val contextManager = collection.mutable.Map[ID, CONTEXT]()
  private val surrogateForNodes = collection.mutable.Set[ID]()
  private val computedResults = collection.mutable.Map[ID, NeighborData[P]]()
  private val applicationNeighborsCache = collection.mutable.Set[ID]()

  override def cloneAction(node: Node[T], reaction: Reaction[T]): Action[T] =
    new RunSurrogateScafiProgram(environment, node, reaction, randomGenerator, programName, retentionTime)

  override def execute(): Unit = {
    val alchemistCurrentTime = Try(environment.getSimulation)
      .map(_.getTime)
      .orElse(Failure(new IllegalStateException("The simulation is uninitialized (did you serialize the environment?)")))
      .get
    // Clean the surrogateForNodes according to the retention time
    surrogateForNodes.filterInPlace(activeApplicationNeighborDevices.contains)
    // Run the program for each node offloading the computation to this surrogate
    surrogateForNodes.foreach(deviceId => {
      contextManager.get(deviceId) match {
        case Some(contextNode) =>
          val computedResult = program(contextNode)
          val nodePosition = environment.getPosition(environment.getNodeByID(deviceId))
          val toSend = NeighborData(computedResult, nodePosition, alchemistCurrentTime)
          computedResults(deviceId) = toSend
        case None => ()
      }
    })
    node.setConcentration(new SimpleMolecule(s"SurrogateFor[$programName]"), isSurrogateFor.toList.sorted.asInstanceOf[T])
    node.setConcentration(new SimpleMolecule("ComponentsExecutionCount"), surrogateForNodes.size.toDouble.asInstanceOf[T])
    completed = true
  }

  private def activeApplicationNeighborDevices: Set[ID] = {
    val neighborsNodes = environment.getNeighborhood(node).getNeighbors.iterator().asScala.map(_.getId).toSet
    val (alreadyCached, unknownNeighbors) = neighborsNodes.partition(applicationNeighborsCache.contains)
    val newApplicationNeighbors = unknownNeighbors
      .map(id => {
        SurrogateScafiIncarnation
          .allScafiProgramsForType(environment.getNodeByID(id), classOf[RunApplicationScafiProgram[T, P]])
          .map(_.asInstanceOf[RunApplicationScafiProgram[T, P]])
          .find(_.programNameMolecule == programNameMolecule)
      })
      .collect { case Some(program) => program }
      .map(_.node.getId)
    applicationNeighborsCache.addAll(newApplicationNeighbors)
    alreadyCached ++ newApplicationNeighbors
  }

  def setSurrogateFor(nodeId: ID): Unit = surrogateForNodes.add(nodeId)

  def removeSurrogateFor(nodeId: ID): Unit = {
    surrogateForNodes.remove(nodeId)
    contextManager.remove(nodeId)
  }

  def isSurrogateFor: Set[ID] = surrogateForNodes.toSet

  def setContextFor(nodeId: ID, context: CONTEXT): Unit = contextManager.put(nodeId, context)

  def getComputedResultFor(nodeId: ID): Option[NeighborData[P]] = computedResults.get(nodeId)

  def isComputationalCycleComplete: Boolean = completed

  override def prepareForComputationalCycle(): Unit = completed = false
}
