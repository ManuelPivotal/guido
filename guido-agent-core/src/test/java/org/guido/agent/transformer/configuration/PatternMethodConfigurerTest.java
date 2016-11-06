package org.guido.agent.transformer.configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import org.guido.agent.transformer.configuration.PatternMethodConfigurer.Reload;
import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class PatternMethodConfigurerTest {
	
	static ClassPool pool;
	static GuidoLogger LOG = GuidoLogger.getLogger(PatternMethodConfigurerTest.class);
	static {
		GuidoLogger.setGlobalLogLevel(GuidoLogger.DEBUG);
	}
	
	@BeforeClass
	static public void init() {
		pool = ClassPool.getDefault();;
	}
	
	class ChangeNotifier extends AbstractConfigurationWatcher {
		
		public ChangeNotifier(String configurationPath, int secondsBetweenPolls) {
			super(configurationPath, secondsBetweenPolls);
		}

		@Override
		public void start() {
			BufferedReader reader = new BufferedReader(new StringReader("**=off"));
			try {
				notify.onLoaded(reader);
			} catch(Exception e) {}
		}

		@Override
		protected void doStart() {
		}

		@Override
		protected void doWatch() {
		}
	}

	
	class ErrorNotifier extends AbstractConfigurationWatcher {
		
		public ErrorNotifier(String configurationPath, int secondsBetweenPolls) {
			super(configurationPath, secondsBetweenPolls);
		}

		@Override
		public void start() {
			notify.onError();
		}

		@Override
		protected void doStart() {
		}

		@Override
		protected void doWatch() {
		}
	}
	
	@Test
	public void canParseLineWithTarget() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer("me-and.myhost");
		PatternMethodConfig lineConfig;
		lineConfig = configurer.parseLine("**.a.b.c.**@me**=threshold:20,off");
		Assert.assertNotNull(lineConfig);
		Assert.assertEquals("**.a.b.c.**", lineConfig.getClassName());
		Assert.assertEquals((long)(20 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(false, lineConfig.isAllowed());
	}

	@Test
	public void canIgnoreLineWithWrongTarget() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer("me-and.myhost");
		PatternMethodConfig lineConfig;
		lineConfig = configurer.parseLine("**.a.b.c.**@abc**=threshold:20,off");
		Assert.assertNull(lineConfig);
	}
	
	@Test
	public void canParseInferfaces() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer("me-and.myhost");
		PatternMethodConfig lineConfig;
		lineConfig = configurer.parseLine("[**.a.b.c.**]@me**=threshold:20,off");
		Assert.assertNotNull(lineConfig);
		Assert.assertEquals("**.a.b.c.**", lineConfig.getClassName());
		Assert.assertEquals((long)(20 * 1000000), lineConfig.getThreshold());
		Assert.assertEquals(false, lineConfig.isAllowed());
		Assert.assertEquals(true, lineConfig.isInterface());
	}

	@Test
	public void configSetIfConfigFileDoesExist() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		
		Assert.assertEquals(0, configurer.getRules().size());
		
		Reload reload = mock(Reload.class);
		
		configurer.loadClassConfig(new ChangeNotifier("", 0), reload);
		Assert.assertEquals(1, configurer.getRules().size());
		verify(reload).doReload();
	}

	
	@Test
	public void noConfigIfConfigFileDoesNotExist() {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.endConfigure();
		
		Assert.assertEquals(1, configurer.getRules().size());
		Reload reload = mock(Reload.class);
		
		configurer.loadClassConfig(new ErrorNotifier("", 0), reload);
		Assert.assertEquals(0, configurer.getRules().size());
		verify(reload).doReload();
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
		configurer.addLine("[java.sql.Driver.*]=on,threshold:10");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
			if(methods.contains(method)) {
				Assert.assertEquals((long)(10 * 1000000), methodConfig.getThreshold());
			} else {
				Assert.assertEquals((long)(-1), methodConfig.getThreshold());
			}
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
		configurer.addLine("[java.sql.Driver.*]=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}

		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("[java.sql.Driver.*]=on");
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
		configurer.addLine("[java.sql.Connection.*]=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}
	}
	
	@Test
	public void doNotConfuseClassesAndInterfaces() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOff();

		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		configurer.startConfigure();
		configurer.addLine("java.sql.Connection.*=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertFalse(methodConfig.isAllowed());
		}
	}

	@Test
	public void canCheckInheritedInterfacesWithDefaultOff() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer();
		configurer.defaultIsOff();
		configurer.showMethodRules();

		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		CtClass itf = pool.get("java.sql.Connection");
		List<CtMethod> methods = Arrays.asList(itf.getDeclaredMethods());
		configurer.startConfigure();
		configurer.addLine("[java.sql.Connection.*]=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed() == methods.contains(method));
		}

		configurer.startConfigure();
		configurer.addLine("**=off");
		configurer.addLine("[java.sql.Connection.*]=on");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(false, methodConfig.isAllowed());
		}
	}
	@Test
	public void canFindLastThreshold() throws Exception {
		PatternMethodConfigurer configurer = new PatternMethodConfigurer("me");
		configurer.defaultIsOff();
		
		configurer.startConfigure();
		configurer.addLine("org.**=on,threshold:10");
		configurer.addLine("org.postgresql.**=on,threshold:20");
		//configurer.addLine("org.postgresql.jdbc.PgConnection=on,threshold:30");
		configurer.endConfigure();
		
		CtClass driver = pool.get("org.postgresql.jdbc.PgConnection");
		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
			Assert.assertEquals(20 * 1000000, methodConfig.getThreshold());
		}

		configurer.startConfigure();
		configurer.addLine("org.**=on,threshold:10");
		configurer.addLine("org.postgresql.**=on,threshold:20");
		configurer.addLine("org.postgresql.jdbc.PgConnection=on,threshold:30");
		configurer.endConfigure();

		for(CtMethod method : driver.getDeclaredMethods()) {
			PatternMethodConfig methodConfig = configurer.configFor(method);
			Assert.assertEquals(true, methodConfig.isAllowed());
			Assert.assertEquals(20 * 1000000, methodConfig.getThreshold());
		}

	}
}
