package org.guido.server.test.util;

import java.nio.file.Files;
import java.nio.file.Paths;

public abstract class TestUtils {
	static public String loadTestFileData(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(ClassLoader.getSystemResource(name).toURI())));
	}
}
