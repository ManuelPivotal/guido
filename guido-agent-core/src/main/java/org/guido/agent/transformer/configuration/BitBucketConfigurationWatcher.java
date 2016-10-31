package org.guido.agent.transformer.configuration;

import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.Base64;

import org.guido.agent.transformer.configuration.GithubConfigurationWatcher.ContentDeserialize;
import org.guido.agent.transformer.configuration.GithubConfigurationWatcher.GithubMessage;
import org.guido.agent.transformer.logger.GuidoLogger;

import oss.guido.com.fasterxml.jackson.core.JsonParser;
import oss.guido.com.fasterxml.jackson.core.JsonProcessingException;
import oss.guido.com.fasterxml.jackson.databind.DeserializationContext;
import oss.guido.com.fasterxml.jackson.databind.DeserializationFeature;
import oss.guido.com.fasterxml.jackson.databind.JsonNode;
import oss.guido.com.fasterxml.jackson.databind.ObjectMapper;
import oss.guido.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import oss.guido.com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import oss.guido.com.fasterxml.jackson.databind.module.SimpleModule;

public class BitBucketConfigurationWatcher extends AbstractConfigurationWatcher {
	
	static GuidoLogger LOG = GuidoLogger.getLogger(BitBucketConfigurationWatcher.class);

	private BitbucketMessage currentConfiguration;
	@JsonDeserialize(using = ContentDeserialize.class)
	public static class BitbucketMessage {
		String data;
		String node;
		
		@Override
		public boolean equals(Object other) {
			if(other == null || !(other instanceof BitbucketMessage)) {
				return false;
			}
			return node.equals(((BitbucketMessage)other).node);
		}

		public String configurationContent() {
			return data;
		}
	}
	
	@SuppressWarnings("serial")
	public static class ContentDeserialize extends StdDeserializer<BitbucketMessage> {
		
		public ContentDeserialize() {
			this(null);
		}
		
		protected ContentDeserialize(Class<?> vc) {
			super(vc);
		}

		@Override
		public BitbucketMessage deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
			JsonNode node = jp.getCodec().readTree(jp);
			String data = node.get("data").asText();
			BitbucketMessage message = new BitbucketMessage();
			message.data = data;
			message.node = node.get("node").asText();
			return message;
		}
	}

	private ObjectMapper mapper;
	URLAuth urlAuth;

	public BitBucketConfigurationWatcher(String configurationPath, int secondsBetweenPolls) {
		super(configurationPath, secondsBetweenPolls);
		if(configurationPath != null) {
			try {
				urlAuth = URLAuth.createFrom(configurationPath);
			} catch(MalformedURLException mfe) {
				LOG.error(mfe, "Invalid URL {}", configurationPath);
				throw new RuntimeException(mfe);
			}
		}
		initWatcher();
	}
	
	private void initWatcher() {
		currentConfiguration = new BitbucketMessage();
		currentConfiguration.node = "";
		
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		SimpleModule module = new SimpleModule();
		module.addDeserializer(BitbucketMessage.class, new ContentDeserialize());
		mapper.registerModule(module);
	}

	@Override
	protected void doStart() {
		loadConfigurationFromBitBucket();
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
					LOG.debug("github response code is {}", connection.getResponseCode());
					notifyError();
				}
				StringBuffer sb = new StringBuffer();
				String line;
				while((line = connectionReader.readLine()) != null) {
					sb.append(line).append("\n");
				}
				BitbucketMessage configuration = turnToJson(sb.toString());
				urlAuth.lastETag();
				currentConfiguration = configuration;
				notify.onLoaded(new BufferedReader(new StringReader(currentConfiguration.configurationContent())));
				resetError();
			}
		} catch(Throwable e) {
			LOG.error(e, "Error while getting github {}", configurationPath);
			notifyError();
		}
	}
	
	BitbucketMessage turnToJson(String configuration) throws Exception {
		return mapper.readValue(configuration, BitbucketMessage.class);
	}

	@Override
	protected void doWatch() {
		loadConfigurationFromBitBucket();
	}
}
