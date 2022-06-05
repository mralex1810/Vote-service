package cheesesosiska;

import com.beust.jcommander.JCommander;

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

    public static void main(String[] args) {
        SlaArgs jArgs = getSlaArgs(args);

        String[] artists = System.getenv("ARTISTS").split(",");
        URI url;
        try {
            url = new URI("http://" + jArgs.getUrl() + "/votes");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Bad URI for: " + jArgs.getUrl(), e);
        }
        int requests = jArgs.getRequests();
        int users = jArgs.getUsers();
        if (requests == 0) throw new RuntimeException("-n must be > 0");
        if (users == 0) throw new RuntimeException("-c must be > 0");

        ExecutorService service = Executors.newFixedThreadPool(users);
        double totalTime;
        List<Future<RequestResult>> results = null;
        double start = System.currentTimeMillis();
        results = executeCalls(artists, url, requests, users);
        totalTime = (System.currentTimeMillis() - start) / 1000;

        double[] times = new double[requests];
        int code200Counter = 0;
        int code400Counter = 0;
        double sumTime = 0;
        for (int i = 0; i < requests; i++) {
            RequestResult res = null;
            try {
                res = results.get(i).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            times[i] = res.time();
            sumTime += res.time();
            switch (res.code() / 100) {
                case 2 -> code200Counter++;
                case 4 -> code400Counter++;
            }
        }
        Arrays.sort(times);

        printMainStat(requests, users, totalTime, times, sumTime);
        printPercentile(times);
        printStatusCodeDistribution(code200Counter, code400Counter);
    }

    private static void printStatusCodeDistribution(int code200Counter, int code400Counter) {
        System.out.println("Status code distribution\n");
        System.out.printf("[200] %d responses\n\n", code200Counter);
        System.out.printf("[400] %d responses\n\n", code400Counter);
    }

    private static void printPercentile(double[] times) {
        System.out.println("Latency distribution:\n");
        for (var perc : PROCENTILES) {
            System.out.printf(" %d %% in %5.4f secs\n\n", perc, evaluatePercentile(times, perc));
        }
    }

    private static double evaluatePercentile(double[] sortedArr, int perc) {
        return sortedArr[perc * (sortedArr.length - 1) / 100];
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

    private static List<Future<RequestResult>> executeCalls(String[] artists, URI url, int requests, int users) {
        ExecutorService service = Executors.newFixedThreadPool(users);
        List<Future<RequestResult>> results = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            results.add(service.submit(new Request(i, artists[i % artists.length], url)));
        }
        service.shutdown();
        return results;
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
