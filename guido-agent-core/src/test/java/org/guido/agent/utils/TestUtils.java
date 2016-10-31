package org.guido.agent.utils;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TestUtils {
	static public String loadTestFileData(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(ClassLoader.getSystemResource(name).toURI())));
	}
}
