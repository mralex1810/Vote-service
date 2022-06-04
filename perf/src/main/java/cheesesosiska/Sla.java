package cheesesosiska;

import com.beust.jcommander.JCommander;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Sla {

    private static final List<Integer> PROCENTILES = List.of(10, 25, 50, 75, 90, 95, 99);

    public static void main(String[] args) throws URISyntaxException, InterruptedException, ExecutionException {
        SlaArgs jArgs = getSlaArgs(args);

        String[] artists = jArgs.getArtists().toArray(new String[0]);
        URI url = new URI("http://" + jArgs.getUrl() + "/votes");
        int requests = jArgs.getRequests();
        int users = jArgs.getUsers();

        ExecutorService service = Executors.newFixedThreadPool(users);
        prepareService(artists, url, users, service);

        double start = System.currentTimeMillis();
        List<Future<RequestResult>> results = executeCalls(artists, url, requests, service);
        double totalTime = (System.currentTimeMillis() - start) / 1000;
        service.shutdown();
        double[] times = new double[requests];
        int code200Counter = 0;
        int code400Counter = 0;
        double sumTime = 0;
        for (int i = 0; i < requests; i++) {
            var res = results.get(i).get();
            times[i] = res.time();
            sumTime += res.time();
            switch (res.code() / 100) {
                case 2 -> code200Counter++;
                case 4 -> code400Counter++;
            }
        }
        Arrays.sort(times);

        printMainStat(requests, users, totalTime, times, sumTime);
        printPercintile(times);
        printStatusCodeDistribution(code200Counter, code400Counter);
    }

    private static void printStatusCodeDistribution(int code200Counter, int code400Counter) {
        System.out.println("Status code distribution\n");
        System.out.printf("[200] %d responses\n\n", code200Counter);
        System.out.printf("[400] %d responses\n\n", code400Counter);
    }

    private static void printPercintile(double[] times) {
        System.out.println("Latency distribution:\n");
        Percentile percentile = new Percentile();
        percentile.setData(times);
        for (var c : PROCENTILES) {
            System.out.printf(" %d %% in %5.4f secs\n\n", c, percentile.evaluate(c));
        }
    }

    private static void printMainStat(int requests, int users, double totalTime, double[] times, double sumTime) {
        System.out.printf("Runs %d vote requests with %d concurrent requests at the same time\n", requests, users);

        System.out.println("Summary:\n");
        System.out.printf(" Total: %.4f secs\n\n", totalTime);
        System.out.printf(" Slowest: %.4f secs\n\n", times[requests - 1]);
        System.out.printf(" Fastest: %.4f secs\n\n", times[0]);
        System.out.printf(" Average: %.4f secs\n\n", sumTime / requests);
        System.out.printf(" Requests/sec: %.4f secs\n\n", requests / totalTime);
    }

    private static List<Future<RequestResult>> executeCalls(String[] artists, URI url, int requests, ExecutorService service) throws URISyntaxException {
        List<Future<RequestResult>> results = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            results.add(service.submit(new Request(i, artists[i % artists.length], url)));
        }
        return results;
    }

    private static void prepareService(String[] artists, URI url, int users, ExecutorService service) throws URISyntaxException {
        for (int i = 0; i < users; i++) {
            service.submit(new Request(i, artists[i % artists.length], url));
        }
    }

    private static SlaArgs getSlaArgs(String[] args) {
        SlaArgs jArgs = new SlaArgs();
        JCommander helloCmd = JCommander.newBuilder()
                .addObject(jArgs)
                .build();
        helloCmd.parse(args);
        return jArgs;
    }
}
