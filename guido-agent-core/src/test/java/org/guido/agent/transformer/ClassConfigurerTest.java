package org.guido.agent.transformer;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class ClassConfigurerTest {
	
	static ClassPool pool;
	
	@BeforeClass
	static public void init() {
		pool = ClassPool.getDefault();;
	}
	
	
	@Test
	public void canParseConfigurationLine() {
		ClassConfigurer configurer = new ClassConfigurer();
		PerClassConfig lineConfig;
		
		lineConfig = configurer.parseLine("*=threshold:20,off");
		Assert.assertEquals("*", lineConfig.getClassName());
		Assert.assertEquals((long)(20 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(false, lineConfig.isAllowed());

		lineConfig = configurer.parseLine("a.b.*.c=threshold:12,on");
		Assert.assertEquals("a.b.*.c", lineConfig.getClassName());
		Assert.assertEquals((long)(12 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(true, lineConfig.isAllowed());
	}
	
	@Test
	public void canCheckSimpleClass() throws Exception {
		ClassConfigurer configurer = new ClassConfigurer();
		configurer.startConfigure();
		
		// no configuration, all allowed
		CtClass driver = pool.get("org.postgresql.Driver");
		for(CtMethod method : driver.getDeclaredMethods()) {
			PerClassConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
		}

		// all off
		configurer.addLine("**=off");
		configurer.endConfigure();
		for(CtMethod method : driver.getDeclaredMethods()) {
			PerClassConfig methodConfig = configurer.configFor(method);
			GuidoLogger.debug(methodConfig + " for " + method.getLongName());
			Assert.assertEquals(false, methodConfig.isAllowed());
		}

		// add driver only
		configurer.addLine("org.postgresql.Driver.*=on");
		configurer.endConfigure();
		for(CtMethod method : driver.getDeclaredMethods()) {
			PerClassConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
		}
	}
	
	@Test
	public void canCheckSimpleInterfaces() throws Exception {
		ClassConfigurer configurer = new ClassConfigurer();

		CtClass driver = pool.get("org.postgresql.Driver");
		CtClass itf = pool.get("java.sql.Driver");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Driver.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PerClassConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}
	}

	@Test
	public void canCheckInheritedInterfaces() throws Exception {
		ClassConfigurer configurer = new ClassConfigurer();

		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		CtClass itf = pool.get("java.sql.Connection");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Connection.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PerClassConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}
	}
}
