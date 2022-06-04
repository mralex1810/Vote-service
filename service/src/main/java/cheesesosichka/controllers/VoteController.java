package chessesosicka.vesdecodbackend.controllers;

import chessesosicka.vesdecodbackend.util.IntervalManager;
import chessesosicka.vesdecodbackend.data.Vote;
import io.github.bucket4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/votes")
public class VoteController {
    @Value("${BUCKET_LIMITS}")
    private Long BUCKET_LIMITS;
    @Value("${BUCKET_REFILL}")
    private Long BUCKET_REFILL;
    private Long oldestVote;
    private Long newestVote;
    private final Predicate<String> PHONE_PREDICATE = Pattern.compile("9\\d{9}").asMatchPredicate();
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Map<String, ArrayList<Long>> artistsStats = new HashMap<>();

    VoteController(@Value("${ARTISTS}") String[] artists) {
        for (var artist : artists) {
            artistsStats.put(artist, new ArrayList<>());
        }
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> doPost(final @RequestBody Vote vote) {
        if (!PHONE_PREDICATE.test(vote.phone())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Bucket bucket = getBucket(vote.phone());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            ArrayList<Long> artistStat = artistsStats.getOrDefault(vote.artist(), null);
            if (artistStat == null) {
                return fillHeader(ResponseEntity.status(HttpStatus.NOT_FOUND),
                        probe.getRemainingTokens(), probe.getNanosToWaitForRefill()).build();
            }
            updateTime(artistStat, System.currentTimeMillis());
            return fillHeader(ResponseEntity.status(HttpStatus.CREATED),
                    probe.getRemainingTokens(), probe.getNanosToWaitForRefill()).build();
        }
        return fillHeader(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS),
                probe.getRemainingTokens(), probe.getNanosToWaitForRefill()).build();
    }

    public synchronized void updateTime(ArrayList<Long> artistStat, long currentTime) {
        artistStat.add(currentTime);
        if (oldestVote == null) {
            oldestVote = currentTime;
        }
        newestVote = currentTime;
    }

    @GetMapping(produces = "application/json")
    public Map<String, Object> doGet() {
        Map<String, Object> data = new HashMap<>();
        ArrayList<Map<String, Object>> info = new ArrayList<>();
        for (var entity : artistsStats.entrySet()) {
            info.add(Map.of("name", entity.getKey(),
                    "votes", entity.getValue().size()));
        }
        data.put("data", info.toArray());
        return data;
    }

    @GetMapping(value = "/stats", produces = "application/json")
    public Map<String, Object> doStats(@RequestParam(value = "from", defaultValue = "-1") Long from,
                                      @RequestParam(value = "to", defaultValue = "-1") Long to,
                                      final @RequestParam(value = "intervals", defaultValue = "10") long intervals,
                                      final @RequestParam(value = "artists", defaultValue = "") String artists) {
        String[] artistsNames;
        if (artists.length() > 0) {
            artistsNames = artists.split(",");
        } else {
            artistsNames = artistsStats.keySet().toArray(new String[0]);
        }
        from = from == -1 ? oldestVote : from;
        to = to == -1 ? newestVote : to;
        if (from == null || to == null) {
            return Map.of("data", new Object[0]);
        }
        IntervalManager manager = new IntervalManager(from, to, intervals);
        Map<String, Object> ans = new HashMap<>();
        Object[] data = new Object[(int) manager.countIntervals()];
        for (int i = 0; i < manager.countIntervals(); i++) {
            long[] interval = manager.getInterval(i);
            long votes = 0;
            for (String name : artistsNames) {
                List<Long> artistStat = artistsStats.get(name);
                if (artistStat == null || artistStat.isEmpty()) {
                    continue;
                }
                votes += calculate(artistStat, interval[0], interval[1]);
            }
            data[i] = Map.of("start", interval[0],
                    "end", interval[1],
                    "votes", votes);
        }
        ans.put("data", data);
        return ans;
    }

    private long calculate(List<Long> artistStat, long from, long to) {
        int startIndex = Collections.binarySearch(artistStat, from);
        int endIndex = Collections.binarySearch(artistStat, to);
        if (startIndex < 0) {
            startIndex = -startIndex - 1;
        }
        if (endIndex < 0) {
            endIndex = -endIndex - 1;
        } else {
            endIndex++;
        }
        return endIndex - startIndex;
    }

    private Bucket getBucket(final String phone) {
        Bucket bucket = buckets.getOrDefault(phone, null);
        if (bucket == null) {
            Bucket tmp = createBucket();
            buckets.put(phone, tmp);
            return tmp;
        }
        return bucket;
    }

    private Bucket createBucket() {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(BUCKET_LIMITS, Refill.greedy(BUCKET_LIMITS, Duration.ofNanos(BUCKET_REFILL))))
                .build();
    }

    private ResponseEntity.BodyBuilder fillHeader(ResponseEntity.BodyBuilder response, Long remaining, Long reset) {
        return response.header("x-ratelimit-limit", String.valueOf(BUCKET_LIMITS))
                .header("x-ratelimit-remaining", String.valueOf(remaining))
                .header("x-ratelimit-reset", String.valueOf(reset));
    }

}
