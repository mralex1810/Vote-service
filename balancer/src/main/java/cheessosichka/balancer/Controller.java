package cheessosichka.balancer;


import com.github.snksoft.crc.CRC;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@RestController()
@RequestMapping("/votes")
public class Controller {
    private Long oldestVote;
    private Long newestVote;
    private final Predicate<String> PHONE_PREDICATE = Pattern.compile("9\\d{9}").asMatchPredicate();

    @Value("${SERVICES}")
    private String[] services;
    private final HttpClient client = HttpClient.newBuilder().build();

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> doPost(final @RequestBody Vote vote) throws IOException, InterruptedException, URISyntaxException {
        if (!PHONE_PREDICATE.test(vote.phone())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        int serviceIndex = (int) (computeHash(vote.phone()) % services.length);
        updateTime(System.currentTimeMillis());
        HttpRequest request = HttpRequest.newBuilder(new URI("http://" + services[serviceIndex] + "/votes"))
                .setHeader("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(vote)))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return ResponseEntity.status(response.statusCode()).build();
    }

    @GetMapping(produces = "application/json")
    public Map doGet() throws IOException, InterruptedException, URISyntaxException {
        Map getData = null;
        for (var service : services) {
            HttpRequest request = HttpRequest.newBuilder(new URI("http://" + service + "/votes"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map tmp = new Gson().fromJson(response.body(), Map.class);

            ArrayList<Map> tmpData = (ArrayList<Map>) tmp.get("data");
            tmpData.sort(Comparator.comparing(a -> (String) ((Map) a).get("name")));
            for (Map it : tmpData) {
                it.put("votes", ((Double) it.get("votes")).longValue());
            }
            if (getData == null) {
                getData = tmp;
            } else {
                ArrayList<Map> data = (ArrayList<Map>) getData.get("data");
                for (int i = 0; i < tmpData.size(); i++) {
                    data.get(i).put("votes", (long) data.get(i).get("votes") + (long) tmpData.get(i).get("votes"));
                }
            }
        }
        return getData;
    }

    @GetMapping(value = "/stats", produces = "application/json")
    public Map doStats(@RequestParam(value = "from", defaultValue = "-1") Long from,
                       @RequestParam(value = "to", defaultValue = "-1") Long to,
                       final @RequestParam(value = "intervals", defaultValue = "10") long intervals,
                       final @RequestParam(value = "artists", defaultValue = "") String artists) throws URISyntaxException, IOException, InterruptedException {
        from = from == -1 ? oldestVote : from;
        to = to == -1 ? newestVote : to;
        if (from == null || to == null) {
            return Map.of();
        }
        Map statsData = null;
        for (var service : services) {
            String req = String.format("http://%s/votes/stats?from=%d&to=%d&intervals=%d&artists=%s", service, from, to, intervals, artists);
            HttpRequest request = HttpRequest.newBuilder(new URI(req))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map tmp = new Gson().fromJson(response.body(), Map.class);
            ArrayList<Map> tmpData = (ArrayList<Map>) tmp.get("data");
            tmpData.sort(Comparator.comparingDouble(a -> (Double) ((Map) a).get("start")));
            for (Map it : tmpData) {
                it.put("votes", ((Double) it.get("votes")).longValue());
            }
            if (statsData == null) {
                statsData = tmp;
                for (Map it : tmpData) {
                    it.put("start", ((Double) it.get("start")).longValue());
                    it.put("end", ((Double) it.get("end")).longValue());
                }
            } else {
                ArrayList<Map> data = (ArrayList<Map>) statsData.get("data");
                for (int i = 0; i < data.size(); i++) {
                    data.get(i).put("votes", (long) data.get(i).get("votes") + (long) tmpData.get(i).get("votes"));
                }
            }
        }
        return statsData;
    }


    public void updateTime(long currentTime) {
        if (oldestVote == null) {
            oldestVote = currentTime;
        }
        newestVote = currentTime;
    }

    private long computeHash(String phone) {
        return CRC.calculateCRC(CRC.Parameters.CRC16, phone.getBytes());
    }
}
