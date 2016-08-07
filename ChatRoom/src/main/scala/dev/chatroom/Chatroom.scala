package dev.chatroom

import java.io.File
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object Chatroom {
  
  def main(args: Array[String]): Unit = {
  
    var port = "6051"
    if(! args.isEmpty) {
      port = args(0)
    }
    
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load())
    val system = ActorSystem("Chatroom", config)
    val systemBoot = system.actorOf(Props(new SystemBoot(port)), name="systemBoot")
  
    systemBoot ! SystemBoot.Boot
  }
}