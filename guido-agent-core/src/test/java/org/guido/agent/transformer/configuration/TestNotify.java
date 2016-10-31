package org.guido.agent.transformer.configuration;

import java.io.BufferedReader;

import org.guido.agent.transformer.logger.GuidoLogger;

public class TestNotify implements ConfigurationNotify {
	
	static GuidoLogger LOG = GuidoLogger.getLogger(TestNotify.class);
	
	@Override
	public void onError() {
		LOG.info("Error");
	}

	@Override
	public void onLoaded(BufferedReader reader) throws Exception {
		String line;
		while((line = reader.readLine()) != null) {
			LOG.info("{}", line);
		}
	}
}
