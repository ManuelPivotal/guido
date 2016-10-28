package org.guido.agent.transformer.guidologger;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.Test;

public class GuidoLoggerTest {
	// more a dev helper than a test
	@Test
	public void canHaveZeroOrMoreArgs() {
		GuidoLogger logger = GuidoLogger.getLogger("TEST");
		
		logger.debug("hello, world");
		logger.debug("hello, {}", "world");
		logger.debug("{}, {}", "hello", "world");
		logger.debug("{}{} {}", "hello", ',', "world");
		logger.debug("{} is a number", 10);
		
		logger.error("error");
		logger.error(new Exception("boom"), "error on {}", 10);
	}
}
