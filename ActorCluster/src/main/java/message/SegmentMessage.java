package message;

import java.io.Serializable;

import akka.actor.AbstractActor;

public class SegmentMessage implements Serializable {

    private long start;

    private long end;

    private AbstractActor actor;

    public SegmentMessage(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public SegmentMessage(long start, long end, AbstractActor actor) {
        this.start = start;
        this.end = end;
        this.actor = actor;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

	public AbstractActor getActor() {
		return actor;
	}

	public void setActor(AbstractActor actor) {
		this.actor = actor;
	}
}
