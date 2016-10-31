package org.guido.agent.transformer.configuration;

import org.guido.agent.transformer.logger.GuidoLogger;

public class CanReadGithubConfiguration {
	static GuidoLogger LOG = GuidoLogger.getLogger("test");
	
	//@Test
	public void canSeeGithubChange() throws Exception {
		AbstractConfigurationWatcher watcher = new GithubConfigurationWatcher("https://api.github.com/repos/ManuelPivotal/guido/contents/guido-conf/guido.conf?ref=develop", 10);
		watcher.configurationNotify(new TestNotify());
		watcher.start();
		Thread.sleep(Long.MAX_VALUE);
	}
}
