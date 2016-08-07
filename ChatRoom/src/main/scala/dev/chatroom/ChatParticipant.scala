package dev.chatroom

import akka.actor.Actor
import akka.event.Logging

class ChatParticipant(name: String) extends Actor {
  
  val log = Logging(context.system, this)
  
  def receive = {
    case Speak(senderName: String, message: String) =>
      log.debug("ChatParticipant(" + name + "): Speak")
      sender ! Reply(name, "echo " + message)
  }
  
}