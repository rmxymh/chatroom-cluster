package dev.chatroom

import java.io.File
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object Chatroom extends App {
  val config = ConfigFactory.defaultApplication()
  val system = ActorSystem("UserConsole", config)
  val userActor = system.actorOf(Props[UserActor], name="userActor")
  
  userActor ! Begin
}
