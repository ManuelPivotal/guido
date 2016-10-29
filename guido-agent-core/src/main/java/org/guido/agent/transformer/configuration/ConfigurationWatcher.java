package org.guido.agent.transformer.configuration;

public interface ConfigurationWatcher {
	void configurationPath(String configurationPath);
	void configurationPolling(int secondsBetweenPolls);
	void configurationNotify(ConfigurationNotify notify);
	void start();
}
