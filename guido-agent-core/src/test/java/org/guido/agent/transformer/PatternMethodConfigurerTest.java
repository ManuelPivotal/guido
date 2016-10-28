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

public class PatternMethodConfigurerTest {
	
	static ClassPool pool;
	
	@BeforeClass
	static public void init() {
		pool = ClassPool.getDefault();;
	}
	
	@Test
	public void canParseConfigurationLineWithDefaultOn() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		
		PatternMethodConfig lineConfig;
		
		lineConfig = configurer.parseLine("*=threshold:20,off");
		Assert.assertEquals("*", lineConfig.getClassName());
		Assert.assertEquals((long)(20 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(false, lineConfig.isAllowed());

		lineConfig = configurer.parseLine("*=off,threshold:20");
		Assert.assertEquals("*", lineConfig.getClassName());
		Assert.assertEquals((long)(20 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(false, lineConfig.isAllowed());
		
		lineConfig = configurer.parseLine("a.b.*.c=threshold:12,on");
		Assert.assertEquals("a.b.*.c", lineConfig.getClassName());
		Assert.assertEquals((long)(12 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(true, lineConfig.isAllowed());
	}

	@Test
	public void canCheckSimpleClassWithDefaultOn() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOn();

		configurer.startConfigure();
		
		// no configuration, all allowed
		CtClass driver = pool.get("org.postgresql.Driver");
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
		}

		// all off
		configurer.addLine("**=off");
		configurer.endConfigure();
		
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}

		// add driver only
		configurer.addLine("org.postgresql.Driver.*=on");
		configurer.endConfigure();
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
		}
	}
	
	@Test
	public void canCheckSimpleClassWithDefaultOff() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOff();

		configurer.startConfigure();
		
		// no configuration, all disabled
		CtClass driver = pool.get("org.postgresql.Driver");
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}
		
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}

		// add driver only
		configurer.addLine("org.postgresql.Driver.*=on");
		configurer.endConfigure();
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
		}
		
		configurer.addLine("**=off"); // add all off
		configurer.endConfigure();
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}
	}
	
	@Test
	public void canCheckSimpleInterfaces() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOn();

		CtClass driver = pool.get("org.postgresql.Driver");
		CtClass itf = pool.get("java.sql.Driver");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Driver.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}
	}

	@Test
	public void canCheckSimpleInterfacesWithDefaultOff() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOff();

		CtClass driver = pool.get("org.postgresql.Driver");
		CtClass itf = pool.get("java.sql.Driver");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		
		configurer.startConfigure();
		configurer.addLine("java.sql.Driver.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}

		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Driver.*=on");
		configurer.endConfigure();
		
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}
	}

	@Test
	public void canCheckInheritedInterfaces() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOn();

		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		CtClass itf = pool.get("java.sql.Connection");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Connection.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}
	}

	@Test
	public void canCheckInheritedInterfacesWithDefaultOff() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOff();

		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		CtClass itf = pool.get("java.sql.Connection");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("java.sql.Connection.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}

		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("java.sql.Connection.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}
	}
}
