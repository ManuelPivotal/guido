package org.guido.agent.transformer.configuration;

import static org.guido.util.PropsUtil.toNano;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.AntPathMatcher;

import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class PatternMethodConfigurer implements ConfigurationNotify {
	
	GuidoLogger LOG = GuidoLogger.getLogger("PatternMethodConfigurer");
	
	private String target;
	
	public interface Reload {
		void doReload();
	}
	
	public PatternMethodConfigurer() {
		this(null);
	}
	
	public PatternMethodConfigurer(String target) {
		this.target = target;
	}
	
	boolean loadInError = false;
	List<PatternMethodConfig> perClassConfigs = new ArrayList<PatternMethodConfig>();
	List<PatternMethodConfig> tmpPerClassConfigs;
	
	PatternMethodConfig notAllowedConfig = new PatternMethodConfig(null, -1, false, false);
	PatternMethodConfig allowedConfig = new PatternMethodConfig(null, -1, true, false);
	
	private Reload reload;
	
	AntPathMatcher pathMatcher = new AntPathMatcher();
	AntPathMatcher targetMatcher = new AntPathMatcher("|");
	
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
			if(showMethodRules) {
				LOG.info("Adding rule {} for group {}", line, target);
			}
			tmpPerClassConfigs.add(classConfig);
		}
	}

	PatternMethodConfig parseLine(String line) {
		String[] split = line.split("=");
		if(split.length != 2) {
			return null;
		}
		String className = split[0];
		String[] target = split[0].split("@");
		if(target.length == 2) {
			if(!targetingUs(target[1])) {
				return null;
			}
			LOG.debug("{} is tragetting {}", target[0], target);
			className = target[0];
		}
		boolean isInterface = false;
		int classNameLength = className.length();
		if(classNameLength > 2 && className.startsWith("[") && className.endsWith("]")) {
			className = className.substring(1, classNameLength - 1);
			isInterface = true;
		}
		
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
		return new PatternMethodConfig(className, threshold, allowed, isInterface);
	}
	
	class NullReload implements Reload {
		@Override
		public void doReload() {
		}
	}

	private boolean targetingUs(String configurationTarget) {
		return targetMatcher.match(configurationTarget, target);
	}
	
	public void loadClassConfig(ConfigurationWatcher watcher) {
		loadClassConfig(watcher, new NullReload());
	}
	
	public void loadClassConfig(ConfigurationWatcher watcher, final Reload reload) {
		this.reload = reload;
		watcher.configurationNotify(this);
		watcher.start();
	}
	
	public PatternMethodConfig configFor(CtMethod method) {
		String rootMethod = method.getDeclaringClass().getName() +  "." + method.getName();
		if(showMethodRules) {
			LOG.output("------------------------------------------------------------------");
			LOG.output("Getting rule for {}", rootMethod);
		}
		PatternMethodConfig config = configFor(method.getDeclaringClass(), method, rootMethod, false);
		if(!config.allowed) {
			PatternMethodConfig interfaceConfig = configForInterfaces(method.getDeclaringClass(), method, rootMethod);
			if(interfaceConfig.allowed) {
				config = interfaceConfig; // otherwise the first one was the good one.
			}
		}
		if(showMethodRules) {
			LOG.output("-> returning {}", config);
		}
		return config;
	}
	
	private PatternMethodConfig configForInterfaces(CtClass clazz, CtMethod method, String rootMethod) {
		try {
			CtClass[] interfaces = clazz.getInterfaces();
			for(CtClass itf : interfaces) {
				try {
					CtMethod[] methods = itf.getDeclaredMethods(method.getName());
					for(CtMethod sub : methods) {
						if(sub.equals(method)) {
							return configFor(itf, method, rootMethod, true);
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

	protected PatternMethodConfig configFor(CtClass clazz, CtMethod method, String rootMethod, boolean isInterface) {
		String methodName = clazz.getName() +  "." + method.getName();
		boolean foundOn = false;
		boolean foundOff = false;
		PatternMethodConfig foundOnConfig = allowedConfig;
		PatternMethodConfig foundOffConfig = notAllowedConfig;
		
		for(PatternMethodConfig config : perClassConfigs) {
			if(isInterface) {
				if(!config.isInterface() && config.isAllowed()) {
					continue;
				}
			}
			if(pathMatcher.match(config.className, methodName)) {
				if(config.isAllowed()) {
					if(showMethodRules) {
						if(rootMethod.equals(methodName)) {
							LOG.output("RULE: {} ON by rule {}, threshold:{}", rootMethod, config.className, (config.threshold == -1 ? "[DEFAULT]" : config.threshold));
						} else {
							LOG.output("RULE: {} ON by interface method[{}] rule {}, threshold:{}", rootMethod, methodName, config.className, (config.threshold == -1 ? "[DEFAULT]" : config.threshold));
						}
					}
					foundOn = true;
					foundOnConfig = config;
				} else {
					if(showMethodRules) {
						if(rootMethod.equals(methodName)) {
							LOG.output("RULE: {} OFF by rule {}", rootMethod, config.className);
						} else {
							LOG.output("RULE: {} OFF by interface method[{}] and rule {}", rootMethod, methodName, config.className);
						}
					}
					foundOff = true;
					foundOffConfig = config;
				}
			}
		}
		// default on : 1 off and 0 on is off, otherwise on
		// default off : 1 on and 0 off is on, otherwise off
		if(isDefaultOn()) { // default on : 1 off and no on we are off, otherwise on
			if(foundOff && !foundOn) {
				return foundOffConfig;
			}
			return foundOn ? foundOnConfig : allowedConfig;
		} else { // default off : 
			return foundOn && !foundOff ? foundOnConfig : foundOffConfig;
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

	public PatternMethodConfig configFor(Class<?> clazz, Method method) {
		String methodName = clazz.getName() +  "." + method.getName();
		boolean foundOn = false;
		boolean foundOff = false;
		PatternMethodConfig foundOnConfig = allowedConfig;
		PatternMethodConfig foundOffConfig = notAllowedConfig;

		for(PatternMethodConfig config : perClassConfigs) {
			if(pathMatcher.match(config.className, methodName)) {
				if(config.isAllowed()) {
					if(showMethodRules) {
						LOG.output("RULE: {} ON by rule {}, threshold:{}", methodName, config.className, (config.threshold == -1 ? "[DEFAULT]" : config.threshold));
					}
					foundOn = true;
					foundOnConfig = config;
				} else {
					if(showMethodRules) {
						LOG.output("RULE: {} OFF by rule {}", methodName, config.className);
					}
					foundOff = true;
					foundOffConfig = config;
				}
			}
		}
		// default on : 1 off and 0 on is off, otherwise on
		// default off : 1 on and 0 off is on, otherwise off
		if(isDefaultOn()) { // default on : 1 off and no on we are off, otherwise on
			if(foundOff && !foundOn) {
				return foundOffConfig;
			}
			return foundOn ? foundOnConfig : allowedConfig;
		} else { // default off : 
			return foundOn && !foundOff ? foundOnConfig : foundOffConfig;
		}
	}

	public PatternMethodConfig configFor(String methodName) {
		boolean foundOn = false;
		boolean foundOff = false;
		PatternMethodConfig foundOnConfig = allowedConfig;
		PatternMethodConfig foundOffConfig = notAllowedConfig;

		for(PatternMethodConfig config : perClassConfigs) {
			if(pathMatcher.match(config.className, methodName)) {
				if(config.isAllowed()) {
					if(showMethodRules) {
						LOG.output("RULE: {} ON by rule {}, threshold:{}", methodName, config.className, (config.threshold == -1 ? "[DEFAULT]" : config.threshold));
					}
					foundOn = true;
					foundOnConfig = config;
				} else {
					if(showMethodRules) {
						LOG.output("RULE: {} OFF by rule {}", methodName, config.className);
					}
					foundOff = true;
					foundOffConfig = config;
				}
			}
		}
		// default on : 1 off and 0 on is off, otherwise on
		// default off : 1 on and 0 off is on, otherwise off
		if(isDefaultOn()) { // default on : 1 off and no on we are off, otherwise on
			if(foundOff && !foundOn) {
				return foundOffConfig;
			}
			return foundOn ? foundOnConfig : allowedConfig;
		} else { // default off : 
			return foundOn && !foundOff ? foundOnConfig : foundOffConfig;
		}
	}
}
