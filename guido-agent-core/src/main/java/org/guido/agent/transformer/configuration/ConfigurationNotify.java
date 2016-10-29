package org.guido.agent.transformer.configuration;

import java.io.BufferedReader;

public interface ConfigurationNotify {
	void onError();
	void onLoaded(BufferedReader reader) throws Exception;
}
