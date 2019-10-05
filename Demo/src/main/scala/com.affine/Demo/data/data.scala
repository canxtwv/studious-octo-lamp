package com.affine.Demo

import java.util.UUID
import io.surfkit.typebus._
import io.surfkit.typebus.event.DbAccessor
import io.surfkit.typebus.entity.EntityDb
import scala.concurrent.Future

package object data {


  sealed trait DemoCommand
  case class CreateDemo(data: String) extends DemoCommand
  case class GetDemo(id: UUID) extends DemoCommand
  case class GetDemoEntityState(id: String) extends DemoCommand with DbAccessor

  sealed trait DemoEvent
  case class DemoCreated(entity: Demo) extends DemoEvent
  case class DemoCompensatingActionPerformed(state: DemoState) extends DemoEvent
  case class DemoState(entity: Option[Demo])
  case class Demo(id: UUID, data: String)

  object Implicits extends AvroByteStreams{
    implicit val createDemoRW = Typebus.declareType[CreateDemo, AvroByteStreamReader[CreateDemo], AvroByteStreamWriter[CreateDemo]]
    implicit val DemoCreatedRW = Typebus.declareType[DemoCreated, AvroByteStreamReader[DemoCreated], AvroByteStreamWriter[DemoCreated]]
    implicit val DemoRW = Typebus.declareType[Demo, AvroByteStreamReader[Demo], AvroByteStreamWriter[Demo]]
    implicit val getDemoRW = Typebus.declareType[GetDemo, AvroByteStreamReader[GetDemo], AvroByteStreamWriter[GetDemo]]
    implicit val getDemoEntityStateRW = Typebus.declareType[GetDemoEntityState, AvroByteStreamReader[GetDemoEntityState], AvroByteStreamWriter[GetDemoEntityState]]
    implicit val DemoStateRW = Typebus.declareType[DemoState, AvroByteStreamReader[DemoState], AvroByteStreamWriter[DemoState]]
  }

  trait DemoDatabase extends EntityDb[DemoState]{
    def createDemo(x: CreateDemo): Future[DemoCreated]
    def getDemo(x: GetDemo): Future[Demo]
  }
}



