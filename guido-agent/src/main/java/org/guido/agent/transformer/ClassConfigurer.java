package org.guido.agent.transformer;

import static org.guido.util.PropsUtil.toNano;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.AntPathMatcher;

import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class ClassConfigurer {
	
	public interface Reload {
		void doReload();
	}
	
	boolean loadInError = false;
	List<PerClassConfig> perClassConfigs = new ArrayList<PerClassConfig>();
	List<PerClassConfig> tmpPerClassConfigs = new ArrayList<PerClassConfig>();
	
	PerClassConfig notAllowedConfig = new PerClassConfig(null, -1, false);
	PerClassConfig allowedConfig = new PerClassConfig(null, -1, true);
	
	AntPathMatcher pathMatcher = new AntPathMatcher();
	boolean hasDynamicConfiguration;
	long lastModified = 0;
	
	/*
	 classname=threshold:x,on,off
	 */
	void endConfigure() {
		perClassConfigs = tmpPerClassConfigs;
	}
	
	public void reset() {
		while(!perClassConfigs.isEmpty()) {
			perClassConfigs.remove(0);
		}
	}
	
	void loadClassConfigFromFile(String fileName) {
		loadInError = false;
		File configFile = new File(fileName);
		try(BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
			String line;
			while((line = reader.readLine()) != null) {
				addLine(line);
			}
			// to minimize the time we modify perClassConfigs
			endConfigure();
			perClassConfigs = tmpPerClassConfigs; 
			lastModified = configFile.lastModified();
		} catch(Exception e) {
			loadInError = true;
		}
	}
	
	void addLine(String line) {
		line = line.trim();
		if("".equals(line)) {
			return;
		}
		if(line.startsWith("#")) {
			return;
		}
		PerClassConfig classConfig = parseLine(line);
		if(classConfig != null) {
			tmpPerClassConfigs.add(classConfig);
		}
	}

	PerClassConfig parseLine(String line) {
		String[] split = line.split("=");
		if(split.length != 2) {
			return null;
		}
		String className = split[0];
		String[] infos = split[1].split(",");
		long threshold = -1;
		boolean allowed = true;
		for(String info : infos) {
			info = info.trim().toLowerCase();
			if(info.startsWith("threshold:")) {
				threshold = toNano(info.substring("threshold:".length()));
			} else if("on".equals(info)) {
				allowed = true;
			} else if("off".equals(info)) {
				allowed = false;
			}
		}
		return new PerClassConfig(className, threshold, allowed);
	}

	public void loadClassConfig(final String configFile, final Reload reload) {
		if(configFile == null) {
			return;
		}
		hasDynamicConfiguration = true;
		loadClassConfigFromFile(configFile);
		new Thread(new Runnable() {
			@Override
			public void run() {
				for(;;) {
					try {
						Thread.sleep(30 * 1000);
						long newLastModified = new File(configFile).lastModified();
						if(newLastModified > lastModified) {
							loadClassConfigFromFile(configFile);
							GuidoLogger.debug("reloading file " + configFile);
							reload.doReload();
						}
					} catch(InterruptedException e) {
						return;
					} catch(Exception ie) {
						continue;
					}
				}
			}
			
		}).start();
	}
	
	public boolean hasDynConfiguration() {
		return hasDynamicConfiguration;
	}

	public PerClassConfig configFor(CtMethod method) {
		PerClassConfig config = configFor(method.getDeclaringClass(), method);
		if(!config.allowed) {
			//debug("not allowed at first, checking interfaces");
			if(configForInterfaces(method.getDeclaringClass(), method)) {
				//debug("found in interfarces");
				return new PerClassConfig(config.className, config.threshold, true);
			} else {
				return notAllowedConfig;
			}
		}
		return config;
	}
	
	private boolean configForInterfaces(CtClass clazz, CtMethod method) {
		try {
			CtClass[] interfaces = clazz.getInterfaces();
			for(CtClass itf : interfaces) {
				try {
					//debug("getting methods " + method.getName() + " in interface " + itf.getName());
					CtMethod[] methods = itf.getDeclaredMethods(method.getName());
					for(CtMethod sub : methods) {
						if(sub.equals(method)) {
							//debug("methods are equals, checking rules for method in interface");
							return configFor(itf, method).isAllowed();
						}
					}
				} catch(Exception e) {
					//debug("no methods found");
					continue;
				}
				if(configForInterfaces(itf, method) == true) {
					return true;
				}
			}
			return false;
		} catch(Exception e) {
			return false;
		}
	}

	protected PerClassConfig configFor(CtClass clazz, CtMethod method) {
		String methodName = clazz.getName() +  "." + method.getName();
		boolean notAllowed = false;
		for(PerClassConfig config : perClassConfigs) {
			if(pathMatcher.match(config.className, methodName)) {
				//debug("match for " + config.className + ", path " + methodName);
				if(!config.allowed) {
					notAllowed = true;
				} else {
					return config;
				}
			}
		}
		return (notAllowed) ? notAllowedConfig : allowedConfig;
	}
}
