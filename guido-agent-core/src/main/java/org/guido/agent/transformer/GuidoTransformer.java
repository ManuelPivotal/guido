package org.guido.agent.transformer;

import static org.guido.agent.transformer.interceptor.GuidoInterceptor.addCatch;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertAfter;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertBefore;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_ALLOWED;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_CLASS_NAME;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_COUNT;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_SHORT_SIGNATURE;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.REF_THRESHOLD;
import static org.guido.agent.transformer.interceptor.ReferenceIndex.TOTAL_REF;
import static org.guido.util.PropsUtil.getPropOrEnv;
import static org.guido.util.PropsUtil.getPropOrEnvBoolean;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
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
import org.guido.util.ExceptionUtil;
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
import oss.guido.javassist.CannotCompileException;
import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;
import oss.guido.javassist.LoaderClassPath;
import oss.guido.net.logstash.logback.appender.LoggingEventAsyncDisruptorAppender;
import oss.guido.net.logstash.logback.appender.LogstashTcpSocketAppender;
import oss.guido.org.slf4j.MDC;

public class GuidoTransformer implements ClassFileTransformer {
	
	private Logger LOG;
	private GuidoLogger guidoLOG = GuidoLogger.getLogger("GuidoTransformer");
	
	List<String> addedClassLoader = new ArrayList<String>();
	ClassPool pool;
	LinkedBlockingDeque<Object[]> queue = new LinkedBlockingDeque<Object[]>(8192);
	List<Object[]> references = new ArrayList<Object[]>(32 * 1024);
	List<CtMethod> methods = new ArrayList<CtMethod>(32 * 1024);
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
		classConfigurer = new PatternMethodConfigurer();
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
			for(int index = 0; index < methods.size(); index++) {
				PatternMethodConfig newConfig = classConfigurer.configFor(methods.get(index));
				if(isReferenceDifferent(index, newConfig)) {
					updateReference(index, newConfig);
					totalChanged++;
				}
			}
		}
		guidoLOG.info("Method definitions modified - " + totalChanged + "/" + methods.size() + " method(s) modified");
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
	
	private int createReference(CtMethod method) {
		int referenceNumber;
		synchronized(references) {
			references.add(newMethodReference(method));
			methods.add(method);
			referenceNumber = references.size() - 1;
		}
		return referenceNumber;
	}
	
	private Object[] newMethodReference(CtMethod method) {
		Object[] ref = new Object[TOTAL_REF];
		String name = method.getDeclaringClass().getName() + "." + method.getName();
		name = name.replaceAll("\\$", "-");

		ref[REF_ALLOWED] = true;
		ref[REF_CLASS_NAME] = method.getDeclaringClass().getSimpleName();
		ref[REF_SHORT_SIGNATURE] = name; //method.getDeclaringClass().getName() + "." + method.getName();
		ref[REF_THRESHOLD] = threshold;
		ref[REF_COUNT] = (long)0;
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
	
	class MemStatsDumper implements Runnable {				
		@Override
		public void run() {
			guidoLOG.info("stats dump thread started");
			DecimalFormat df = new DecimalFormat("###,###.###"); 
			//NumberFormat numberFormat = new NumberFormat("###,###");
			for(;;) {
				try {
					Thread.sleep(30 * 1000);
					Statistics stats = logRate.getStatistics();
					guidoLOG.info("{[sent={}]}", df.format(stats.getCountLong()));
					int total = 0;
					int totalOn = 0;
					int totalOff = 0;
					for(Object[] ref : references) {
						if((boolean)ref[REF_ALLOWED]) {
							totalOn++;
						} else {
							totalOff++;
						}
						total++;
					}
					guidoLOG.info("Total method is {}, {} on, {} off",
							total, 
							totalOn,
							totalOff);
				} catch(InterruptedException ie) {
					guidoLOG.info("InterruptedException in loop take()");
					return;
				} catch(Exception e) {
					guidoLOG.error(e, "exception in thred stat dump");
					return;
				}
			}
		}
	}
	
	private void startQListeners() {
		int totalThreads = logQListeners;
		if(statsFlag) {
			totalThreads++;
		}
		//qListenerExecutor = Executors.newFixedThreadPool(totalThreads);
		for(int index = 0; index < logQListeners; index++) {
			qTransformerExecutor.submit(new LOGQListener());
		}
		if(statsFlag) {
			qTransformerExecutor.submit(new MemStatsDumper());
		}
	}
	
	@Override
	public byte[] transform(ClassLoader loader, 
								String className,
								Class<?> classBeingRedefined, 
								ProtectionDomain protectionDomain,
								byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			// A null loader means the bootstrap loader.
			// We do not instrument classes loaded by the bootstrap loader nor unnamed classes
			if(loader == null || className == null) {
				return null;
			}

			ClassPool newPool = new ClassPool(ClassPool.getDefault());
			newPool.insertClassPath(new LoaderClassPath(loader));

			addLoaderToPool(loader, protectionDomain);
			
			newPool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
			CtClass cclass = null;
			
			try {
				cclass = newPool.get(className.replaceAll("/", "."));
			} catch(Exception e) {
				return null;
			}
			
			if(isClassLoader(cclass)) {
				return null; //createHook(cclass);
			}
			
			if(isObservableByName(cclass)) {
				boolean changed = false;
				for(CtMethod method : cclass.getDeclaredMethods()) {
					if(!method.isEmpty()) {
						if(isElligeable(cclass, method)) {
							try {
								int index = createReference(method);
								PatternMethodConfig config = classConfigurer.configFor(method);
								updateReference(index, config);
								method.insertBefore(insertBefore(index));
								method.insertAfter(insertAfter());
								CtClass etype = pool.get("java.lang.Throwable");
								method.addCatch(addCatch(), etype);
								changed = true;
							} catch(Exception e) {
								guidoLOG.info("Cannot transform {} - probably missing dependencies", method.getLongName());
							}
						}
					}
					return changed ? cclass.toBytecode() : null;
				}
			}
			cclass.detach();
		} catch(Exception e) {
			guidoLOG.error(e, "Error while transforming {}", e);
		}
		return null;
	}

	private void updateReference(int index, PatternMethodConfig config) {
		Object[] reference = references.get(index);
		reference[REF_ALLOWED] = config.isAllowed();
		long configThreshold = config.getThreshold();
		reference[REF_THRESHOLD] = (configThreshold == -1 ? threshold : configThreshold);
	}

	private boolean isElligeable(CtClass cclass, CtMethod method) {
		if(cclass.isInterface() || cclass.isEnum() || cclass.isAnnotation()) {
			return false;
		}
		return true;
	}

	private void addLoaderToPool(ClassLoader loader, ProtectionDomain protectionDomain) {
		String loaderClass = loader.getClass().getCanonicalName();
		if(!loaderClass.contains("DelegatingClassLoader")) {
			String signature = String.valueOf(loader.hashCode());
			if(!addedClassLoader.contains(signature)) {
				guidoLOG.debug("Adding new class loader of {} signature [{}]", loaderClass, signature);
				addedClassLoader.add(signature);
				//pool.insertClassPath(new LoaderClassPath(loader));
				forceGuidoClassesToLoader(loader, protectionDomain);
			}
		}
	}

	private void forceGuidoClassesToLoader(ClassLoader loader, ProtectionDomain protectionDomain) {
		String loaderClass = loader.getClass().getCanonicalName();
		String binaryName = GuidoInterceptor.class.getCanonicalName().replaceAll("\\.", "/");
		guidoLOG.debug(String.format("@@@@ checking %s in %s", binaryName, loaderClass));
		try {
			loader.loadClass(binaryName);
			guidoLOG.debug("@@@@ " + binaryName + " exists in " + loaderClass);
			return;
		} catch(Throwable e) {
			guidoLOG.debug("@@@@ " + binaryName + " does not exist in " + loaderClass);
		}
		guidoLOG.debug("@@@@ forcing load of Guido classes by {}", loaderClass);
		try {
			for(Class<?> clazz : GuidoInterceptor.toLoad) {
				CtClass guidoClass = pool.get(clazz.getCanonicalName());
				guidoClass.toClass(loader, protectionDomain);
			}
			CtClass interceptorCtClass = pool.get(GuidoInterceptor.class.getCanonicalName());
			Class<?> interceptorClass = interceptorCtClass.toClass(loader, protectionDomain);
			Map<String, Object> params = buildConstructorArg();
			interceptorClass.getDeclaredConstructor(Object.class).newInstance(params);
		} catch(Exception e) {
			Throwable rootCause = ExceptionUtil.getRootCause(e);
			if(rootCause instanceof LinkageError || rootCause instanceof CannotCompileException) {
				return;
			}
			guidoLOG.error(e, "cannot force {} to load guido classes.", loader.getClass()); 
		} catch(LinkageError le) {
			// already in the class loader space
		}
		guidoLOG.debug("@@@@ forcing load of Guido classes done.");
	}

	private Map<String, Object> buildConstructorArg() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("refs", GuidoInterceptor.references);
		params.put("logQ", queue);
		params.put("pid", GuidoInterceptor.pid);
		params.put("threshold", GuidoInterceptor.threshold);
		params.put("extraprops", GuidoInterceptor.extraProps);
		params.put("stopMeLatch", GuidoInterceptor.stopMeLatch);
		return params;
	}
	
	private boolean isClassLoader(CtClass cclass) {
		if(cclass.isFrozen()) {
			return false;
		}
		try {
			String classLoader = ClassLoader.class.getCanonicalName();
			String parentClass = cclass.getName();
			while(cclass != null) {
				String className = cclass.getName();
				if(classLoader.equals(className)) {
					guidoLOG.debug(parentClass + " is a ClassLoader");
					return true;
				}
				cclass = cclass.getSuperclass();
			}
		} catch(Exception e) {
			guidoLOG.error(e, "Cannot check class {}", cclass.getName());
			return false;
		}
		return false;
	}
	
	String forbiddenStarts[] = new String[] {
			"java/", 
			"javax/", 
			"sun/", 
			"com/sun/", 
			"org/guido/", 
			"oss/guido/",
			"java.", 
			"javax.", 
			"sun.", 
			"com.sun.", 
			"org.guido.",
			"oss.guido."
			};
	
	private boolean isObservableByName(CtClass cclass) {
		String className = cclass.getName();
		for(String forbiddenStart : forbiddenStarts) {
			if(className.startsWith(forbiddenStart)) {
				return false;
			}
		}
		return true;
	}
}
