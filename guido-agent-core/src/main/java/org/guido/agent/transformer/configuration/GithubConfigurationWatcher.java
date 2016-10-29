package org.guido.agent.transformer.configuration;

import static java.net.HttpURLConnection.HTTP_OK;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import org.guido.agent.transformer.logger.GuidoLogger;

public class GithubConfigurationWatcher extends AbstractConfigurationWatcher {

	static GuidoLogger LOG = GuidoLogger.getLogger("GithubConfigurationWatcher");
	
	private String currentConfiguration = "";
	private boolean alreadyOnError = false;
	
	public GithubConfigurationWatcher(String configurationPath, int secondsBetweenPolls) {
		super(configurationPath, secondsBetweenPolls);
		try {
		urlAuth = URLAuth.createFrom(configurationPath);
		} catch(MalformedURLException mfe) {
			LOG.error(mfe,  "Invalid URL {}", configurationPath);
			throw new RuntimeException(mfe);
		}
	}
	
	URLAuth urlAuth;

	@Override
	protected void doStart() {
		loadConfigurationFromGithub();
	}

	private void loadConfigurationFromGithub() {
		try {
			BufferedReader gitFileReader = null;
			HttpURLConnection connection = urlAuth.openConnection();
			gitFileReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			if(connection.getResponseCode() != HTTP_OK) {
				LOG.debug("github response code is {}", connection.getResponseCode());
				notifyError();
			}
			LOG.debug("File can be read...");
			StringBuffer sb = new StringBuffer();
			String line;
			while((line = gitFileReader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			String configuration = sb.toString();
			if(!configuration.equals(currentConfiguration)) {
				currentConfiguration = configuration;
				notify.onLoaded(new BufferedReader(new StringReader(currentConfiguration)));
				resetError();
			}
		} catch(Throwable e) {
			LOG.error(e, "Error while getting github {}", configurationPath);
			notifyError();
		}
	}

	private void notifyError() {
		if(!alreadyOnError) {
			notify.onError();
			alreadyOnError = true;
		}
	}

	private void resetError() {
		alreadyOnError = false;
	}

	@Override
	protected void doWatch() {
	}
}
