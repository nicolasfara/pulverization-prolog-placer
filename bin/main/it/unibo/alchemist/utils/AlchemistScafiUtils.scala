package it.unibo.alchemist.utils

import it.unibo.alchemist.model.implementations.actions.RunScafiProgram.{NeighborData, RichMap}
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{CNAME, CONTEXT, ContextImpl, EXPORT, ID, LSNS_ALCHEMIST_COORDINATES, LSNS_ALCHEMIST_DELTA_TIME, LSNS_ALCHEMIST_ENVIRONMENT, LSNS_ALCHEMIST_NODE_MANAGER, LSNS_ALCHEMIST_RANDOM, LSNS_ALCHEMIST_TIMESTAMP, NBR_ALCHEMIST_DELAY, NBR_ALCHEMIST_LAG}
import it.unibo.alchemist.model.{Action, Environment, Node, NodeProperty, Position, Time => AlchemistTime}
import it.unibo.alchemist.scala.PimpMyAlchemist._
import it.unibo.scafi.space.Point3D
import org.apache.commons.math3.random.RandomGenerator

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.reflect.ClassTag

object AlchemistScafiUtils {
  private val commonNames = new ScafiIncarnationForAlchemist.StandardSensorNames {}

  def getNeighborsWithProgram[T, P <: Position[P], PG <: Action[T]: ClassTag](node: Node[T])(implicit env: Environment[T, P]): List[Node[T]] = {
    (for {
      node <- env.getNeighborhood(node).asScala
      reaction <- node.getReactions.asScala
      action <- reaction.getActions.asScala
      if implicitly[ClassTag[PG]].runtimeClass.isInstance(action)
    } yield node).toList
  }

  def getAlchemistActions[K, T, P <: Position[P]](environment: Environment[T, P], nodeID: Int, clazz: Class[K]): Iterable[K] = {
    environment
      .getNodeByID(nodeID)
      .getReactions
      .asScala
      .flatMap(_.getActions.asScala)
      .filter(clazz.isInstance(_))
      .map(_.asInstanceOf[K])
  }

  def getNodeProperty[T, P <: Position[P], Prop <: NodeProperty[T]](node: Node[T], clazz: Class[Prop]): Prop = {
    node.getProperties.asScala
      .find(clazz.isInstance(_))
      .map(_.asInstanceOf[Prop])
      .getOrElse(throw new NoSuchElementException(s"Node ${node.getId} does not have a property of type ${clazz.getName}"))
  }

  implicit def euclideanToPoint[P <: Position[P]](point: P): Point3D = point.getDimensions match {
    case 1 => Point3D(point.getCoordinate(0), 0, 0)
    case 2 => Point3D(point.getCoordinate(0), point.getCoordinate(1), 0)
    case 3 => Point3D(point.getCoordinate(0), point.getCoordinate(1), point.getCoordinate(2))
  }

  def alchemistTimeToNanos(time: AlchemistTime): Long = (time.toDouble * 1_000_000_000).toLong
  def buildContext[T, P <: Position[P]](
      environment: Environment[T, P],
      exports: Iterable[(ID, EXPORT)],
      localSensors: Map[String, T],
      neighborhoodSensors: scala.collection.mutable.Map[CNAME, Map[ID, Any]],
      alchemistCurrentTime: AlchemistTime,
      deltaTime: Long,
      currentTime: Long,
      position: P,
      node: Node[T],
      neighborhoodManager: Map[ID, NeighborData[P]],
      nodeManager: SimpleNodeManager[T],
      randomGenerator: RandomGenerator,
  ): CONTEXT = new ContextImpl(node.getId, exports, localSensors, Map.empty) {
    override def nbrSense[TT](nsns: CNAME)(nbr: ID): Option[TT] =
      neighborhoodSensors
        .getOrElseUpdate(
          nsns,
          nsns match {
            case commonNames.NBR_LAG =>
              neighborhoodManager.mapValuesStrict[FiniteDuration](nbr =>
                FiniteDuration(alchemistTimeToNanos(alchemistCurrentTime - nbr.executionTime), TimeUnit.NANOSECONDS),
              )
            /*
             * nbrDelay is estimated: it should be nbr(deltaTime), here we suppose the round frequency
             * is negligibly different between devices.
             */
            case commonNames.NBR_DELAY =>
              neighborhoodManager.mapValuesStrict[FiniteDuration](nbr =>
                FiniteDuration(
                  alchemistTimeToNanos(nbr.executionTime) + deltaTime - currentTime,
                  TimeUnit.NANOSECONDS,
                ),
              )
            case commonNames.NBR_RANGE => neighborhoodManager.mapValuesStrict[Double](_.position.distanceTo(position))
            case commonNames.NBR_VECTOR =>
              neighborhoodManager.mapValuesStrict[Point3D](_.position.minus(position.getCoordinates))
            case NBR_ALCHEMIST_LAG =>
              neighborhoodManager.mapValuesStrict[Double](alchemistCurrentTime - _.executionTime)
            case NBR_ALCHEMIST_DELAY =>
              neighborhoodManager.mapValuesStrict(nbr => alchemistTimeToNanos(nbr.executionTime) + deltaTime - currentTime)
          },
        )
        .get(nbr)
        .map(_.asInstanceOf[TT])

    override def sense[TT](lsns: String): Option[TT] = (lsns match {
      case LSNS_ALCHEMIST_COORDINATES  => Some(position.getCoordinates)
      case commonNames.LSNS_DELTA_TIME => Some(FiniteDuration(deltaTime, TimeUnit.NANOSECONDS))
      case commonNames.LSNS_POSITION =>
        val k = position.getDimensions
        Some(
          Point3D(
            position.getCoordinate(0),
            if (k >= 2) position.getCoordinate(1) else 0,
            if (k >= 3) position.getCoordinate(2) else 0,
          ),
        )
      case commonNames.LSNS_TIMESTAMP  => Some(currentTime)
      case commonNames.LSNS_TIME       => Some(java.time.Instant.ofEpochMilli((alchemistCurrentTime * 1000).toLong))
      case LSNS_ALCHEMIST_NODE_MANAGER => Some(nodeManager)
      case LSNS_ALCHEMIST_DELTA_TIME =>
        Some(
          alchemistCurrentTime.minus(
            neighborhoodManager.get(node.getId).map(_.executionTime).getOrElse(AlchemistTime.INFINITY),
          ),
        )
      case LSNS_ALCHEMIST_ENVIRONMENT => Some(environment)
      case LSNS_ALCHEMIST_RANDOM      => Some(randomGenerator)
      case LSNS_ALCHEMIST_TIMESTAMP   => Some(alchemistCurrentTime)
      case _                          => localSensors.get(lsns)
    }).map(_.asInstanceOf[TT])
  }
}
