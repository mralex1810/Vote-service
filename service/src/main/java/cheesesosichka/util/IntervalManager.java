package chessesosicka.vesdecodbackend.util;

public class IntervalManager {
    final private long from;
    final private long to;
    final private long intervals;

    public IntervalManager(long from, long to, long intervals) {
        this.from = from;
        this.to = to;
        this.intervals = Math.min(intervals, to - from);
    }

    long delay() {
        return (to - from) / intervals;
    }

    public long[] getInterval(long interval) {
        if (interval == intervals - 1) {
            return new long[]{from + interval * delay(), to};
        }
        return new long[]{from + interval * delay(), from + (interval + 1) * delay()};
    }

    public long countIntervals() {
        return intervals;
    }
}
