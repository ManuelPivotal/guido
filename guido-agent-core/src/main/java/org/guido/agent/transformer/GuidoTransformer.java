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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

import org.guido.agent.logs.provider.GuidoJsonJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider.MessageAddon;
import org.guido.agent.logs.provider.GuidoLogstashEncoder;
import org.guido.agent.stats.ExponentialMovingAverageRate;
import org.guido.agent.transformer.PatternMethodConfigurer.Reload;
import org.guido.agent.transformer.interceptor.GuidoInterceptor;
import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.PropsUtil;

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
	private GuidoLogger guidoLOG = GuidoLogger.getLogger("GuidoTransformer");
	
	List<Class<?>> addedClassLoader = new ArrayList<Class<?>>();
	ClassPool pool;
	LinkedBlockingDeque<Object[]> queue = new LinkedBlockingDeque<Object[]>();
	List<Object[]> references = new ArrayList<Object[]>(32 * 1024);
	List<CtMethod> methods = new ArrayList<CtMethod>(32 * 1024);
	Map<String, String> extraProps = new HashMap<String, String>();
	private long threshold;
	private PatternMethodConfigurer classConfigurer;
	
	String propEx = "guido.ext.";
	int propExLength = propEx.length();
	MessageAddon[] addons = new MessageAddon[0];
	int logQListeners = 1;
	
	String[] jsonFieldNames = new String[] {
			"pid", 
			"threadUuid", 
			"depth", 
			"methodCalled", 
			"duration"
	};
	
	ExponentialMovingAverageRate logRate = new ExponentialMovingAverageRate();
	
	boolean traceFlag = false;
	
	public GuidoTransformer() {
		getExtraProps();
		getTraceFlag();
		createClassConfigurer();
		getMethodThreshold();
		buildAddons();
		createLogger();
		createDefaults();
		setPid();
		getQListenersCount();
		startQListeners();
	}
	
	private void getTraceFlag() {
		traceFlag = PropsUtil.getPropOrEnv("guido.__trace") != null;
		if(traceFlag) {
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

	private void createClassConfigurer() {
		classConfigurer = new PatternMethodConfigurer();
		classConfigurer.defaultIsOff();
		if(traceFlag) {
			classConfigurer.showMethodRules();
		}
		
		String configFile = PropsUtil.getPropOrEnv("guido.classconfig");
		if(configFile != null) {
			classConfigurer.loadClassConfig(PropsUtil.getPropOrEnv("guido.classconfig"), new Reload() {
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
		guidoLOG.info("Modifying method definitions - " + totalChanged + "/" + methods.size() + " method(s) modified");
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

	private void setPid() {
		String pid = UUID.randomUUID().toString();
		GuidoInterceptor.pid = pid;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void createLogger() {
		LoggerContext loggerContext = new LoggerContext();
		
		LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		
		Appender globalAppender = null;
		if(getPropOrEnv("guido.showLogsOnConsole") != null) {
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
		encoder.setMessageProvider(new GuidoJsonMessageProvider(addons));
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
	
	private void startQListeners() {
		for(int index = 0; index < logQListeners; index++) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					for(;;) {
						try {
							Object[] logContents = queue.take();
							logContents[4] = toMicroSec(logContents[4]);
							LOG.info("pid={} threadUuid={} depth={} methodCalled={} duration={}", logContents);
							if(traceFlag) {
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
					long nanoSec = (long)object;
					Long d = nanoSec % 1000;
					Long l = nanoSec / 1000;
					return l.toString() + "." + d.toString();
				}
			}).start();
		}
		if(traceFlag) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					guidoLOG.info("stats dump thread started");
					for(;;) {
						try {
							Thread.sleep(30 * 1000);
							guidoLOG.info(logRate.toString());
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
							guidoLOG.info("Total method is " + total 
									+ ", " + totalOn + " on, " + totalOff + " off");
						} catch(InterruptedException ie) {
							guidoLOG.error("ie exception in loop take()");
							return;
						} catch(Exception e) {
							guidoLOG.error("exception in thred stat dump", e);
							return;
						}
					}
				}
			}).start();
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
			// We do not instrument classes loaded by the bootstrap loader.
			if(loader == null) {
				return null;
			}
			
			boolean allowdebug = false;
			addLoaderToPool(loader, protectionDomain);
			
			if(className == null) {
				guidoLOG.info("classname is null");
				if(classBeingRedefined != null) {
					guidoLOG.info("classBeingRedefined is " + classBeingRedefined.getCanonicalName());
				}
				if(classfileBuffer != null) {
					guidoLOG.info("classfileBuffer is not null");
				}
				return null;
			}
			
			pool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
			CtClass cclass = null;
			
			try {
				cclass = pool.get(className.replaceAll("/", "."));
			} catch(Exception e) {
				if(allowdebug)
					guidoLOG.error(className + " not in pool", e);
				return null;
			}
			
			if(isObservableClass(cclass)) {
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
								guidoLOG.error("Error while transforming " + method.getLongName(), e);
							}
						}
					}
					return changed ? cclass.toBytecode() : null;
				}
			}
		} catch(Exception e) {
			guidoLOG.error("Error while transforming " + className, e);
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
		if(!addedClassLoader.contains(loader.getClass())) {
			guidoLOG.debug("@@@@ adding class loader of class " + loader.getClass().getCanonicalName());
			guidoLOG.debug("@@@@ loader instance of ClassLoader is " + (loader instanceof ClassLoader));
			addedClassLoader.add(loader.getClass());
			pool.appendClassPath(new LoaderClassPath(loader));
			forceGuidoClassesToLoader(loader, protectionDomain);
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
		
		if(!loaderClass.contains("DelegatingClassLoader")) {
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
				guidoLOG.error(e, "cannot force {} to load guido classes.", loader.getClass()); 
				return;
			} catch(LinkageError le) {
				// already in the class loader space
			}
			guidoLOG.debug("@@@@ forcing load of Guido classes done.");
		}
	}

	private Map<String, Object> buildConstructorArg() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("refs", GuidoInterceptor.references);
		params.put("logQ", queue);
		params.put("pid", GuidoInterceptor.pid);
		params.put("threshold", GuidoInterceptor.threshold);
		params.put("extraprops", GuidoInterceptor.extraProps);
		return params;
	}
	
	private boolean isObservableClass(CtClass cclass) {
		return isObservableByName(cclass) && isObservableByClass(cclass);
	}
	
	private boolean isObservableByClass(CtClass cclass) {
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
					return false;
				}
				cclass = cclass.getSuperclass();
			}
		} catch(Exception e) {
			guidoLOG.error("Cannot check class {}", cclass.getName(), e);
			return false;
		}
		return true;
	}

	String forbiddenStarts[] = new String[] {
			"java/", 
			"javax/", 
			"sun/", 
			"com/sun/", 
			"org/guido/", 
			"oss/guido/",
			//"org/springframework/",
			"java.", 
			"javax.", 
			"sun.", 
			"com.sun.", 
			"org.guido.",
			"oss.guido."
			//"org.springframework."
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
