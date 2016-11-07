package org.guido.agent.transformer.configuration;

public interface ConfigurationWatcher {

	static final public int NO_POLL = -1;

	void configurationPath(String configurationPath);
	void configurationPolling(int secondsBetweenPolls);
	void configurationNotify(ConfigurationNotify notify);
	void start();
}
