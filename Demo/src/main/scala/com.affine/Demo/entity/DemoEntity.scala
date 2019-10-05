package com.affine.Demo.entity

import java.time.LocalDateTime
import java.util.UUID
import com.affine.Demo.data._
import scala.concurrent.Future

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import io.surfkit.typebus.bus.Publisher
import io.surfkit.typebus.entity.EntityDb

object DemoEntity {

  sealed trait Command
  // command
  final case class EntityCreateDemo(id: UUID, create: CreateDemo)(val replyTo: ActorRef[DemoCreated]) extends Command
  final case class EntityModifyState(state: DemoState)(val replyTo: ActorRef[DemoCompensatingActionPerformed]) extends Command
  // query
  final case class EntityGetDemo(get: GetDemo)(val replyTo: ActorRef[Demo]) extends Command
  final case class EntityGetState(id: UUID)(val replyTo: ActorRef[DemoState]) extends Command

  val entityTypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("DemoEntity")

  def behavior(entityId: String): Behavior[Command] =
    EventSourcedBehavior[Command, DemoEvent, DemoState](
    persistenceId = PersistenceId(entityId),
    emptyState =  DemoState(None),
    commandHandler,
    eventHandler)

  private val commandHandler: (DemoState, Command) => Effect[DemoEvent, DemoState] = { (state, command) =>
    command match {
      case x: EntityCreateDemo =>
        val id = x.id
        val entity = Demo(id, x.create.data)
        val created = DemoCreated(entity)
        Effect.persist(created).thenRun(_ => x.replyTo.tell(created))

      case x: EntityGetDemo =>
        state.entity.map(x.replyTo.tell)
        Effect.none

      case x: EntityGetState =>
        x.replyTo.tell(state)
        Effect.none

      case x: EntityModifyState =>
        val compensatingActionPerformed = DemoCompensatingActionPerformed(x.state)
        Effect.persist(compensatingActionPerformed).thenRun(_ => x.replyTo.tell(compensatingActionPerformed))

      case _ => Effect.unhandled
    }
  }

  private val eventHandler: (DemoState, DemoEvent) => DemoState = { (state, event) =>
    state match {
      case state: DemoState =>
        event match {
        case DemoCreated(module) =>
          DemoState(Some(module))
        case DemoCompensatingActionPerformed(newState) =>
          newState
        case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
      }
      case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}


class DemoEntityDatabase(system: ActorSystem[_], val producer: Publisher)(implicit val ex: ExecutionContext)
  extends DemoDatabase{
  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.actor.typed.scaladsl.adapter._
  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  override def typeKey = DemoEntity.entityTypeKey
  val sharding =  ClusterSharding(system)
  val psEntities: ActorRef[ShardingEnvelope[DemoEntity.Command]] =
    sharding.init(Entity(typeKey = typeKey,
      createBehavior = createEntity(DemoEntity.behavior)(system.toUntyped))
      .withSettings(ClusterShardingSettings(system)))

  def entity(id: String) =
    sharding.entityRefFor(DemoEntity.entityTypeKey, id)

  override def createDemo(x: CreateDemo): Future[DemoCreated] = {
    val id = UUID.randomUUID()
    entity(id.toString) ? DemoEntity.EntityCreateDemo(id, x)
  }

  override def getDemo(x: GetDemo): Future[Demo] =
    entity(x.id.toString) ? DemoEntity.EntityGetDemo(x)

  override def getState(id: String): Future[DemoState] =
    entity(id) ? DemoEntity.EntityGetState(UUID.fromString(id))

  override def modifyState(id: String, state: DemoState): Future[DemoState] =
    (entity(id) ? DemoEntity.EntityModifyState(state)).map(_.state)
}
