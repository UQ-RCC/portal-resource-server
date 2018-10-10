package au.org.massive.strudel_web.job_control;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by jason on 29/09/15.
 */
public class ConfigurationRegistry {
    private Map<String, AbstractSystemConfiguration> systemConfigurations;

    public ConfigurationRegistry() {
        this.systemConfigurations = new HashMap<>();
    }

    public void addSSHCertSigningBackend() {
    }


    public void addSystemConfiguration(String id, AbstractSystemConfiguration configuration) {
        addSystemConfiguration(id, configuration, false);
    }

    public void addSystemConfiguration(String id, AbstractSystemConfiguration configuration, boolean setAsDefault) {
        systemConfigurations.put(id, configuration);
        if (setAsDefault || systemConfigurations.size() == 1) {
            systemConfigurations.put("default", configuration);
        }
    }

    public AbstractSystemConfiguration getSystemConfigurationById(String id) {
        return systemConfigurations.get(id);
    }

    /**
     * Gets the default SSH cert signing backend, which is either the one maked 'default', or the first one
     * in the map.
     * @return the default cert signing backend
     */
    public AbstractSystemConfiguration getDefaultSystemConfiguration() {
        return systemConfigurations.getOrDefault("default", systemConfigurations.get(systemConfigurations.keySet().iterator().next()));
    }

    public String getSystemConfigurationAsJson() {
        Gson gson = new Gson();
        Set<String> configurationKeys = systemConfigurations.keySet();
        configurationKeys.remove("default");
        HashMap<String, AbstractSystemConfiguration> systemConfigurationsCopy = new HashMap<>();
        for (String key : configurationKeys) {
            systemConfigurationsCopy.put(key, systemConfigurations.get(key));
        }
        return gson.toJson(systemConfigurationsCopy);
    }
}
