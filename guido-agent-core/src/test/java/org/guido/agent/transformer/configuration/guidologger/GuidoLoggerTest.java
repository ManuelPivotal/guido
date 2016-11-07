package org.guido.agent.transformer.configuration.guidologger;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.Test;

public class GuidoLoggerTest {
	// more a dev helper than a test
	@Test
	public void canHaveZeroOrMoreArgs() {
		GuidoLogger logger = GuidoLogger.getLogger("TEST");
		GuidoLogger.setGlobalLogLevel(GuidoLogger.DEBUG);
		logStuff(logger);
	}

	private void logStuff(GuidoLogger logger) {
		logger.debug("hello, world");
		logger.debug("hello, {}", "world");
		logger.debug("{}, {}", "hello", "world");
		logger.debug("{}{} {}", "hello", ',', "world");
		logger.debug("{} is a number", 10);
		
		logger.error("error");
		logger.error(new Exception("boom"), "error on {}", 10);

		logger.info("info");
	}
}
