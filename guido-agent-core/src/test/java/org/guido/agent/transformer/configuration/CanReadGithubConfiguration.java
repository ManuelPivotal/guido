package org.guido.agent.transformer.configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.guido.agent.transformer.logger.GuidoLogger;
import org.junit.Test;

public class CanReadGithubConfiguration {
	static GuidoLogger LOG = GuidoLogger.getLogger("test");
	
	static String url = "https://raw.githubusercontent.com/vfabric-pso-emea/pcf-broker-dashboard-skeleton/master/auth2"; 
	
	@Test
	public void canReadNoAuthGithubConfiguration() throws Exception {
		URL url = new URL("https://raw.githubusercontent.com/ManuelPivotal/guido/master/build.gradle");
		BufferedReader gitFileReader = null;
		try {
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setUseCaches(false);
			gitFileReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			if(connection.getResponseCode() != 200) {
				LOG.error("response code is {}", connection.getResponseCode());
				return;
			}
		} catch(Exception e) {
			LOG.error(e, "exception while getting file {}", CanReadGithubConfiguration.url);
		}
		LOG.info("File can be read...");
		String line;
		while((line = gitFileReader.readLine()) != null) {
			LOG.info("{}", line);
		}
	}
	
	@Test
	public void canReadWithBasicAuthGithubConfiguration() throws Exception {
		String httpUrl = "https://ManuelPivotal:Fauchelevent001@raw.githubusercontent.com/vfabric-pso-emea/pcf-broker-dashboard-skeleton/master/auth2";
		//String httpUrl = "https://raw.githubusercontent.com/vfabric-pso-emea/pcf-broker-dashboard-skeleton/master/auth2";
		BufferedReader gitFileReader = null;
		HttpURLConnection connection = null;
		try {
			URLAuth urlAuth = URLAuth.createFrom(httpUrl);
			URL url = new URL(urlAuth.url);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			urlAuth.addAuth(connection);
			connection.setUseCaches(false);
			gitFileReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			if(connection.getResponseCode() != 200) {
				LOG.error("response code is {}", connection.getResponseCode());
				throw new RuntimeException("code is " + connection.getResponseCode());
			}
		} catch(Throwable e) {
			LOG.error(e, "exception {} while getting file {}", 
						safeResponseCode(connection), 
						httpUrl);
			throw e;
		}
		LOG.info("File can be read...");
		String line;
		while((line = gitFileReader.readLine()) != null) {
			LOG.info("{}", line);
		}
	}

	private int safeResponseCode(HttpURLConnection connection) {
		try {
			return connection.getResponseCode();
		} catch(Throwable t) {
			return -1;
		}
	}

}
