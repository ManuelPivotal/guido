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

public class PatternMethodConfigurer {
	
	GuidoLogger guidoLOG = GuidoLogger.getLogger("PatternMethodConfigurer");
	
	public interface Reload {
		void doReload();
	}
	
	boolean loadInError = false;
	List<PatternMethodConfig> perClassConfigs = new ArrayList<PatternMethodConfig>();
	List<PatternMethodConfig> tmpPerClassConfigs;
	
	PatternMethodConfig notAllowedConfig = new PatternMethodConfig(null, -1, false);
	PatternMethodConfig allowedConfig = new PatternMethodConfig(null, -1, true);
	
	AntPathMatcher pathMatcher = new AntPathMatcher();
	boolean hasDynamicConfiguration;
	long lastModified = 0;
	
	boolean defaultAllOff = true;
	boolean showMethodRules = false;
	
	public void defaultIsOff() {
		defaultAllOff = true;
	}
	
	public void defaultIsOn() {
		defaultAllOff = false;
	}
	
	public void showMethodRules() {
		showMethodRules = true;
	}

	/*
	 classname=threshold:x,on,off
	 classname is an ant like path with . as separator.
	 */
	void endConfigure() {
		perClassConfigs = tmpPerClassConfigs;
	}
	
	public void startConfigure() {
		tmpPerClassConfigs = new ArrayList<PatternMethodConfig>();
	}
	
	void loadClassConfigFromFile(String fileName) {
		loadInError = false;
		File configFile = new File(fileName);
		try(BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
			String line;
			startConfigure();
			while((line = reader.readLine()) != null) {
				addLine(line);
			}
			// to minimize the time we modify perClassConfigs
			endConfigure();
			perClassConfigs = tmpPerClassConfigs; 
			lastModified = configFile.lastModified();
		} catch(Exception e) {
			guidoLOG.error("Error loading method configuration file [" + fileName + "}", e);
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
		PatternMethodConfig classConfig = parseLine(line);
		if(classConfig != null) {
			tmpPerClassConfigs.add(classConfig);
		}
	}

	PatternMethodConfig parseLine(String line) {
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
		return new PatternMethodConfig(className, threshold, allowed);
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
							guidoLOG.debug("reloading file " + configFile);
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

	public PatternMethodConfig configFor(CtMethod method) {
		String rootMethod = method.getDeclaringClass().getName() +  "." + method.getName();
		PatternMethodConfig config = configFor(method.getDeclaringClass(), method, rootMethod);
		if(!config.allowed) {
			//debug("not allowed at first, checking interfaces");
			if(configForInterfaces(method.getDeclaringClass(), method, rootMethod)) {
				//debug("found in interfarces");
				return new PatternMethodConfig(config.className, config.threshold, true);
			} else {
				return notAllowedConfig;
			}
		}
		return config;
	}
	
	private boolean configForInterfaces(CtClass clazz, CtMethod method, String rootMethod) {
		try {
			CtClass[] interfaces = clazz.getInterfaces();
			for(CtClass itf : interfaces) {
				try {
					//debug("getting methods " + method.getName() + " in interface " + itf.getName());
					CtMethod[] methods = itf.getDeclaredMethods(method.getName());
					for(CtMethod sub : methods) {
						if(sub.equals(method)) {
							//debug("methods are equals, checking rules for method in interface");
							return configFor(itf, method, rootMethod).isAllowed();
						}
					}
				} catch(Exception e) {
					continue;
				}
				if(configForInterfaces(itf, method, rootMethod) == true) {
					return true;
				}
			}
			return false;
		} catch(Exception e) {
			return false;
		}
	}
	
	boolean isDefaultOn() {
		return !defaultAllOff;
	}
	boolean isDefaultOff() {
		return defaultAllOff;
	}

	protected PatternMethodConfig configFor(CtClass clazz, CtMethod method, String rootMethod) {
		String methodName = clazz.getName() +  "." + method.getName();
		boolean foundOn = false;
		boolean foundOff = false;
		for(PatternMethodConfig config : perClassConfigs) {
			if(pathMatcher.match(config.className, methodName)) {
				if(config.isAllowed()) {
					if(showMethodRules) {
						if(rootMethod.equals(methodName)) {
							guidoLOG.info("RULE: {} ON by rule {}", rootMethod, config.className);
						} else {
							guidoLOG.info("RULE: {} ON by interface method[{}] rule {}", rootMethod, methodName, config.className);
						}
					}
					foundOn = true;
				} else {
					if(showMethodRules) {
						if(rootMethod.equals(methodName)) {
							guidoLOG.info("RULE: {} OFF by rule {}", rootMethod, config.className);
						} else {
							guidoLOG.info("RULE: {} OFF by interface method[{}] and rule {}", rootMethod, methodName, config.className);
						}
					}
					foundOff = true;
				}
			}
		}
		// default on : 1 off and 0 on is off, otherwise on
		// default off : 1 on and 0 off is on, otherwise off
		if(isDefaultOn()) { // default on : 1 off and no on we are off, otherwise on
			return foundOff && !foundOn ? notAllowedConfig : allowedConfig;
		} else { // default off : 
			return foundOn && !foundOff ? allowedConfig : notAllowedConfig;
		}
	}
}
