package au.org.massive.strudel_web.job_control;

import au.org.massive.strudel_web.ssh.CertAuthInfo;
import au.org.massive.strudel_web.ssh.ForkedSSHClient;
import au.org.massive.strudel_web.ssh.SSHClient;
import au.org.massive.strudel_web.ssh.SSHExecException;
import au.org.massive.strudel_web.util.RegexHelper;
import au.org.massive.strudel_web.util.UnsupportedKeyException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.text.StrSubstitutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces {@link Task} objects based on a given {@link TaskConfiguration} and job type.
 *
 * @author jrigby
 */
public class TaskFactory {

    private final TaskConfiguration config;
    private final Path tmpDir;

    public TaskFactory(TaskConfiguration config, Path tmpDir) {
        this.config = config;
        this.tmpDir = tmpDir;
    }

    public Task getInstance(String taskType, CertAuthInfo certInfo, String remoteHost) throws NoSuchTaskTypeException {
        TaskParameters params = config.findByTaskType(taskType);
        if(remoteHost == null) {
            remoteHost = params.getRemoteHost();
        }
        return new Task(new ForkedSSHClient(certInfo, remoteHost, tmpDir),
                params.getExecConfig(), params.getResultRegexPattern(), params.getDefaultParams(),
                params.getRequiredParams());
    }

    public Task getInstance(String taskType, CertAuthInfo certInfo) throws NoSuchTaskTypeException {
        return this.getInstance(taskType, certInfo, null);
    }

    public class Task {
        private final SSHClient sshClient;
        private final ExecConfig execConfig;
        private final String resultRegexPattern;
        private final Map<String, String> defaultParams;
        private final Set<String> requiredParams;
        private final Gson gson;
        
        private Task(SSHClient sshClient, ExecConfig execConfig,
        		String resultRegexPattern, Map<String, String> defaultParams, 
        		Set<String> requiredParams) {
            this.sshClient = sshClient;
            this.execConfig = execConfig;
            this.resultRegexPattern = resultRegexPattern;
            this.defaultParams = defaultParams;
            this.requiredParams = requiredParams;
            this.gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        }

        public TaskResult<List<Map<String, String>>> run(Map<String, String> parameters) throws IOException, SSHExecException, MissingRequiredTaskParametersException, UnsupportedKeyException {
            validateParameters(parameters, defaultParams, requiredParams);

            byte[] input;
            String cmdPattern = execConfig.getLegacyPattern();
            if(cmdPattern != null) {
                input = createCommand(cmdPattern, parameters, defaultParams, requiredParams).getBytes(StandardCharsets.UTF_8);
            } else {
                /* Combine our parameters with the defaults and dump them to JSON. */
                Map<String, String> _params = new HashMap<>();
                if(defaultParams != null) {
                    _params.putAll(defaultParams);
                }
                _params.putAll(parameters);
                //_params.put("taskType", this.taskType);
                if(!_params.isEmpty()) {
                    input = gson.toJson(_params).getBytes(StandardCharsets.UTF_8);
                } else {
                    input = new byte[0];
                }
            }

            List<String> command = execConfig.getCommand();
            if(command.isEmpty()) {
                command = new ArrayList<>();
                command.add("bash");
                command.add("-s");
            }

            String rawCmdResult = sshClient.exec(command.stream().toArray(String[]::new), input);

            List<UserMessage> messages = config.getMessagesFromCommandOutput(rawCmdResult);
            List<Map<String,String>> processedCmdResult = RegexHelper.processRegexForEachLine(resultRegexPattern, rawCmdResult);
            return new TaskResult<>(messages, processedCmdResult);
        }
    }

    private static void validateParameters(Map<String, String> params, Map<String, String> defaultParams, Set<String> requiredParams) throws MissingRequiredTaskParametersException {
        if(requiredParams == null) {
            return;
        }

        // Verify all required parameters are present
        Set<String> suppliedParams = new HashSet<>(params.keySet());
        if (defaultParams != null) {
            suppliedParams.addAll(defaultParams.keySet());
        }

        if (!suppliedParams.containsAll(requiredParams)) {
            String rparams = String.join(", ", requiredParams);
            throw new MissingRequiredTaskParametersException(String.format("The following parameters are required: %s", rparams));
        }
    }

    private static String createCommand(String commandPattern,
                                 Map<String, String> params,
                                 Map<String, String> defaultParams,
                                 Set<String> requiredParams) throws MissingRequiredTaskParametersException {
        validateParameters(params, defaultParams, requiredParams);

        // Replace user supplied parameters
        StrSubstitutor sub = new StrSubstitutor(makeParamsSafe(params));
        String result = sub.replace(commandPattern);

        if (defaultParams != null) {
            // Fill in the rest with defaults
            sub = new StrSubstitutor(makeParamsSafe(defaultParams));
            return sub.replace(result);
        } else {
            return result;
        }
    }

    private static Map<String, String> makeParamsSafe(Map<String, String> params) {
        Map<String, String> safeValues = new HashMap<>();
        for (String key : params.keySet()) {
            String value = "'" + params.get(key).replace("'", "\\'") + "'";
            safeValues.put(key, value);
        }
        return safeValues;
    }
}
