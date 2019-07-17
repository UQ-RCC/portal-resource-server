package au.org.massive.strudel_web.job_control;

import au.org.massive.strudel_web.util.RegexHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides a set of convenience methods for initialising a {@link TaskConfiguration} object.
 * All {@link TaskConfiguration} objects should ideally subclass {@link AbstractSystemConfiguration}.
 *
 * @author jrigby
 */
public abstract class AbstractSystemConfiguration implements TaskConfiguration {

    // This list is there only for the /api/configurations endpoint
    // and is only used by the client.
    //private final List<String> authBackendNames;

    private final Map<String, TaskParameters> configurations;
    private final String loginHost;
    private final boolean terminateTunnelOnLoginHost;

    private final List<String> messageRegexs;

    public AbstractSystemConfiguration(String loginHost) {
        this(loginHost, false);
    }

    public AbstractSystemConfiguration(String loginHost, boolean terminateTunnelOnLoginHost) {
        //authBackendNames = new LinkedList<>();
        configurations = new HashMap<>();
        this.loginHost = loginHost;
        this.terminateTunnelOnLoginHost = terminateTunnelOnLoginHost;
        messageRegexs = new LinkedList<>();
    }

    public String getLoginHost() {
        return loginHost;
    }

    public boolean isTunnelTerminatedOnLoginHost() {
        return terminateTunnelOnLoginHost;
    }

//    public void addAuthBackend(String name) {
//        authBackendNames.add(name);
//    }

    protected void addRemoteCommand(String jobName, Map<String, String> defaults,
                                    String[] requiredParams, String commandPattern, 
                                    String resultPattern) {
        addRemoteCommand(getLoginHost(), jobName, defaults, requiredParams, 
        		commandPattern, resultPattern);
    }

    protected void addRemoteCommand(String host, String jobName, Map<String, String> defaults,
                                    String[] requiredParams, String commandPattern, 
                                    String resultPattern) {

        Set<String> requiredParamsSet;
        if (requiredParams == null || requiredParams.length == 0) {
            requiredParamsSet = new HashSet<>();
        } else {
            requiredParamsSet = new HashSet<>(Arrays.asList(requiredParams));
        }

        addRemoteCommand(host, jobName, defaults, requiredParamsSet, commandPattern, resultPattern);
    }

    protected void addRemoteCommand(String host, String jobName, Map<String, String> defaults,
                                    Set<String> requiredParams, String commandPattern, String resultPattern) {

        if (defaults == null) {
            defaults = new HashMap<>();
        }
        if (requiredParams == null) {
            requiredParams = new HashSet<>();
        }

        addRemoteCommand(jobName, new TaskParameters(host, commandPattern, resultPattern, defaults, requiredParams));
    }

    protected void addRemoteCommand(String jobName, TaskParameters job) {
        configurations.put(jobName, job);
    }

    /**
     * Adds a regex pattern used to extract command output that is to be displayed to the user via a dialogue box,
     * for example.
     * @param messageRegex the regex pattern
     */
    protected void addMessageRegex(String messageRegex) {
        messageRegexs.add(messageRegex);
    }

    /**
     * Applies the list of message regular expressions (messageRegexs) to extract
     * any text that should be presented to the user as a result of any remote command executed
     * @param commandOutput text from remote command execution
     * @return a list of {@link UserMessage}
     */
    @Override
    public List<UserMessage> getMessagesFromCommandOutput(String commandOutput) {
        List<UserMessage> messages = new LinkedList<>();
        for (String regex : messageRegexs) {
            List<Map<String, String>> extractedMessages = RegexHelper.processRegex(regex, commandOutput);
            for (Map<String,String> msg : extractedMessages) {
                for (final String key : msg.keySet()) {
                    UserMessage.MessageType messageType;
                    switch (key) {
                        case "info":
                            messageType = UserMessage.MessageType.INFORMATION;
                            break;
                        case "warn":
                            messageType = UserMessage.MessageType.WARNING;
                            break;
                        case "error":
                            messageType = UserMessage.MessageType.ERROR;
                            break;
                        default:
                            messageType = UserMessage.MessageType.INFORMATION;
                    }
                    messages.add(new UserMessage(messageType, msg.get(key)));
                }
            }
        }
        return messages;
    }

    @Override
    public TaskParameters findByTaskType(String jobType)
            throws NoSuchTaskTypeException {
        String searchString = jobType.toLowerCase();

        if (!configurations.containsKey(searchString)) {
            throw new NoSuchTaskTypeException();
        }

        return configurations.get(searchString);
    }

}
