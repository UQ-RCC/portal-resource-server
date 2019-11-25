package au.org.massive.strudel_web.job_control;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

/**
 * Created by jason on 21/09/15.
 */
public class JsonSystemConfiguration extends AbstractSystemConfiguration {

    public static JsonSystemConfiguration getInstance(Map<String, Object> config) throws InvalidJsonConfigurationException {

        String loginHost;
        if (config.containsKey("loginHost")) {
            loginHost = (String) config.get("loginHost");
        } else {
            throw new InvalidJsonConfigurationException("JSON configuration must define 'loginHost'");
        }

        Boolean isTunnelTerminatedOnLoginHost = (Boolean) config.get("isTunnelTerminatedOnLoginHost");

        JsonSystemConfiguration jsonJobConfiguration;
        if (isTunnelTerminatedOnLoginHost == null) {
            jsonJobConfiguration = new JsonSystemConfiguration(loginHost);
        } else {
            jsonJobConfiguration = new JsonSystemConfiguration(loginHost, isTunnelTerminatedOnLoginHost);
        }
        jsonJobConfiguration.parseConfig(config);
        return jsonJobConfiguration;
    }

    public static JsonSystemConfiguration getInstance(URL url) throws InvalidJsonConfigurationException, IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(url.openStream()));

        StringBuilder jsonFile = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            jsonFile.append(inputLine);
        }
        in.close();

        return getInstance(jsonFile.toString());
    }

    public static JsonSystemConfiguration getInstance(String jsonConfig) throws InvalidJsonConfigurationException {
        Gson gson = new Gson();
        //noinspection unchecked
        return getInstance(gson.fromJson(jsonConfig, HashMap.class));
    }

    private JsonSystemConfiguration(String loginHost) {
        super(loginHost);
    }

    private JsonSystemConfiguration(String loginHost, boolean terminateTunnelOnLoginHost) {
        super(loginHost, terminateTunnelOnLoginHost);
    }

    @SuppressWarnings("unchecked")
    private void parseConfig(Map<String, Object> config) throws InvalidJsonConfigurationException {

        if (!config.containsKey("tasks")) {
            throw new InvalidJsonConfigurationException("JSON configuration must define 'tasks'");
        }

        List<String> messageRegexs = (List<String>)config.get("messageRegexs");
        if (messageRegexs != null) {
            for (String pattern : messageRegexs) {
                addMessageRegex(pattern);
            }
        }
        // Hoang: TODO: add HTTP method to tasks
        Map<String, Map<String, Object>> tasks = (Map<String, Map<String, Object>>) config.get("tasks");
        for (String taskName : tasks.keySet()) {

            Map<String, Object> task = tasks.get(taskName);

            String remoteHost = getLoginHost();
            if (task.containsKey("remoteHost")) {
                remoteHost = (String) task.get("remoteHost");
            }
            Map<String, String> defaults = new HashMap<>();
            if (task.containsKey("defaults")) {
                defaults = (Map<String, String>) task.get("defaults");
            }
            Set<String> requiredParams = new HashSet<>();
            if (task.containsKey("required")) {
                List<String> requiredParamsList = (List<String>) task.get("required");
                requiredParams = new HashSet<>(requiredParams.size());
                requiredParams.addAll(requiredParamsList);
            }

            ExecConfig execConfig;
            if (task.containsKey("execConfig")) {
                execConfig = (ExecConfig) task.get("execConfig");
            } else {
                throw new InvalidJsonConfigurationException("JSON configuration for task '" + taskName + "' must define 'execConfig'");
            }

            String resultsPattern;
            if (task.containsKey("resultPattern")) {
                resultsPattern = (String) task.get("resultPattern");
            } else {
                throw new InvalidJsonConfigurationException("JSON configuration for task '" + taskName + "' must define 'resultPattern'");
            }

            String httpMethod = "GET";
            List<String> acceptedMethods = Arrays.asList("GET", "POST");
            if(task.containsKey("method") 
            		&& acceptedMethods.contains(task.get("method").toString().toUpperCase()))
            	httpMethod = task.get("method").toString().toUpperCase();
            TaskParameters taskParameters = new TaskParameters(
                    remoteHost,
                    execConfig,
                    resultsPattern,
                    defaults,
                    requiredParams, 
                    httpMethod
            );

            addRemoteCommand(taskName, taskParameters);
        }
    }
}
