package dev.chatroom

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorIdentity
import akka.actor.Identify
import akka.actor.ReceiveTimeout
import akka.actor.Props
import akka.event.Logging


case class MessageFromConsole(message: String)

sealed trait ChatroomAction
case object GoOnline extends ChatroomAction
case object GoOffline extends ChatroomAction
case object SetupSystem extends ChatroomAction
case object Begin extends ChatroomAction
case class Reply(name: String, message: String) extends ChatroomAction
case class Speak(name: String, message: String) extends ChatroomAction
case object Shutdown extends ChatroomAction
case object AddChatParticipant extends ChatroomAction
case class RemoveChatParticipant(id: String) extends ChatroomAction
case object Register extends ChatroomAction

class UserActor extends Actor {
  
  val log = Logging(context.system, this)
  
  val chatManagerPath = "akka.tcp://Chatroom@127.0.0.1:6051/user/systemBoot/chatManager"
  val consoleActor = context.actorOf(Props[ConsoleActor], name="consoleActor")
  var name = "DefaultUser"
  
  sendIdentifyRequest()

  def sendIdentifyRequest(): Unit = {
    context.actorSelection(chatManagerPath) ! Identify(chatManagerPath)

    import context.dispatcher
    context.system.scheduler.scheduleOnce(3.seconds, self, ReceiveTimeout)
  }

  def receive = identifying

  def identifying: Actor.Receive = {
    case ActorIdentity(path, Some(chatManager)) =>
      context.watch(chatManager)
      context.become(active(chatManager))
      chatManager ! Register
      consoleActor ! EnableConsole(name)
    case ActorIdentity(path, None) => println("Remote actor not available: " + path)
    case ReceiveTimeout => sendIdentifyRequest()
    case _ => println("Not ready yet")
  }


  def active(chatManager: ActorRef): Actor.Receive = {
    case MessageFromConsole(message: String) =>
      log.debug("UserActor: Message From Console")
      if(message.startsWith("/")) {
        if(message.startsWith("/GoOnline")) {
          chatManager ! GoOnline
        } else if(message.startsWith("/GoOffline")) {
          chatManager ! GoOffline
        } else if(message.startsWith("/name")) {
          val tokens = message.split(" ")
          if(tokens.length > 1) {
            println("Username " + name + " is renamed as " + tokens(1))
            name = tokens(1)
          }
        } else if(message.startsWith("/AddChatParticipant")) {
          chatManager ! AddChatParticipant
        } else if(message.startsWith("/RemoveChatParticipant")) {
          val tokens = message.split(" ")
          if(tokens.length > 1) {
            val id = tokens(1)
            chatManager ! RemoveChatParticipant(id)
          }          
        }
      } else if(message.length() > 0) {
        chatManager ! Speak(name, message)
      }
      consoleActor ! EnableConsole(name)
      
    case Begin =>
      log.debug("UserActor: Begin")
      consoleActor ! EnableConsole(name)
      
    case Reply(senderName: String, message: String) =>
      log.debug("UserActor: Reply")
      // Dump to console
      println(senderName + " : " + message)
  }
}
