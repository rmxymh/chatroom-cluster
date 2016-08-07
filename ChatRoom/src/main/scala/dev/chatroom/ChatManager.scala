package dev.chatroom

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.util.Calendar

import akka.actor.Actor
import akka.actor.FSM
import akka.actor.ActorRef
import akka.actor.Props
import akka.event.Logging

sealed trait ChatroomState
case object Online extends ChatroomState
case object Offline extends ChatroomState

sealed trait ChatroomAction
case object GoOnline extends ChatroomAction
case object GoOffline extends ChatroomAction
case class SetupSystem(id: String) extends ChatroomAction
case object Begin extends ChatroomAction
case class Reply(name: String, message: String) extends ChatroomAction
case class Speak(name: String, message: String) extends ChatroomAction
case class CmdReply(name: String, message: String) extends ChatroomAction
case class CmdSpeak(name: String, message: String) extends ChatroomAction
case object Shutdown extends ChatroomAction
case object AddChatParticipant extends ChatroomAction
case class RemoveChatParticipant(id: String) extends ChatroomAction
case object CmdAddChatParticipant extends ChatroomAction
case class CmdRemoveChatParticipant(id: String) extends ChatroomAction
case object Register extends ChatroomAction
case class SetCoordinator(actor: ActorRef) extends ChatroomAction
case object GetNumChatParticipant extends ChatroomAction

object ChatParticipantInfo {
  var participantNumber = 0
}

class ChatManager extends Actor with FSM[ChatroomState, ChatroomAction] {
  
  //val log = Logging(context.system, this)
  val chatlogPath = "chat.log"
  var chatlog:BufferedWriter = null

  val maxParticipant = 3
  var currentParticipantId = 1
  var chatParticipants = Map[String, ActorRef]()
  var userActor: ActorRef = null
  var coordinator : ActorRef = null
  var baseId: String = "0"
  
  def initChatlog() {
    if(chatlog == null) {
      val file = new File(chatlogPath); 
      chatlog = new BufferedWriter(new FileWriter(file.getAbsoluteFile()))
      log.info("Write chat log at " + file.getAbsolutePath())
    }
  }
  
  def finalizeChatlog() {
    if(chatlog != null) {
      chatlog.close()
    }
  }
  
  def chat(name: String, message: String) {
    if(chatlog != null) {
      val time = Calendar.getInstance().getTime()
      val logMsg = time + " " + name + " > " + message
      chatlog.write(logMsg)
      chatlog.newLine()
      chatlog.flush()
    }
  }
  
  def addChatParticipant(id: String) {
    log.debug("** Add ChatParticipant " + baseId + "-" + id)
    val bootname = "%Participant%(" + baseId + "-" + id + ")"
    val actorname = "Participant" + baseId + "-" + id
    chatParticipants += actorname -> context.actorOf(Props(new ChatParticipant(bootname)), name=actorname)
    currentParticipantId += 1
    ChatParticipantInfo.participantNumber += 1
  }
  
  def removeChatParticipant(id: String): Boolean = {
    val actorname = "Participant" + id.toString()
    val actorref = chatParticipants.getOrElse(actorname, null)
    
    if(actorref != null) {
      chatParticipants -= actorname
      context stop actorref
      ChatParticipantInfo.participantNumber -= 1
      true
    } else {
      false
    }
  }
  
  startWith(Offline, GoOffline)
  
  when(Offline) {
    case Event(SetupSystem(id: String), _) => 
      log.debug("* ChatManager: SetupSystem")
      baseId = id
      for( x <- 1 to maxParticipant ) {
        addChatParticipant(x.toString())
      }
      log.debug("** ChatManager: Setup User Actor")
      
      initChatlog
      goto(Online)
      
    case Event(GoOnline, _) => 
      log.debug("** ChatManager: GoOnline")
      initChatlog
      goto(Online)
      
    case Event(Shutdown, _) => 
      log.debug("** ChatManager: Shutdown")
      finalizeChatlog
      stay
      
    case Event(Speak(name: String, message: String), _) =>
      if(userActor != null) {
        userActor ! Reply("System", "Chatroom is offline.")
      }
      stay
      
    case Event(AddChatParticipant, _) =>
      if(coordinator != null) {
        coordinator ! RequestAddChatParticipant
      }
      stay
      
    case Event(RemoveChatParticipant(id: String), _) =>
      if(coordinator != null) {
        coordinator ! RequestRemoveChatParticipant(id)
      }
      stay

    case Event(CmdAddChatParticipant, _) =>
      val newParticipantId = currentParticipantId
      addChatParticipant(newParticipantId.toString())
      val fullId = baseId + "-" + newParticipantId.toString
      coordinator ! RequestReply("System", "New Chat Participant " + fullId + " login")
      stay
      
    case Event(CmdRemoveChatParticipant(id: String), _) =>
      if(removeChatParticipant(id)) {
        coordinator ! RequestReply("System", "Chat Participant " + id + " logout")
      }
      stay
      
    case Event(Register, _) =>
      userActor = sender
      stay
      
    case Event(SetCoordinator(actor: ActorRef), _) =>
      coordinator = actor
      stay
      
    case Event(GetNumChatParticipant, _) =>
      sender ! ReportLocalNumChatParticipant(chatParticipants.size)
      stay
  }
  

  when(Online) {      
    case Event(Speak(name: String, message: String), _) => 
      log.debug("** ChatManager: Speak(" + name + "): " + message)

      coordinator ! RequestSpeak(name, message)
      stay
      
    case Event(Reply(name: String, message: String), _) => 
      log.debug("** ChatManager: Reply")

      coordinator ! RequestReply(name, message)
      stay

    case Event(CmdSpeak(name: String, message: String), _) => 
      log.debug("** ChatManager: Speak(" + name + "): " + message)
      
      chat(name, message)
      
      chatParticipants.foreach { participant => participant._2 ! Speak(name, message) }
      stay
      
    case Event(CmdReply(name: String, message: String), _) => 
      log.debug("** ChatManager: Reply")
      
      chat(name, message)
      
      if(userActor != null) {
        userActor ! Reply(name, message)
      }
      stay
      
    case Event(GoOffline, _) => 
      log.debug("** ChatManager: GoOffline")
      finalizeChatlog
      goto(Offline)
      
    case Event(Shutdown, _) => 
      log.debug("** ChatManager: Shutdown")
      finalizeChatlog
      goto(Offline)
      
    case Event(AddChatParticipant, _) =>
      if(coordinator != null) {
        coordinator ! RequestAddChatParticipant
      }
      stay
      
    case Event(RemoveChatParticipant(id: String), _) =>
      if(coordinator != null) {
        coordinator ! RequestRemoveChatParticipant(id)
      }
      stay

    case Event(CmdAddChatParticipant, _) =>
      val newParticipantId = currentParticipantId
      addChatParticipant(newParticipantId.toString())
      val fullId = baseId + "-" + newParticipantId.toString
      coordinator ! RequestReply("System", "New Chat Participant " + fullId + " login")
      stay
      
    case Event(CmdRemoveChatParticipant(id: String), _) =>
      if(removeChatParticipant(id)) {
        coordinator ! RequestReply("System", "Chat Participant " + id + " logout")
      } else {
        coordinator ! RequestReply("System", "Chat Participant " + id + " is not found")
      }
      stay

    case Event(Register, _) =>
      userActor = sender
      stay
      
    case Event(SetCoordinator(actor: ActorRef), _) =>
      coordinator = actor
      stay
      
    case Event(GetNumChatParticipant, _) =>
      sender ! ReportLocalNumChatParticipant(chatParticipants.size)
      stay
  }
  
  initialize()
}
