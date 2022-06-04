package cheesesosiska;

import com.google.gson.Gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

public class Request implements Callable<RequestResult> {
    HttpClient client;
    HttpRequest request;
    Request(final int index, final String artist, final URI uri) throws URISyntaxException {
        client = HttpClient.newBuilder().build();
        String json = new Gson().toJson(new Vote(Long.toString(9000000000L + index), artist));
        request = HttpRequest.newBuilder()
                .uri(uri)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
    @Override
    public RequestResult call() throws Exception {
        double startTime = System.currentTimeMillis();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return new cheesesosiska.RequestResult(response.statusCode(), (System.currentTimeMillis() - startTime) / 1000);
    }
}
