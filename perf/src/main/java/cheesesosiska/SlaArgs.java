package cheesesosiska;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;

import java.util.List;

public class SlaArgs {
    @Parameter(names = {"-c"}, arity = 1, description = "Number of users", required = true, validateWith = PositiveInteger.class)
    private Integer users;
    @Parameter(names = {"-n"}, arity = 1, description = "Number of requests", required = true, validateWith = PositiveInteger.class)
    private Integer requests;
    @Parameter(description = "Service address", required = true)
    private String url;

    public Integer getUsers() {
        return users;
    }

    public Integer getRequests() {
        return requests;
    }

    public String getUrl() {
        return url;
    }

}
