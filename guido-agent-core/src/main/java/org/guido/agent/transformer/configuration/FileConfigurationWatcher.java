package org.guido.agent.transformer.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.guido.agent.transformer.logger.GuidoLogger;

public class FileConfigurationWatcher implements ConfigurationWatcher {
	
	private GuidoLogger guidoLOG = GuidoLogger.getLogger("FileConfiguationWatcher");

	private String filePath;
	private int pollTime;
	private long lastModified;
	private ConfigurationNotify notify;
	
	public FileConfigurationWatcher(String filePath, int pollTime) {
		this.pollTime = pollTime;
		this.filePath = filePath;
	}

	@Override
	public void configurationPath(String filePath) {
		this.filePath = filePath;
	}

	@Override
	public void configurationPolling(int second) {
		this.pollTime = second;
	}

	@Override
	public void configurationNotify(ConfigurationNotify notify) {
		this.notify = notify;
	}
	
	void loadClassConfigFromFile(String fileName) {
		File configFile = new File(fileName);
		try(BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
			notify.onLoaded(reader);
			lastModified = configFile.lastModified();
		} catch(Exception e) {
			guidoLOG.error(e, "Error loading method configuration file [{}]", fileName);
			notify.onError();
		}
	}

	@Override
	public void start() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				guidoLOG.debug("Starting file poller on {} every {} s", filePath, pollTime);
				for(;;) {
					try {
						Thread.sleep(pollTime * 1000);
						long newLastModified = new File(filePath).lastModified();
						if(newLastModified == 0) { // file does not exists
							if(lastModified != 0) { // file was moved recently
								guidoLOG.debug("File {} not found", filePath, lastModified, newLastModified);
								lastModified = 0;
								notify.onError();
							}
						} else if(newLastModified > lastModified) {
							guidoLOG.debug("Reloading file {}", filePath);
							loadClassConfigFromFile(filePath);
						}
					} catch(InterruptedException ie) {
						return;
					} catch(Exception e) {
						guidoLOG.error(e, "error in the autoreaload thread, file is {}", filePath);
						continue;
					}
				}
			}
		}).start();
	}
}
