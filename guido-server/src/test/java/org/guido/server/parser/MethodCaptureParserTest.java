package org.guido.server.parser;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.guido.server.test.util.TestUtils.loadTestFileData;

import org.guido.persistence.domain.MethodPerformanceCapture;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MethodCaptureParserTest {
	
	private static final Logger LOG = LoggerFactory.getLogger(MethodCaptureParserTest.class);

	@Test
	public void canParseCaptureLine() throws Exception {
		String testString = loadTestFileData("guido-line-sample.log");
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
		MethodPerformanceCapture captured = mapper.readValue(testString, new TypeReference<MethodPerformanceCapture>() {});
		LOG.info("Captured is {}", captured);
	}
}
