package org.guido.agent.transformer.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.guido.agent.transformer.logger.GuidoLogger;

public class FileConfigurationWatcher extends AbstractConfigurationWatcher {
	
	private GuidoLogger guidoLOG = GuidoLogger.getLogger("FileConfiguationWatcher");

	private long lastModified;
	
	public FileConfigurationWatcher(String filePath, int pollTime) {
		super(filePath, pollTime);
	}
	
	protected void loadClassConfigurationFromFile() {
		File configFile = new File(configurationPath);
		try(BufferedReader reader = new BufferedReader(new FileReader(configurationPath))) {
			notify.onLoaded(reader);
			lastModified = configFile.lastModified();
		} catch(Exception e) {
			guidoLOG.error(e, "Error loading method configuration file [{}]", configurationPath);
			notify.onError();
		}
	}

	@Override
	protected void doStart() {
		loadClassConfigurationFromFile();
	}
	
	@Override
	protected void doWatch() {
		long newLastModified = new File(configurationPath).lastModified();
		if(newLastModified == 0) { // file does not exists
			if(lastModified != 0) { // file was moved recently
				guidoLOG.debug("File {} not found", configurationPath, lastModified, newLastModified);
				lastModified = 0;
				notify.onError();
			}
		} else if(newLastModified > lastModified) {
			guidoLOG.debug("Reloading file {}", configurationPath);
			loadClassConfigurationFromFile();
		}
	}
}
