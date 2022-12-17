package main;

import java.util.UUID;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import actors.PrimeClusterMasterActor;
import actors.PrimeClusterWorkerActor;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.routing.RandomPool;

public class PrimeClusterApp {

	public static void main(String[] args) {
		boolean createMaster = true;
		String port = "1337";

		if (args.length > 0) {
			System.out.println(args[0]);
			System.setProperty("akka.remote.netty.tcp.port", args[0]);
			port = args[0];
			createMaster = false;
		}

		// Create an Akka system
		Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" +
		 Integer.parseInt(port)).withFallback(ConfigFactory.load());
//		ActorSystem actorSystem = ActorSystem.create("ActorCluster");
		ActorSystem actorSystem = ActorSystem.create("ActorCluster", config);
		
		if(createMaster){
			//Create Master
			actorSystem.actorOf(ClusterSingletonManager.props(Props.
				create(PrimeClusterMasterActor.class), PoisonPill.getInstance(),
				ClusterSingletonManagerSettings.create(actorSystem)), "master");
		}
		
		//Create RandomPool of 2 Workers
		actorSystem.actorOf(new RandomPool(2).props(Props.create(
		PrimeClusterWorkerActor.class)), "W_" + UUID.randomUUID());



		
	}

}
