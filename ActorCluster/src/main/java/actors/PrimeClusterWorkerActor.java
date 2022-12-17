package actors;

import java.util.ArrayList;
import java.util.List;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.dispatch.Futures;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import message.ConstantMessages;
import message.PrimeResult;
import message.SegmentMessage;

public class PrimeClusterWorkerActor extends AbstractActor implements ConstantMessages {

   private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

   private final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();

   private boolean working = false;

   public PrimeClusterWorkerActor() {
   }
   
   @Override
   public Receive createReceive() {
    return receiveBuilder().matchEquals(MSG_WORK_AVAILABLE, msg -> {
    	sender().tell(MSG_GIVE_WORK, self());
    }).match(SegmentMessage.class, msg -> {
	    // receive partition from PrimeMaster
	    List<Long> primes = new ArrayList<>();
	    for (long i = ((SegmentMessage) msg).getStart(); i < ((SegmentMessage) msg).getEnd(); i++) {
		    // add to result, if element is a prime number
		    if (isPrime(i)) {
		    	primes.add(i);
		    }
	    }
	    // send resulting subset of primes to PrimeMaster
	    // primes.forEach(p -> System.out.println(p));
	    this.getSender().tell(new PrimeResult(primes), this.getSelf());
    }).match(DistributedPubSubMediator.SubscribeAck.class, msg ->
    	log.info("Subscribed to 'workers'!")
    ).build();
   }

   @Override
   public void preStart() {
      mediator.tell(new DistributedPubSubMediator.Subscribe(TOPIC_WORKERS, self()), self());
   }

   @Override
   public void postStop() {
      mediator.tell(new DistributedPubSubMediator.Unsubscribe(TOPIC_WORKERS, self()), self());
   }
   
   /**
    * @return whether n is a prime number
    */
   private boolean isPrime(long n) {

       if (n <= 1) {
           return false;
       }

       for (int j = 2; j <= n / 2; j++) {
           if (n % j == 0) {
               return false;
           }
       }
       return true;
   }
}
