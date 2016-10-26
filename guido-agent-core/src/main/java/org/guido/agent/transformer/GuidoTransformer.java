package org.guido.agent.transformer;

import static org.guido.agent.transformer.interceptor.GuidoInterceptor.addCatch;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertAfter;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertBefore;
import static org.guido.agent.transformer.logger.GuidoLogger.debug;
import static org.guido.agent.transformer.logger.GuidoLogger.error;
import static org.guido.agent.transformer.logger.GuidoLogger.info;
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

import org.guido.agent.logs.provider.GuidoJsonMessageProvider;
import org.guido.agent.logs.provider.GuidoJsonMessageProvider.MessageAddon;
import org.guido.agent.logs.provider.GuidoLogstashEncoder;
import org.guido.agent.transformer.PatternMethodConfigurer.Reload;
import org.guido.agent.transformer.interceptor.GuidoInterceptor;
import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.PropsUtil;

import oss.guido.ch.qos.logback.classic.Level;
import oss.guido.ch.qos.logback.classic.Logger;
import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.classic.spi.ILoggingEvent;
import oss.guido.ch.qos.logback.core.ConsoleAppender;
import oss.guido.ch.qos.logback.core.util.Duration;
import oss.guido.javassist.ByteArrayClassPath;
import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;
import oss.guido.javassist.LoaderClassPath;
import oss.guido.javassist.bytecode.Descriptor;
import oss.guido.net.logstash.logback.appender.LogstashTcpSocketAppender;

public class GuidoTransformer implements ClassFileTransformer {
	
	private Logger LOG;
	
	List<Class<?>> addedClassLoader = new ArrayList<Class<?>>();
	ClassPool pool;
	LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<String>();
	List<Map<String, Object>> references = new ArrayList<Map<String, Object>>(32 * 1024);
	List<CtMethod> methods = new ArrayList<CtMethod>(32 * 1024);
	Map<String, String> extraProps = new HashMap<String, String>();
	private long threshold;
	private PatternMethodConfigurer classConfigurer;
	
	String propEx = "guido.ext.";
	int propExLength = propEx.length();
	MessageAddon[] addons = new MessageAddon[0];
	
	public GuidoTransformer() {
		getExtraProps();
		loadClassConfigurer();
		getMethodThreshold();
		buildAddons();
		createLogger();
		createDefaults();
		startQListener();
		setPid();
	}
	
	MessageAddon dateAddon = new MessageAddon() {
		@Override
		public String getAddon(ILoggingEvent event) {
			SimpleDateFormat format = new SimpleDateFormat("MMM d HH:mm:ss");
			return format.format(new Date(event.getTimeStamp()));
		}
	};
	
	private void buildAddons() {
		addons = new MessageAddon[] {
				dateAddon,
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return extraProps.get("hostname");
					}
				},
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return extraProps.get("facility");
					}
				},
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return "PERF";
					}
				},
				new MessageAddon() {
					@Override
					public String getAddon(ILoggingEvent event) {
						return "[" + event.getThreadName() + "]";
					}
				}
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

	private void loadClassConfigurer() {
		classConfigurer = new PatternMethodConfigurer();
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
		GuidoLogger.info("Start modifying class definitions");
		int totalChanged = 0;
		synchronized(references) {
			for(int index = 0; index < methods.size(); index++) {
				PatternMethodConfig newConfig = classConfigurer.configFor(methods.get(index));
				boolean changed = isReferenceDifferent(index, newConfig);
//				GuidoLogger.debug("Current is [" 
//						+ references.get(index).get("allowed")
//						+ ", " + references.get(index).get("threshold")  + "] new is ["
//						+ newConfig.isAllowed() + "," + newConfig.getThreshold() + "] -> " + changed 
//						+ " for " + methods.get(index).getDeclaringClass().getName()
//					);
				if(changed) {
					//GuidoLogger.debug("config has changed for " + methods.get(index).getLongName());
					updateReference(index, newConfig);
					totalChanged++;
				}
			}
		}
		GuidoLogger.info("Modifying method definitions - " + totalChanged + "/" + methods.size() + " method(s) modified");
	}

	private boolean isReferenceDifferent(int index, PatternMethodConfig newConfig) {
		boolean changed =  
				(boolean)references.get(index).get("allowed") != newConfig.isAllowed()
				|| (long)references.get(index).get("threshold") != (newConfig.getThreshold() == -1 ? threshold : newConfig.getThreshold());
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
		String logmaticKey = getPropOrEnv("guido.logmaticKey");
		if(logmaticKey == null) {
			throw new RuntimeException("guido.logmaticKey is missing - set it as -Dguido.logmaticKey=x or in a guido.logmaticKey env property");
		}
		String destination = getPropOrEnv("guido.destination", "api.logmatic.io:10514");
		LoggerContext loggerContext = new LoggerContext();
		
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

		LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		
		if(getPropOrEnv("guido.showLogsOnConsole") != null) {
			ConsoleAppender consoleAppender = new ConsoleAppender();
			GuidoLogstashEncoder consoleEncoder = new GuidoLogstashEncoder();
			encoder.setMessageProvider(new GuidoJsonMessageProvider(addons));
			consoleEncoder.start();
			consoleAppender.setEncoder(consoleEncoder);
			consoleAppender.setContext(loggerContext);
			consoleAppender.start();
			LOG.addAppender(consoleAppender);
		} else {
			LOG.addAppender(tcpSocketAppender);
		}
		
//		for(Entry<String, String> prop : extraProps.entrySet()) {
//			MDC.put(prop.getKey(), prop.getValue());
//		}
		
		LOG.setAdditive(false);
		loggerContext.start();
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
	
	private Map<String, Object> newMethodReference(CtMethod method) {
		Map<String, Object> ref = new HashMap<String, Object>();
		ref.put("allowed", Boolean.TRUE);
		ref.put("threshold", threshold);
		ref.put("class", method.getDeclaringClass());
		ref.put("className", method.getDeclaringClass().getSimpleName());
		ref.put("longName", method.getLongName());
		ref.put("name", method.getName());
		ref.put("shortSignature", method.getDeclaringClass().getName() + "." + method.getName());
		ref.put("signature", Descriptor.toString(method.getSignature()));
		return ref;
	}
	
	private void startQListener() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for(;;) {
					try {
						String message = queue.take();
						LOG.info(message);
					} catch(InterruptedException ie) {
						error("ie exception in loop take()", ie);
						return;
					} catch(Exception e) {
						error("exception in loop take()", e);
					}
				}
			}
		}).start();
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
				info("classname is null");
				if(classBeingRedefined != null) {
					info("classBeingRedefined is " + classBeingRedefined.getCanonicalName());
				}
				if(classfileBuffer != null) {
					info("classfileBuffer is not null");
				}
				return null;
			}
			
			pool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
			CtClass cclass = null;
			
			try {
				cclass = pool.get(className.replaceAll("/", "."));
			} catch(Exception e) {
				if(allowdebug)
					error(className + " not in pool", e);
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
								error("Error while transforming " + method.getLongName(), e);
							}
						}
					}
					return changed ? cclass.toBytecode() : null;
				}
			}
		} catch(Exception e) {
			error("Error while transforming " + className, e);
		}
		return null;
	}

	private void updateReference(int index, PatternMethodConfig config) {
		Map<String, Object> reference = references.get(index);
		reference.put("allowed", config.isAllowed());
		long configThreshold = config.getThreshold();
		reference.put("threshold", configThreshold == -1 ? threshold : configThreshold);
	}

	private boolean isElligeable(CtClass cclass, CtMethod method) {
		if(cclass.isInterface() || cclass.isEnum() || cclass.isAnnotation()) {
			return false;
		}
		return true;
	}

	private void addLoaderToPool(ClassLoader loader, ProtectionDomain protectionDomain) {
		if(!addedClassLoader.contains(loader.getClass())) {
			debug("@@@@ adding class loader of class " + loader.getClass().getCanonicalName());
			debug("@@@@ loader instance of ClassLoader is " + (loader instanceof ClassLoader));
			addedClassLoader.add(loader.getClass());
			pool.appendClassPath(new LoaderClassPath(loader));
			forceGuidoClassesToLoader(loader, protectionDomain);
		}
	}

	private void forceGuidoClassesToLoader(ClassLoader loader, ProtectionDomain protectionDomain) {
		String loaderClass = loader.getClass().getCanonicalName();
		String binaryName = GuidoInterceptor.class.getCanonicalName().replaceAll("\\.", "/");
		debug(String.format("@@@@ checking %s in %s", binaryName, loaderClass));
		try {
			loader.loadClass(binaryName);
			debug("@@@@ " + binaryName + " exists in " + loaderClass);
			return;
		} catch(Throwable e) {
			debug("@@@@ " + binaryName + " does not exist in " + loaderClass);
		}
		
		if(!loaderClass.contains("DelegatingClassLoader")) {
			debug("@@@@ forcing load of Guido classes by " + loader.getClass());
			try {
				for(Class<?> clazz : GuidoInterceptor.toLoad) {
					CtClass guidoClass = pool.get(clazz.getCanonicalName());
					guidoClass.toClass(loader, protectionDomain);
				}
				CtClass interceptorCtClass = pool.get(GuidoInterceptor.class.getCanonicalName());
				Class<?> interceptorClass = interceptorCtClass.toClass(loader, protectionDomain);
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("refs", GuidoInterceptor.references);
				params.put("logQ", queue);
				params.put("pid", GuidoInterceptor.pid);
				params.put("threshold", GuidoInterceptor.threshold);
				params.put("extraprops", GuidoInterceptor.extraProps);
				interceptorClass.getDeclaredConstructor(Object.class).newInstance(params);
				debug("@@@@ forcing load of Guido classes done.");
			} catch(Exception e) {
				error("cannot force " 
						+ loader.getClass() 
						+ " to load guido classes." 
						, e);
			}
		}
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
					debug(parentClass + " is a ClassLoader");
					return false;
				}
				cclass = cclass.getSuperclass();
			}
		} catch(Exception e) {
			error("Cannot check class " + cclass.getName(), e);
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
			//"org/springframework/",
			"java.", 
			"javax.", 
			"sun.", 
			"com.sun.", 
			"org.guido.", 
			//"org.springframework."
			};
	
	private boolean isObservableByName(CtClass cclass) {
		String className = cclass.getName();
		if(className.contains("telaside")) {
			return true;
		}
		for(String forbiddenStart : forbiddenStarts) {
			if(className.startsWith(forbiddenStart)) {
				return false;
			}
		}
		return true;
	}
}
