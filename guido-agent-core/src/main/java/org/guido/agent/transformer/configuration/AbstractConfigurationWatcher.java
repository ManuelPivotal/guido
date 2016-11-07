package org.guido.agent.transformer.configuration;

import java.util.concurrent.ExecutorService;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.util.ThreadExecutorUtils;

public abstract class AbstractConfigurationWatcher implements ConfigurationWatcher, Runnable {
	
	GuidoLogger guidoLOG = GuidoLogger.getLogger(getClass().getSimpleName());
	
	protected String configurationPath;
	protected int secondsBetweenPolls;
	protected ConfigurationNotify notify;
	protected boolean needStop = false;
	private boolean alreadyOnError = false;
	
	public AbstractConfigurationWatcher(String configurationPath, int secondsBetweenPolls) {
		configurationPath(configurationPath);
		configurationPolling(secondsBetweenPolls);
	}

	@Override
	public void configurationPath(String configurationPath) {
		this.configurationPath = configurationPath;
	}

	@Override
	public void configurationPolling(int secondsBetweenPolls) {
		this.secondsBetweenPolls = secondsBetweenPolls;
	}

	@Override
	public void configurationNotify(ConfigurationNotify notify) {
		this.notify = notify;
	}
	
	protected abstract void doStart();
	protected abstract void doWatch();
	protected void doTerminate() {}
	
	ExecutorService watchService = ThreadExecutorUtils.newSingleThreadExecutor();

	@Override
	public void start() {
		if(secondsBetweenPolls == NO_POLL) { // no polling - run it once and no watcher.
			doStart();
		} else {
			watchService.submit(this);
		}
	}
	
	public void stop() {
		needStop = true;
	}
	
	@Override
	public void run() {
		guidoLOG.debug("Starting file poller on {} every {} s", configurationPath, secondsBetweenPolls);
		for(;;) {
			try {
				doWatch();
				if(Thread.interrupted()) {
					guidoLOG.info("is interrupted is true - exiting");
					return;
				}
				if(needStop) {
					return;
				}
				Thread.sleep(secondsBetweenPolls * 1000);
				if(needStop) {
					return;
				}
			} catch(InterruptedException ie) {
				guidoLOG.info("received InterruptedException - exiting");
				return;
			} catch(Exception e) {
				guidoLOG.error(e, "error in the watch thread, path is {}", configurationPath);
				continue;
			}
		}
	}

	protected void notifyError() {
		if(!alreadyOnError) {
			notify.onError();
			alreadyOnError = true;
		}
	}

	protected void resetError() {
		alreadyOnError = false;
	}
}
