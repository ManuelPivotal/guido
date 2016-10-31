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

public class GithubConfigurationWatcher extends AbstractConfigurationWatcher {

	static GuidoLogger LOG = GuidoLogger.getLogger("GithubConfigurationWatcher");
	URLAuth urlAuth;

	private ObjectMapper mapper;
	
	private GithubMessage currentConfiguration;
	@JsonDeserialize(using = ContentDeserialize.class)
	public static class GithubMessage {
		String content;
		String sha;
		
		@Override
		public boolean equals(Object other) {
			if(other == null || !(other instanceof GithubMessage)) {
				return false;
			}
			return sha.equals(((GithubMessage)other).sha);
		}

		public String configurationContent() {
			return new String(Base64.getDecoder().decode(content.getBytes()));
		}
	}
	
	@SuppressWarnings("serial")
	public static class ContentDeserialize extends StdDeserializer<GithubMessage> {
		
		public ContentDeserialize() {
			this(null);
		}
		
		protected ContentDeserialize(Class<?> vc) {
			super(vc);
		}

		@Override
		public GithubMessage deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
			JsonNode node = jp.getCodec().readTree(jp);
			String content = node.get("content").asText().replaceAll("\\n",  "");
			GithubMessage message = new GithubMessage();
			message.content = content;
			message.sha = node.get("sha").asText();
			return message;
		}
	}
	
	public GithubConfigurationWatcher(String configurationPath, int secondsBetweenPolls) {
		super(configurationPath, secondsBetweenPolls);
		try {
			urlAuth = URLAuth.createFrom(configurationPath);
		} catch(MalformedURLException mfe) {
			LOG.error(mfe, "Invalid URL {}", configurationPath);
			throw new RuntimeException(mfe);
		}
		currentConfiguration = new GithubMessage();
		currentConfiguration.sha = "";
		
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		SimpleModule module = new SimpleModule();
		module.addDeserializer(GithubMessage.class, new ContentDeserialize());
		mapper.registerModule(module);
	}
	
	@Override
	protected void doStart() {
		loadConfigurationFromGithub();
	}

	private void loadConfigurationFromGithub() {
		try {
			HttpURLConnection connection = urlAuth.openConnection();
			try(BufferedReader gitFileReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
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
				while((line = gitFileReader.readLine()) != null) {
					sb.append(line).append("\n");
				}
				GithubMessage configuration = turnToJson(sb.toString());
				urlAuth.lastModified();
				boolean confDiffer = !configuration.equals(currentConfiguration);
				LOG.debug("github/current configuration [{}/{}] - are equal is {}", 
											currentConfiguration.sha,
											configuration.sha,
											!confDiffer);
				if(confDiffer) {
					currentConfiguration = configuration;
					notify.onLoaded(new BufferedReader(new StringReader(currentConfiguration.configurationContent())));
					resetError();
				}
			}
		} catch(Throwable e) {
			LOG.error(e, "Error while getting github {}", configurationPath);
			notifyError();
		}
	}
	
	private GithubMessage turnToJson(String configuration) throws Exception {
		return mapper.readValue(configuration, GithubMessage.class);
	}

	@Override
	protected void doWatch() {
		loadConfigurationFromGithub();
	}
}
