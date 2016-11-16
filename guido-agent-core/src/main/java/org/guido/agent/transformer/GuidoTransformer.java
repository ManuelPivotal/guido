package org.guido.agent.transformer;

import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_ALLOWED;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_COUNT;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_LONG_NAME;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_SHORT_SIGNATURE;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_THRESHOLD;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.TOTAL_REF;
import static org.guido.util.PropsUtil.getPropOrEnv;
import static org.guido.util.PropsUtil.getPropOrEnvBoolean;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;

import org.guido.agent.logs.provider.GuidoJsonJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider.MessageAddon;
import org.guido.agent.logs.provider.GuidoLogstashEncoder;
import org.guido.agent.stats.ExponentialMovingAverageRate;
import org.guido.agent.stats.MemoryUsageStats;
import org.guido.agent.stats.Statistics;
import org.guido.agent.transformer.configuration.BitBucketStashConfigurationWatcher;
import org.guido.agent.transformer.configuration.FileConfigurationWatcher;
import org.guido.agent.transformer.configuration.GithubConfigurationWatcher;
import org.guido.agent.transformer.configuration.PatternMethodConfig;
import org.guido.agent.transformer.configuration.PatternMethodConfigurer;
import org.guido.agent.transformer.configuration.PatternMethodConfigurer.Reload;
import org.guido.agent.transformer.interceptor.GuidoInterceptor;
import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.PropsUtil;
import org.guido.util.ThreadExecutorUtils;

import oss.guido.ch.qos.logback.classic.Level;
import oss.guido.ch.qos.logback.classic.Logger;
import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.ch.qos.logback.core.Appender;
import oss.guido.ch.qos.logback.core.ConsoleAppender;
import oss.guido.ch.qos.logback.core.util.Duration;
import oss.guido.javassist.ByteArrayClassPath;
import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;
import oss.guido.javassist.LoaderClassPath;
import oss.guido.net.logstash.logback.appender.LoggingEventAsyncDisruptorAppender;
import oss.guido.net.logstash.logback.appender.LogstashTcpSocketAppender;
import oss.guido.org.slf4j.MDC;

public class GuidoTransformer implements ClassFileTransformer {
	
	private Logger LOG;
	static private GuidoLogger guidoLOG = GuidoLogger.getLogger("GuidoTransformer");
	
	ClassPool pool;
	//CtClass etype = pool.get("java.lang.Throwable");
	LinkedBlockingDeque<Object[]> queue = new LinkedBlockingDeque<Object[]>(8192);
	Map<Integer, Object[]> references = new HashMap<Integer, Object[]>(32 * 1024);

	Map<String, String> extraProps = new HashMap<String, String>();
	private long threshold;
	private PatternMethodConfigurer classConfigurer;
	
	String propEx = "guido.ext.";
	int propExLength = propEx.length();
	MessageAddon[] addons = new MessageAddon[0];
	int logQListeners = 1;
	String pid;
	
	String[] jsonFieldNames = new String[] {
			"pid", 
			"threadUuid", 
			"depth", 
			"methodCalled", 
			"duration"
	};
	
	ExponentialMovingAverageRate logRate = new ExponentialMovingAverageRate();
	
	boolean statsFlag = false;
	
	public GuidoTransformer() {
		setGlobalLogLevel();
		getIncludedPackaged();
		getExtraProps();
		getTraceFlag();
		createClassConfigurer();
		getMethodThreshold();
		buildAddons();
		createLogger();
		createDefaults();
		setInterceptorRefs();
		getQListenersCount();
		startQListeners();
		startMemoryStats();
	}
	
	static List<String> packageRoots = new ArrayList<String>();
	
	private void getIncludedPackaged() {
		guidoLOG.debug("Getting included packages");
		String packages = PropsUtil.getPropOrEnv("guido.packageRoots");
		if(packages != null) {
			String[] prefixes = packages.split(",");
			for(String prefix : prefixes) {
				packageRoots.add(prefix.trim() + '.');
			}
		}
		guidoLOG.debug("root packages are {}", packageRoots);
	}

	private void setGlobalLogLevel() {
		GuidoLogger.setGlobalLogLevel(getPropOrEnv("guido.logLevel", "error"));
	}

	private void startMemoryStats() {
		if(getPropOrEnvBoolean("guido.memStats")) {
			MemoryUsageStats memStats = new MemoryUsageStats();
			int delay = PropsUtil.getPropOrEnvInt("guido.memStatsFrequency", 20000); // every 20s by default
			memStats.init(pid, LOG, delay);
			memStats.start();
		}
	}

	private void getTraceFlag() {
		statsFlag = getPropOrEnvBoolean("guido.showStats");
		if(statsFlag) {
			guidoLOG.info("@@@ stats are activated - they will be generated every 30s");
		}
	}

	private void getQListenersCount() {
		logQListeners = Integer.valueOf(PropsUtil.getPropOrEnv("guido.logqlisteners", "3"));
	}

	MessageAddon dateAddon = new MessageAddon() {
		@Override
		public String getAddon(ILoggingEvent event) {
			SimpleDateFormat format = new SimpleDateFormat("MMM d HH:mm:ss");
			return format.format(new Date(event.getTimeStamp()));
		}
	};
	
	abstract class ConstantMessageAddon implements MessageAddon {
		String constantMessage;
		public ConstantMessageAddon() {
			setConstantMessage(initMessage());
		}
		abstract String initMessage();
		void setConstantMessage(String constantMessage) {
			this.constantMessage = constantMessage;
		}
		public String getAddon(ILoggingEvent event) {
			return constantMessage;
		}
	}
	
	private void buildAddons() {
		addons = new MessageAddon[] {
			new ConstantMessageAddon() {
				@Override
				String initMessage() {
					return "DEBUG metrics - Performance Metrics: hostname=" + extraProps.get("hostname");
				}
			},
		};
	}

	private void getExtraProps() {
		Properties properties = System.getProperties();
		if(properties != null) {
			for(Entry<Object, Object> prop : properties.entrySet()) {
				String key = (String)prop.getKey();
				if(key.startsWith("guido.ext.") && key.length() > propExLength) {
					extraProps.put(key.substring(propExLength), (String)prop.getValue());
				}
			}
		}
	}

	private String GUIDO_BITBUCKET_USER = "guido.bitbucket.user";
	private String GUIDO_BITBUCKET_PASSWORD = "guido.bitbucket.password";

	private void createClassConfigurer() {
		classConfigurer = new PatternMethodConfigurer(PropsUtil.getPropOrEnv("guido.ext.hostname"));
		classConfigurer.defaultIsOff();
		if(getPropOrEnvBoolean("guido.showRules")) {
			classConfigurer.showMethodRules();
		}
		
		String configFile = PropsUtil.getPropOrEnv("guido.classconfig");
		String gitHubURL = PropsUtil.getPropOrEnv("guido.githuburl");
		String bitBucketURL = PropsUtil.getPropOrEnv("guido.bitbucketurl");

		if(bitBucketURL != null) {
			classConfigurer.loadClassConfig(
						new BitBucketStashConfigurationWatcher(bitBucketURL, 
								PropsUtil.getPropOrEnv(GUIDO_BITBUCKET_USER),
								PropsUtil.getPropOrEnv(GUIDO_BITBUCKET_PASSWORD),
								30), 
							new Reload() {
								@Override
								public void doReload() {
									reloadAllReferences();
								}
			});
		}
		if(gitHubURL != null) {
			classConfigurer.loadClassConfig(
					new GithubConfigurationWatcher(gitHubURL, 
								PropsUtil.getPropOrEnv(GUIDO_BITBUCKET_USER),
								PropsUtil.getPropOrEnv(GUIDO_BITBUCKET_PASSWORD),
								30), 
						new Reload() {
							@Override
							public void doReload() {
								reloadAllReferences();
							}
			});
		} else if(configFile != null) {
			classConfigurer.loadClassConfig(new FileConfigurationWatcher(configFile, 30), new Reload() {
				@Override
				public void doReload() {
					reloadAllReferences();
				}
			});
		}
	}

	protected void reloadAllReferences() {
		guidoLOG.info("Start modifying class definitions");
		int totalChanged = 0;
		synchronized(references) {
			for(Entry<Integer, Object[]> entry : references.entrySet()) {
				PatternMethodConfig newConfig = classConfigurer.configFor((String)(entry.getValue()[REF_LONG_NAME]));
				if(isReferenceDifferent(entry.getKey(), newConfig)) {
					updateReference(entry.getKey(), newConfig);
					totalChanged++;
				}
			}
		}
		guidoLOG.info("Method definitions modified - " + totalChanged + "/" + references.size() + " method(s) modified");
	}

	private boolean isReferenceDifferent(int index, PatternMethodConfig newConfig) {
		boolean changed =  
				(boolean)references.get(index)[REF_ALLOWED] != newConfig.isAllowed()
				|| (long)references.get(index)[REF_THRESHOLD] != (newConfig.getThreshold() == -1 ? threshold : newConfig.getThreshold());
		return changed;
	}

	private void getMethodThreshold() {
		String thresholdProp = getPropOrEnv("guido.threshold", "0.5");
		this.threshold = PropsUtil.toNano(thresholdProp);
	}

	private void setInterceptorRefs() {
		pid = UUID.randomUUID().toString();
		GuidoInterceptor.pid = pid;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void createLogger() {
		LoggerContext loggerContext = new LoggerContext();
		
		LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		
		Appender globalAppender = null;
		if(getPropOrEnvBoolean("guido.showLogsOnConsole")) {
			globalAppender = createConsoleAppender(loggerContext);
		} else {
			globalAppender = createLogmaticAppender(loggerContext);
		}
		LOG.addAppender(globalAppender);
		
		for(Entry<String, String> prop : extraProps.entrySet()) {
			MDC.put(prop.getKey(), prop.getValue());
		}
		
		LOG.setAdditive(false);
		loggerContext.start();
	}
	
	@SuppressWarnings("rawtypes")
	private Appender createLogmaticAppender(LoggerContext loggerContext) {
		String logmaticKey = getPropOrEnv("guido.logmaticKey");
		if(logmaticKey == null) {
			throw new RuntimeException("guido.logmaticKey is missing - set it as -Dguido.logmaticKey=x or in a guido.logmaticKey env property");
		}
		String destination = getPropOrEnv("guido.destination", "api.logmatic.io:10514");
		
		GuidoLogstashEncoder encoder = new GuidoLogstashEncoder();
		if(getPropOrEnvBoolean("guido.useJson")) {
			encoder.setMessageProvider(new GuidoJsonJsonMessageProvider(jsonFieldNames));
		} else {
			encoder.setMessageProvider(new GuidoJsonMessageProvider(addons));
		}
		String appName = getPropOrEnv("guido.appname", "default-app-name");
		
		encoder.setCustomFields(String.format("{\"logmaticKey\":\"%s\", \"appname\" : \"%s\"}", logmaticKey, appName));
		encoder.start();
		
		LogstashTcpSocketAppender tcpSocketAppender = new LogstashTcpSocketAppender();
		tcpSocketAppender.addDestination(destination);
		tcpSocketAppender.setKeepAliveDuration(Duration.buildByMinutes(1));
		tcpSocketAppender.setEncoder(encoder);
		tcpSocketAppender.setContext(loggerContext);
		tcpSocketAppender.start();
		return tcpSocketAppender;

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Appender createConsoleAppender(LoggerContext loggerContext) {
		LoggingEventAsyncDisruptorAppender disruptorAppender = new LoggingEventAsyncDisruptorAppender();
		disruptorAppender.setContext(loggerContext);
		
		ConsoleAppender consoleAppender = new ConsoleAppender();
		GuidoLogstashEncoder consoleEncoder = new GuidoLogstashEncoder();
		consoleEncoder.setMessageProvider(new GuidoJsonJsonMessageProvider(jsonFieldNames));
		consoleEncoder.start();
		consoleAppender.setEncoder(consoleEncoder);
		consoleAppender.setContext(loggerContext);
		consoleAppender.start();
		disruptorAppender.addAppender(consoleAppender);
		disruptorAppender.start();
		return disruptorAppender;
	}

	private void createDefaults() {
		pool = ClassPool.getDefault();
		GuidoInterceptor.queue = queue;
		GuidoInterceptor.references = references;
		GuidoInterceptor.threshold = threshold;
		GuidoInterceptor.extraProps = extraProps;
	}
	
	private int createReference(String methodName) {
		int ref = methodName.hashCode();
		synchronized(references) {
			references.put(ref, newMethodReference(methodName));
		}
		return ref;
	}
	
	private Object[] newMethodReference(String methodName) {
		Object[] ref = new Object[TOTAL_REF];

		ref[REF_ALLOWED] = true;
		ref[REF_SHORT_SIGNATURE] = methodName.replaceAll("\\$", "-");
		ref[REF_THRESHOLD] = threshold;
		ref[REF_COUNT] = (long)0;
		ref[REF_LONG_NAME] = methodName;
		return ref;
	}
	
	ExecutorService qTransformerExecutor = ThreadExecutorUtils.newFixedThreadPool(10);
	
	class LOGQListener implements Runnable {
		@Override
		public void run() {
			for(;;) {
				try {
					Object[] logContents = queue.take();
					logContents[4] = toMicroSec(logContents[4]);
					LOG.info("pid={} threadUuid={} depth={} methodCalled={} duration={}", logContents);
					if(statsFlag) {
						logRate.increment();
					}
				} catch(InterruptedException ie) {
					guidoLOG.error("ie exception in loop take()");
					return;
				} catch(Exception e) {
					guidoLOG.error("exception in loop take()", e);
				}
			}
		}

		private Object toMicroSec(Object object) {
			Double duration = (double)(long)object;
			return duration / 1000.00;
		}
	}
	
	class ShowStatsDumper implements Runnable {				
		@Override
		public void run() {
			guidoLOG.info("stats dump thread started");
			DecimalFormat df = new DecimalFormat("###,###.###"); 
			//NumberFormat numberFormat = new NumberFormat("###,###");
			for(;;) {
				try {
					Thread.sleep(30 * 1000);
					showSentAndMethodsStats(df);
					showBytecodeStats(df);
				} catch(InterruptedException ie) {
					guidoLOG.info("InterruptedException in loop take()");
					return;
				} catch(Exception e) {
					guidoLOG.error(e, "exception in thred stat dump");
					return;
				}
			}
		}

		private void showBytecodeStats(DecimalFormat df) {
			double totalSeen = ((double)totalByteCodeSeen)/1000000.00;
			double totalTransformedOrigin = ((double)totalByteCodeTransformedOrigin)/1000000.00;
			double totalTransformedAfter = ((double)totalByteCodeTransformedNewSize)/1000000.00;
			guidoLOG.output("Total bytecode seen by GuidoClassFileTransformer (MB) {}", df.format(totalSeen));
			guidoLOG.output("Total bytecode before transformation by GuidoClassFileTransformer (MB) {}", df.format(totalTransformedOrigin));
			guidoLOG.output("Total bytecode after transformation by GuidoClassFileTransformer (MB) {}", df.format(totalTransformedAfter));
			guidoLOG.output(" >> Bytecode inflation (MB) {}", df.format(totalTransformedAfter - totalTransformedOrigin));
			double percent = 0.0;
			if(totalSeen != 0.0) {
				percent = 100.0 * (totalTransformedAfter - totalTransformedOrigin)/totalSeen;
				
			}
			guidoLOG.output(" >> % of bytecode added is {} %", df.format(percent));
		}

		private void showSentAndMethodsStats(DecimalFormat df) {
			Statistics stats = logRate.getStatistics();
			guidoLOG.output(" >> message(s) sent {}", df.format(stats.getCountLong()));
			int total = 0;
			int totalOn = 0;
			int totalOff = 0;
			for(Object[] ref : references.values()) {
				if((boolean)ref[REF_ALLOWED]) {
					totalOn++;
				} else {
					totalOff++;
				}
				total++;
			}
			guidoLOG.output("Total of instrumented method(s) is {}, {} on, {} off",
					df.format(total), 
					df.format(totalOn),
					df.format(totalOff));
		}
	}
	
	private void startQListeners() {
		for(int index = 0; index < logQListeners; index++) {
			qTransformerExecutor.submit(new LOGQListener());
		}
		if(statsFlag) {
			qTransformerExecutor.submit(new ShowStatsDumper());
		}
	}
	
	private long totalByteCodeSeen = 0;
	private long totalByteCodeTransformedOrigin = 0;
	private long totalByteCodeTransformedNewSize = 0;
	
	@Override
	public byte[] transform(ClassLoader loader, 
								String className,
								Class<?> classBeingRedefined, 
								ProtectionDomain protectionDomain,
								byte[] classfileBuffer) throws IllegalClassFormatException {
			// A null loader means the bootstrap loader.
			// We do not instrument classes loaded by the bootstrap loader nor unnamed classes
		if(classfileBuffer != null) {
			totalByteCodeSeen += classfileBuffer.length;
		}
		
		if(loader == null || className == null) {
			return null;
		}

		String javaClassName = className.replaceAll("/", ".");
		if(!isObservableByName(javaClassName)) {
			return null;
		}
		
		CtClass cclass = getObjectClass(loader, javaClassName, classfileBuffer, protectionDomain);
		
		if(cclass == null) {
			return null;
		}
		
		try {
			boolean changed = false;
			for(CtMethod method : cclass.getDeclaredMethods()) {
				int methodModifier = method.getModifiers();
				if(Modifier.isAbstract(methodModifier) 
					|| Modifier.isNative(methodModifier)) 
				{
					if(guidoLOG.isDebugEnabled()) {
						guidoLOG.debug("Cannot intrument {}, incorect modifier {}", 
									cclass.getName() + '.' + method.getName(), 
									methodModifier);
					}
					continue;
				}
				try {
					String methodName = cclass.getName() + '.' + method.getName();
					int index = createReference(methodName);
					PatternMethodConfig config = classConfigurer.configFor(methodName);
					updateReference(index, config);
					method.insertBefore(GuidoInterceptor.insertBefore(index));
					method.insertAfter(GuidoInterceptor.insertAfter());
					CtClass etype = pool.get("java.lang.Throwable");
					method.addCatch(GuidoInterceptor.addCatch(), etype);
					changed = true;
				} catch(Exception e) {
					guidoLOG.debug("Exception 1 while transforming {}", method.getLongName());
					return null;
				}
			}
			guidoLOG.debug("instrumented {}",  cclass.getName());
			if(changed) {
				totalByteCodeTransformedOrigin += classfileBuffer.length;
				byte[] transformed = cclass.toBytecode();
				totalByteCodeTransformedNewSize += transformed.length;
				return transformed;
			}
			return null;
		} catch(Exception e) {
			guidoLOG.debug("exception 2 while transforming {}", cclass.getName());
		} finally {
			if(cclass != null) {
				cclass.detach();
			}
		}
		return null;
	}
	
	private CtClass getObjectClass(ClassLoader loader, String className, byte[] classfileBuffer, ProtectionDomain protectionDomain) {
		ClassPool newPool = createNewPool(loader, className, classfileBuffer);
		CtClass cclass = null;
		try {
			cclass = newPool.get(className);
			if(isFrozenOrClassLoader(cclass) 
					|| cclass.isInterface()
					|| cclass.isAnnotation()
					) { 
				guidoLOG.debug("{} is a classloader, interface or annotation - ignored.", className);
				return null;
			}
			return cclass;
		} catch(Exception e) {
			return null;
		}
	}

	private ClassPool createNewPool(ClassLoader loader, String className, byte[] classfileBuffer) {
		ClassPool newPool = new ClassPool(ClassPool.getDefault());
		newPool.insertClassPath(new LoaderClassPath(loader));
		newPool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
		return newPool;
	}

	private void updateReference(int index, PatternMethodConfig config) {
		Object[] reference = references.get(index);
		reference[REF_ALLOWED] = config.isAllowed();
		long configThreshold = config.getThreshold();
		reference[REF_THRESHOLD] = (configThreshold == -1 ? threshold : configThreshold);
	}

	static public boolean isElligeable(CtClass cclass, CtMethod method) {
		if(cclass.isInterface() || cclass.isEnum() || cclass.isAnnotation()) {
			return false;
		}
		return true;
	}

	public static boolean isFrozenOrClassLoader(CtClass cclass) {
		if(cclass.isFrozen()) {
			return true;
		}
		String parentClass = cclass.getName();
		try {
			String classLoader = ClassLoader.class.getCanonicalName();
			while(cclass != null) {
				String className = cclass.getName();
				if(classLoader.equals(className)) {
					// guidoLOG.debug(parentClass + " is a ClassLoader");
					return true;
				}
				cclass = cclass.getSuperclass();
			}
		} catch(Exception e) {
			guidoLOG.error(e, "Cannot check class {}", parentClass);
			return true;
		}
		return false;
	}
	
	static private String forbiddenStarts[] = new String[] {
	    	"com.sun.",
	    	"java.",
	    	"javax.",
	    	"org.ietf.",
	    	"org.jcp.",
	    	"org.omg.",
	    	"org.w3c.",
	    	"org.xml.",
	    	"sun.",
	    	"com.oracle.",
	    	"jdk.",
	    	"oracle.",
	    	"javafx.",
			"org.guido.",
			"oss.guido.",
			"javaslang.",
			"org.springframework."
	};
	
	static public String[] forbidenContents = new String[] {
		"CGLIB" // CGLIB induces VerifyError combined with Javassist.
	};
	
	static boolean ignorePackageRoot = PropsUtil.getPropOrEnvBoolean("guido.ignorepackageRoots", false);
	
	static public boolean isObservableByName(String className) {
		for(String forbiddenStart : forbiddenStarts) {
			if(className.startsWith(forbiddenStart)) {
				return false;
			}
		}
		for(String forbidenContent : forbidenContents) {
			if(className.contains(forbidenContent)) {
				return false;
			}
		}
		if(ignorePackageRoot) {
			return true;
		}
		for(String rootPackage : packageRoots) {
			if(className.startsWith(rootPackage)) {
				return true;
			}
		}
		return false;
	}
}
