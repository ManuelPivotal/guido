package org.guido.agent;

import static org.guido.agent.transformer.configuration.ConfigurationWatcher.NO_POLL;

import java.io.FileInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.guido.agent.transformer.GuidoTransformer;
import org.guido.agent.transformer.configuration.FileConfigurationWatcher;
import org.guido.agent.transformer.configuration.PatternMethodConfig;
import org.guido.agent.transformer.configuration.PatternMethodConfigurer;
import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.javassist.ClassPool;
import oss.guido.javassist.CtClass;
import oss.guido.javassist.CtMethod;

public class GuidoCheck {

	private static GuidoLogger LOG = GuidoLogger.getLogger("check");

	public static void main(String[] args) throws Exception {
		GuidoLogger.setGlobalLogLevel(GuidoLogger.INFO);
		LOG.info("GuidoCheck started...");
		new GuidoCheck().run(args);
	}
	
	String conf = null;
	String jarfile = null;
	String hostname = null;
	ClassPool pool = ClassPool.getDefault();
	PatternMethodConfigurer classConfigurer;
	
	private void run(String args[]) throws Exception {
		checkArgs(args);
		loadConfiguration();
		loadClasses();
	}
	
	private void loadConfiguration() {
		classConfigurer = new PatternMethodConfigurer(hostname);
		classConfigurer.defaultIsOff();
		classConfigurer.showMethodRules();
		classConfigurer.loadClassConfig(new FileConfigurationWatcher(conf, NO_POLL));
	}

	int loadedSuccess = 0;
	int loadedFailed = 0;

	private void loadClasses() throws Exception {
		try(ZipInputStream zip = new ZipInputStream(new FileInputStream(jarfile)))
		{
			for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
			    if(!entry.isDirectory() && entry.getName().endsWith(".class")) {
			        String fileEntry = entry.getName().replace('/', '.'); // including ".class"
			        String className = fileEntry.substring(0, fileEntry.length() - ".class".length());
			        analyse(loadClass(className));
			    }
			}
			LOG.info("Loaded {} successfully, {} in error", loadedSuccess, loadedFailed);
		}
	}

	private void analyse(CtClass cclass) {
//		if(cclass != null) {
//			if(GuidoTransformer.isFrozenOrClassLoader(cclass)) {
//				return;
//			}
//			if(GuidoTransformer.isObservableByName(cclass)) {
//				for(CtMethod method : cclass.getDeclaredMethods()) {
//					if(!method.isEmpty()) {
//						if(GuidoTransformer.isElligeable(cclass, method)) {
//							PatternMethodConfig config = classConfigurer.configFor(method);
//						}
//					}
//				}
//			}
//		}
	}

	private CtClass loadClass(String className) {
		try {
			CtClass cclass = pool.get(className);
        	loadedSuccess++;
        	return cclass;
		} catch(Exception e) {
        	loadedFailed++;
    		return null;
		}
	}

	private void checkArgs(String[] args) {
		for(int index = 0; index < args.length; index++) {
			if(args[index].startsWith("--conf=")) {
				conf = args[index].substring("--conf=".length());
			}
			if(args[index].startsWith("--jarfile=")) {
				jarfile = args[index].substring("--jarfile=".length());
			}
			if(args[index].startsWith("--hostname=")) {
				hostname = args[index].substring("--hostname=".length());
			}
		}
		if(hostname == null || conf == null || jarfile == null) {
			LOG.info("Usage : GuidoCheck --hostname=hostname --conf=guido-conf-file --jarfile=jar-file");
			System.exit(1);
		}
		LOG.info("Running with hostname {}, conf {}, jar {}", hostname, conf, jarfile);
	}
}
