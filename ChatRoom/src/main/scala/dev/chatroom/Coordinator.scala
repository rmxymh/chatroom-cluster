package dev.chatroom

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

case class SetChatManager(actor: ActorRef)
case class ReportLocalNumChatParticipant(amount: Int)
case object RequestAddChatParticipant
case class RequestRemoveChatParticipant(id: String)
case class RequestSpeak(name: String, message: String)
case class RequestReply(name: String, message: String)

case object PeerGetNumChatParticipant
case class PeerReportNumChatParticipant(amount: Int)
case object PeerAddChatParticipant
case class PeerRemoveChatParticipant(id: String)
case class PeerSpeak(from: String, name: String, message: String)
case class PeerReply(from: String, name: String, message: String)


class Coordinator(baseId: String, chatManager: ActorRef) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  var peerCoordinators = Map[String, ActorSelection]()
  var peerNumChatParticipants = Map[String, Int]()
  var localNumChatParticipant = 0

  
  var requestSender: ActorRef = null
  
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }
  
  override def postStop(): Unit = cluster.unsubscribe(self)
  
  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
      val peer = context.actorSelection(member.address.toString + "/user/systemBoot/Coordinator")
      peerCoordinators += member.address.toString -> peer
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case ReportLocalNumChatParticipant(amount: Int) =>
      localNumChatParticipant = amount

    case RequestAddChatParticipant =>
      // FIXME
      peerNumChatParticipants = Map[String, Int]()
      peerCoordinators.foreach(f => {
         f._2 ! PeerGetNumChatParticipant
      })
      
    case RequestRemoveChatParticipant(id: String) =>
      log.info("RequestRemoveChatParticipant " + id)
      peerCoordinators.foreach(f => {
         f._2 ! PeerRemoveChatParticipant(id)
      })      
    case PeerGetNumChatParticipant =>
      log.debug("PeerGetNumChatParticipant")
      sender ! PeerReportNumChatParticipant(ChatParticipantInfo.participantNumber)
      
    case PeerReportNumChatParticipant(amount: Int) =>
      log.debug("PeerReportNumChatParticipant")
      if(peerNumChatParticipants.getOrElse(sender.path.address.toString, null) == null) {
        peerNumChatParticipants += sender.path.address.toString -> amount
      }
      if(peerNumChatParticipants.size == peerCoordinators.size) {
        var minVal = 20
        var selectedAddr: String = null
        peerNumChatParticipants.foreach(f => {
          if(f._2 < minVal) {
            minVal = f._2
            selectedAddr = f._1
          }
        })
      
        if(minVal >= 10) {
          chatManager ! CmdReply("System", "The limit of maximum chat participants is reached.") 
        } else if(selectedAddr == null) {
          chatManager ! CmdAddChatParticipant
        } else {
          if(peerCoordinators.getOrElse(selectedAddr, null) != null) {
            peerCoordinators(selectedAddr) ! PeerAddChatParticipant
          } else if(selectedAddr == self.path.address.toString) {
            self ! PeerAddChatParticipant
          }
        }
      }
      
    case PeerAddChatParticipant =>
      if(chatManager != null) {
        chatManager ! CmdAddChatParticipant
      }
    case PeerRemoveChatParticipant(id: String) =>
      val tokens = id.split("-")
      if(tokens.size > 1) {
        if(tokens(0) == baseId) {
        // Naming convention: This participant is kept by me
          if(chatManager != null) {
            chatManager ! CmdRemoveChatParticipant(id)
          }
        }
      }

    case RequestSpeak(name: String, message: String) =>
      peerCoordinators.foreach(f => {
         f._2 ! PeerSpeak(baseId, name, message)
      })
      
    case RequestReply(name: String, message: String) =>
      peerCoordinators.foreach(f => {
         f._2 ! PeerReply(baseId, name, message)
      })
      
    case PeerSpeak(from: String, name: String, message: String) =>
      chatManager ! CmdSpeak(name, message)
      
    case PeerReply(from: String, name: String, message: String) =>
      chatManager ! CmdReply(name, message)
      
    case _: MemberEvent => 
  }
}