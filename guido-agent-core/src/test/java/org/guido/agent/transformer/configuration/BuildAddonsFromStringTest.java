package org.guido.agent.transformer.configuration;

import org.junit.Test;

public class BuildAddonsFromStringTest {
	@Test
	public void canBuildAddonFromPropString() {
		String propString = "%eventDate(MMM d HH:mm:ss) hostname [facility]";
	}
}
