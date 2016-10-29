package org.guido.agent.transformer.configuration;

import static org.guido.util.PropsUtil.toNano;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.AntPathMatcher;

import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class PatternMethodConfigurer implements ConfigurationNotify {
	
	GuidoLogger guidoLOG = GuidoLogger.getLogger("PatternMethodConfigurer");
	
	public interface Reload {
		void doReload();
	}
	
	boolean loadInError = false;
	List<PatternMethodConfig> perClassConfigs = new ArrayList<PatternMethodConfig>();
	List<PatternMethodConfig> tmpPerClassConfigs;
	
	PatternMethodConfig notAllowedConfig = new PatternMethodConfig(null, -1, false);
	PatternMethodConfig allowedConfig = new PatternMethodConfig(null, -1, true);
	
	private Reload reload;
	
	AntPathMatcher pathMatcher = new AntPathMatcher();
	
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
	
	List<PatternMethodConfig> getRules() {
		return perClassConfigs;
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

	public void loadClassConfig(ConfigurationWatcher watcher, final Reload reload) {
		this.reload = reload;
		watcher.configurationNotify(this);
		watcher.start();
	}
	
	public PatternMethodConfig configFor(CtMethod method) {
		String rootMethod = method.getDeclaringClass().getName() +  "." + method.getName();
		PatternMethodConfig config = configFor(method.getDeclaringClass(), method, rootMethod);
		if(!config.allowed) {
			return configForInterfaces(method.getDeclaringClass(), method, rootMethod);
		}
		return config;
	}
	
	private PatternMethodConfig configForInterfaces(CtClass clazz, CtMethod method, String rootMethod) {
		try {
			CtClass[] interfaces = clazz.getInterfaces();
			for(CtClass itf : interfaces) {
				try {
					//debug("getting methods " + method.getName() + " in interface " + itf.getName());
					CtMethod[] methods = itf.getDeclaredMethods(method.getName());
					for(CtMethod sub : methods) {
						if(sub.equals(method)) {
							//debug("methods are equals, checking rules for method in interface");
							return configFor(itf, method, rootMethod);
						}
					}
				} catch(Exception e) {
					continue;
				}
				PatternMethodConfig itfConfig = configForInterfaces(itf, method, rootMethod);
				if(itfConfig.isAllowed()) {
					return itfConfig; 
				}
			}
			return notAllowedConfig;
		} catch(Exception e) {
			return notAllowedConfig;
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
		PatternMethodConfig foundOnConfig = allowedConfig;
		
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
					foundOnConfig = config;
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
			if(foundOff && !foundOn) {
				return notAllowedConfig;
			}
			return foundOn ? foundOnConfig : allowedConfig;
		} else { // default off : 
			return foundOn && !foundOff ? foundOnConfig : notAllowedConfig;
		}
	}

	@Override
	public void onError() {
		startConfigure();
		endConfigure();
		reload.doReload();
	}

	@Override
	public void onLoaded(BufferedReader reader) throws Exception {
		startConfigure();
		String line;
		while((line = reader.readLine()) != null) {
			addLine(line);
		}
		endConfigure();
		reload.doReload();
	}
}
