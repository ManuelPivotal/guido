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
import java.util.List;

import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;

import org.guido.agent.transformer.interceptor.GuidoInterceptor;

public class GuidoTransformer implements ClassFileTransformer {
	
	private ClassPool pool;

	public GuidoTransformer() {
		pool = ClassPool.getDefault();
	}
	
	List<Class<?>> addedClassLoader = new ArrayList<Class<?>>();

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
				interceptorClass.getDeclaredConstructor(Object.class).newInstance(GuidoInterceptor.references);
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
