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
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if(loader != null) {
				if(!addedClassLoader.contains(loader.getClass())) {
					debug("+++ adding class loader of class " + loader.getClass());
					addedClassLoader.add(loader.getClass());
					pool.appendClassPath(new LoaderClassPath(loader));
				}
			}
			
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
			
			if(className.contains("telaside")) {
				debug(className);
				pool.insertClassPath(new ByteArrayClassPath(className, classfileBuffer));
				CtClass cclass = null;
				try {
					cclass = pool.get(className.replaceAll("/", "."));
				} catch(Exception e) {
					info(className + " not in pool");
					return null;
				}
				if(!cclass.isFrozen()) {
					boolean changed = false;
					for(CtMethod method : cclass.getDeclaredMethods()) {
						debug(" >> " + method.getLongName());
						if(!method.isEmpty()) {
							changed = true;
							method.insertBefore(insertBefore(className, method));
							method.insertAfter(insertAfter(), true);
//							CtClass etype = pool.get("java.lang.Exception");
//							method.addCatch(addCatch(), etype);
						}
					}
					return (changed == true) ? cclass.toBytecode() : null;
				}
			}
		} catch(Exception e) {
			error("Error while transforming " + className, e);
		}
		return null;
	}
}
