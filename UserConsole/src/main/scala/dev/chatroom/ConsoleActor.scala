package dev.chatroom

import akka.actor.Actor
import akka.event.Logging

case class EnableConsole(name: String)

class ConsoleActor extends Actor {
  
  val log = Logging(context.system, this)
  
  def receive = {
    case EnableConsole(name: String) =>
      log.debug("ConsoleActor: EnableConsole")
      val prompt = "(" + name + ")# "
      print(prompt)
      acceptUserInput
  }
  
  def acceptUserInput = {
    val msgs = io.Source.stdin.getLines.take(1)
    msgs.foreach { x => context.parent ! MessageFromConsole(x) }
  }
}