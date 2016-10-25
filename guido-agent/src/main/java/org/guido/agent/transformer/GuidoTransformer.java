package org.guido.agent.transformer;

import static org.guido.agent.transformer.interceptor.GuidoInterceptor.addCatch;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertAfter;
import static org.guido.agent.transformer.interceptor.GuidoInterceptor.insertBefore;
import static org.guido.agent.transformer.logger.GuidoLogger.debug;
import static org.guido.agent.transformer.logger.GuidoLogger.error;
import static org.guido.agent.transformer.logger.GuidoLogger.info;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

import org.guido.agent.transformer.interceptor.GuidoInterceptor;

import oss.guido.ch.qos.logback.classic.Level;
import oss.guido.ch.qos.logback.classic.Logger;
import oss.guido.ch.qos.logback.classic.LoggerContext;
import oss.guido.ch.qos.logback.core.ConsoleAppender;
import oss.guido.ch.qos.logback.core.util.Duration;
import oss.guido.javassist.ByteArrayClassPath;
import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;
import oss.guido.javassist.LoaderClassPath;
import oss.guido.net.logstash.logback.appender.LogstashTcpSocketAppender;
import oss.guido.net.logstash.logback.encoder.LogstashEncoder;

public class GuidoTransformer implements ClassFileTransformer {
	
	private Logger LOG;
	
	List<Class<?>> addedClassLoader = new ArrayList<Class<?>>();
	ClassPool pool;
	LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<String>();
	
	public GuidoTransformer() {
		createLogger();
		createDefaults();
		startQListener();
		setPid();
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
		
		LogstashEncoder encoder = new LogstashEncoder();
		encoder.setCustomFields(String.format("{\"logmaticKey\":\"%s\"}", logmaticKey));
		encoder.start();
		
		LogstashTcpSocketAppender tcpSocketAppender = new LogstashTcpSocketAppender();
		tcpSocketAppender.addDestination(destination);
		tcpSocketAppender.setKeepAliveDuration(Duration.buildByMinutes(1));
		tcpSocketAppender.setEncoder(encoder);
		tcpSocketAppender.setContext(loggerContext);
		tcpSocketAppender.start();

		LOG = loggerContext.getLogger("json_tcp");
		LOG.setLevel(Level.INFO);
		LOG.addAppender(tcpSocketAppender);
		if(getPropOrEnv("guido.showLogsOnConsole") != null) {
			ConsoleAppender consoleAppender = new ConsoleAppender();
			LogstashEncoder consoleEncoder = new LogstashEncoder();
			consoleEncoder.start();
			consoleAppender.setEncoder(consoleEncoder);
			consoleAppender.setContext(loggerContext);
			consoleAppender.start();
			LOG.addAppender(consoleAppender);
		}
		LOG.setAdditive(false);
		loggerContext.start();
	}
	
	private String getPropOrEnv(String name, String defaultValue) {
		String value = System.getProperty(name);
		if(value == null) {
			value = System.getenv(name);
		}
		return (value == null) ? defaultValue : value;
	}
	
	private String getPropOrEnv(String name) {
		return getPropOrEnv(name, null);
	}

	private void createDefaults() {
		pool = ClassPool.getDefault();
		GuidoInterceptor.queue = queue;
	}
	
	private void startQListener() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for(;;) {
					try {
						String message = queue.take();
						//info(message);
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
						try {
							method.insertBefore(insertBefore(className, method));
							method.insertAfter(insertAfter());
							CtClass etype = pool.get("java.lang.Throwable");
							method.addCatch(addCatch(), etype);
							changed = true;
						} catch(Exception e) {
							error("Error while transforming " + method.getLongName(), e);
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
