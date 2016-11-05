package org.guido.agent.transformer.configuration;

import org.guido.agent.transformer.logger.GuidoLogger;

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

	@Override
	public void start() {
		//doStart();
		new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					for(;;) {
						Thread.sleep(Long.MAX_VALUE);
					}
				} catch(InterruptedException ie) {
					doTerminate();
				}
			}
		}).start();
		new Thread(this).start();
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
				if(needStop) {
					return;
				}
				Thread.sleep(secondsBetweenPolls * 1000);
				if(needStop) {
					return;
				}
			} catch(InterruptedException ie) {
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
