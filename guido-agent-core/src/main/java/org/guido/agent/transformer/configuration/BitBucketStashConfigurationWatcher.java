package org.guido.agent.transformer.configuration;

import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;

import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.com.fasterxml.jackson.databind.DeserializationFeature;
import oss.guido.com.fasterxml.jackson.databind.ObjectMapper;

public class BitBucketStashConfigurationWatcher extends AbstractConfigurationWatcher {
	
	static GuidoLogger LOG = GuidoLogger.getLogger(BitBucketStashConfigurationWatcher.class);

	private BitBucketStashMessage currentConfiguration;

	public static class TextLine {
		String text;
		public String getText() {
			return text;
		}
		public void setText(String text) {
			this.text = text;
		}
	}
	
	public static class BitBucketStashMessage {
		private String content;
		List<TextLine> lines;
		public List<TextLine> getLines() {
			return lines;
		}
		public void setLines(List<TextLine> lines) {
			this.lines = lines;
		}
		
		public String content() {
			if(content == null) {
				content = buildContent();
			}
			return content;
		}
		
		private String buildContent() {
			if(lines == null || lines.size() == 0) {
				return "";
			}
			StringBuffer sb = new StringBuffer();
			for(TextLine line : lines) {
				sb.append(line.getText()).append("\n");
			}
			return sb.toString();
		}
	}
	
	private ObjectMapper mapper;
	URLAuth urlAuth;
	
	public BitBucketStashConfigurationWatcher() {
		this(null, 0);
	}
	
	public BitBucketStashConfigurationWatcher(String configurationPath, int secondsBetweenPolls) {
		super(configurationPath, secondsBetweenPolls);
		if(configurationPath != null) {
			try {
				urlAuth = URLAuth.createFrom(configurationPath);
			} catch(MalformedURLException mfe) {
				LOG.error(mfe, "Invalid bitbucket URL");
				throw new RuntimeException(mfe);
			}
		}
		currentConfiguration = new BitBucketStashMessage();
		
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	protected void doStart() {
		//loadConfigurationFromBitBucket();
	}

	private void loadConfigurationFromBitBucket() {
		try {
		HttpURLConnection connection = urlAuth.openConnection();
			try(BufferedReader connectionReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				if(connection.getResponseCode() == HTTP_NOT_MODIFIED) {
					LOG.debug("304 - configuration is not modified");
					return;
				}
				if(connection.getResponseCode() != HTTP_OK) {
					LOG.debug("bitbucket response code is {}", connection.getResponseCode());
					notifyError();
				}
				StringBuffer sb = new StringBuffer();
				String line;
				while((line = connectionReader.readLine()) != null) {
					sb.append(line).append("\n");
				}
				BitBucketStashMessage configuration = turnToJson(sb.toString());
				urlAuth.lastETag();
				if(!configuration.content().equals(currentConfiguration.content())) {
					LOG.info("configuration has changed - calling notify callback");
					currentConfiguration = configuration;
					notify.onLoaded(new BufferedReader(new StringReader(currentConfiguration.content())));
					resetError();
				} else {
					LOG.info("configuration has not changed");
				}
			}
		} catch(Throwable e) {
			LOG.info("Config not reacheable using stash {}", 
						(urlAuth != null) ? urlAuth.displayableURL() : "");
			notifyError();
		}
	}
	
	BitBucketStashMessage turnToJson(String configuration) throws Exception {
		return mapper.readValue(configuration, BitBucketStashMessage.class);
	}

	@Override
	protected void doWatch() {
		loadConfigurationFromBitBucket();
	}
}
