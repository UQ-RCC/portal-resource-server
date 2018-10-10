package au.org.massive.strudel_web.job_control;


import java.util.List;

/**
 * Encapsulates both user messages and the processed command result
 */
public class TaskResult<T> {
    private List<UserMessage> userMessages;
    private T cmdResult;

    public TaskResult(List<UserMessage> userMessages, T cmdResult) {
        this.userMessages = userMessages;
        this.cmdResult = cmdResult;
    }

    public List<UserMessage> getUserMessages() {
        return userMessages;
    }

    public boolean hasUserMessages() {
        return !userMessages.isEmpty();
    }

    public T getCommandResult() {
        return cmdResult;
    }

//    public String getCommandResultAsJson() {
//        return new Gson().toJson(getCommandResult());
//    }
}
