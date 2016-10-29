package org.guido.agent.transformer.configuration;

public interface ConfigurationWatcher {
	void configurationPath(String path);
	void configurationPolling(int second);
	void configurationNotify(ConfigurationNotify notify);
	void start();
}
