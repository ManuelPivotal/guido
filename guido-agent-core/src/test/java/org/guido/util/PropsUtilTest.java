package org.guido.util;

import junit.framework.Assert;

import org.junit.Test;


public class PropsUtilTest {
	@Test
	public void canTranslateNameToEnvName() {
		String envName = PropsUtil.turnToEnvName("a.b.c.d");
		Assert.assertEquals("A_B_C_D", envName);
	}
}
