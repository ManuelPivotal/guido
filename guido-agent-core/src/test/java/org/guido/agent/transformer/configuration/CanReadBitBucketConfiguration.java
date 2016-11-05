package org.guido.agent.transformer.configuration;

import java.net.URL;

import junit.framework.Assert;

import org.guido.agent.transformer.configuration.BitBucketStashConfigurationWatcher.BitBucketStashMessage;
import org.guido.agent.transformer.logger.GuidoLogger;
import org.guido.agent.utils.TestUtils;
import org.junit.Test;

public class CanReadBitBucketConfiguration {
	static GuidoLogger LOG = GuidoLogger.getLogger("test");
	
	@Test
	public void canReadBitBucketConfiguration() throws Exception {
		 BitBucketConfigurationWatcher watcher = new BitBucketConfigurationWatcher(
				"https://manuelmeyer007:guidoaccount@api.bitbucket.org/1.0/repositories/manuelmeyer007/guidoconf/src/master/guido.conf", 10);
		watcher.configurationNotify(new TestNotify());
		watcher.start();
		Thread.sleep(Long.MAX_VALUE);
	}
	
	@Test 
	public void canDecodeJsonBB() throws Exception {
		String data = TestUtils.loadTestFileData("stash-browse-json.txt");
		BitBucketStashConfigurationWatcher watcher = new BitBucketStashConfigurationWatcher();
		BitBucketStashMessage message = watcher.turnToJson(data);
		String expected = TestUtils.loadTestFileData("expected-result.txt");
		Assert.assertEquals(expected, message.content());
	}
	
	@Test
	public void canExtractUserInfos() throws Exception {
		String httpUrl = "https://manuel@meyer.com:guidoa@ccount@api.bitbucket.org/1.0/repositories/test.conf";
		int lastAt = httpUrl.lastIndexOf('@');
		if(lastAt != -1) {
			int urlStartIndex = httpUrl.indexOf("://");
			String credentials = httpUrl.substring(urlStartIndex + 3, lastAt);
			String nonCredentials = httpUrl.replace(credentials + "@", "");
			LOG.info("Credentials [{}] non credentials [{}]", credentials, nonCredentials);
			httpUrl = nonCredentials;
		}
		URL url = new URL(httpUrl);
		LOG.debug("URL is {}, userInfo is {}", url.toString(), url.getUserInfo());
	}
}
