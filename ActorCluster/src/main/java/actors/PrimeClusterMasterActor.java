package actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSub;
import akka.event.Logging;
import akka.event.LoggingAdapter;
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
import java.util.concurrent.TimeUnit;

public class PrimeClusterMasterActor extends AbstractActor implements ConstantMessages {
	
	private final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
	private List<SegmentMessage> availableSegments = new ArrayList<>();
	private final List<Long> primeResults = new ArrayList<>();
	private int resultCount = 0;
	private static final int INTERVAL_SIZE = 500000;
	private static final int SEGMENT_COUNT = 500;
	
//	private static final int INTERVAL_SIZE = 2000000;
//	private static final int SEGMENT_COUNT = 500;

   private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

   private long start;

   public PrimeClusterMasterActor() {
	   // For calculating the duration of the program to get all primes from 0 to 500K | 0 t  2 million
	   System.out.println("Start: " + new Timestamp(System.currentTimeMillis()));
	   start = System.currentTimeMillis();
   }

	@Override
	public Receive createReceive() {
		
		schedulePrimeCalculation();
		return receiveBuilder().matchEquals(MSG_PRIMES_GENERATED, msg -> {
			log.info("[Master] Scheduled wake-up!");
			mediator.tell(new akka.cluster.pubsub.DistributedPubSubMediator.Publish(TOPIC_WORKERS, MSG_WORK_AVAILABLE),
					self());
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
	/***
	 * Add results to overall result list and check if every segment is done.
	 * If all segments are done, print the results to a txt file, print the duration to console and terminate the program
	 * @param result: PrimeResult received by a worker
	 * 
	 */
	
	public void handlePrimeResultReceived(PrimeResult result) throws FileNotFoundException {
		
		primeResults.addAll(result.getResults());
		// print highest prime number and number of primes yet
		 System.out.println("Höchste Primzahl bisher: " + primeResults.get(primeResults.size() - 1));
         System.out.println("Anzahl: " + primeResults.size());
		if (++resultCount >= SEGMENT_COUNT) {
            Collections.sort(primeResults);
            // txt file with the results for Windows Clutser
            final PrintWriter p = new PrintWriter("C:/Users/st212041/Documents/PrimeApp Cluster/output_actor_cluster.txt");
            // txt file for Kubernetes Cluster
//            final PrintWriter p = new PrintWriter("/bin/output_actor_cluster.txt");
            p.println("Size: " + primeResults.size());
            primeResults.forEach(prime -> p.println(prime));
            System.out.println("Done.");
            Long duration = System.currentTimeMillis() - start;
            p.println("Duration: " + duration  + " ms");
            p.close();
            System.out.println("Dauer: " + duration);
            System.out.println(new Timestamp(System.currentTimeMillis()));
            this.getContext().system().terminate();
        }
		
	}
	
	/***
	 * Creates the segments based on the given interval size and count of segments to be created.
	 * @return List with the generated segments
	 */
	private List<SegmentMessage> createSegments() {
		long intWidth = INTERVAL_SIZE / SEGMENT_COUNT;

		List<SegmentMessage> segments = new ArrayList<>();
		for (long i = intWidth; i <= INTERVAL_SIZE; i += intWidth) {
			segments.add(new SegmentMessage(i - intWidth, i));
		}
		return segments;
	}

	/***
	 * Checks if there are undone segments left. If so, create new Segments by calling createSegments().
	 * Repeats after first call every 2 seconds.
	 */
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
