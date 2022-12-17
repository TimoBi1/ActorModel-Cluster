package actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSub;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import message.ConstantMessages;
import message.PrimeResult;
import message.SegmentMessage;
import scala.concurrent.duration.Duration;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class PrimeClusterMasterActor extends AbstractActor implements ConstantMessages {
	
	private final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
	private List<SegmentMessage> availableSegments = new ArrayList<>();
	private final List<Long> primeResults = new ArrayList<>();
	private int resultCount = 0, lastIntervalStart = 0;
//	private static final int INTERVAL_SIZE = 5000000;
//	private static final int SEGMENT_COUNT = 500;
	
	private static final int INTERVAL_SIZE = 500000;
	private static final int SEGMENT_COUNT = 500;

   private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

   private static final Random RANDOM = new Random();

   public PrimeClusterMasterActor() {
   }

   private void scheduleWakeUp() {
      context().system().scheduler().scheduleOnce(
         Duration.create(5, TimeUnit.SECONDS),
         self(),
         MSG_WAKE_UP,
         context().dispatcher(),
         null
      );
   }

	@Override
	public Receive createReceive() {
		
		schedulePrimeCalculation();
		return receiveBuilder().matchEquals(MSG_PRIMES_GENERATED, msg -> {
			log.info("[Master] Scheduled wake-up!");
			mediator.tell(new akka.cluster.pubsub.DistributedPubSubMediator.Publish(TOPIC_WORKERS, MSG_WORK_AVAILABLE),
					self());
			schedulePrimeCalculation();
		}).matchEquals(MSG_GIVE_WORK, msg -> {
			// give worker a segment if available
			if (!availableSegments.isEmpty()) {
				sender().tell(availableSegments.remove(0), self());
			}
		}).match(PrimeResult.class, msg -> {
			handlePrimeResultReceived(msg);
			// give worker another segment if available
			if (!availableSegments.isEmpty()) {
				sender().tell(availableSegments.remove(0), self());
			}
			getContext().unwatch(sender());
		}).match(Terminated.class, msg -> log.info("Active worker crashed: " + msg.getActor())).build();

	}
	
	public void handlePrimeResultReceived(PrimeResult result) throws FileNotFoundException {
		
		primeResults.addAll(result.getResults());
		// höchste primzahl und Anzahl der Primzahlen ausgeben
		 System.out.println("Höchste Primzahl bisher: " + primeResults.get(primeResults.size() - 1));
         System.out.println("Anzahl: " + primeResults.size());
		if (++resultCount >= SEGMENT_COUNT) {
            Collections.sort(primeResults);
            
            final PrintWriter p = new PrintWriter("C:/Users/st212041/Documents/PrimeApp Cluster/output_actor_cluster.txt");
            p.println("Size: " + primeResults.size());
            primeResults.forEach(prime -> p.println(prime));
            p.close();
            System.out.println("Done.");
            System.out.println(new Timestamp(System.currentTimeMillis()));
            this.getContext().system().terminate();
        }
		
	}
	
	private List<SegmentMessage> createSegments() {
		// ToDo
		long intWidth = INTERVAL_SIZE / SEGMENT_COUNT; //= 100;

		List<SegmentMessage> segments = new ArrayList<>();
		for (long i = intWidth; i <= INTERVAL_SIZE; i += intWidth) {
			segments.add(new SegmentMessage(i - intWidth, i));
		}
		return segments;
	}

	
	private void schedulePrimeCalculation() {
		if (availableSegments.size() > 0) {
			System.err.print("Remaining segments: " + availableSegments.size() + " - No new segments were generated!\n");
		} else {
			availableSegments.addAll(createSegments());
		}
		context().system().scheduler().scheduleOnce(Duration.create(2000, TimeUnit.MILLISECONDS), self(),
				MSG_PRIMES_GENERATED, context().dispatcher(), null);
	}
}
