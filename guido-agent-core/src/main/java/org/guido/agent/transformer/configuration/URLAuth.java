package org.guido.agent.transformer.configuration;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import org.guido.agent.transformer.logger.GuidoLogger;

class URLAuth {
	private static GuidoLogger LOG = GuidoLogger.getLogger("URLAuth");
	
	String base64Credentials;
	String url;
	String lastModified = null;
	HttpURLConnection connection;
	
	private URLAuth() {}
	
	static public URLAuth createFrom(String httpUrl) throws MalformedURLException {
		URL url = new URL(httpUrl);
		String userInfo = url.getUserInfo();
		URLAuth urlAuth = new URLAuth();
		if(userInfo != null) {
			LOG.debug("user info:{}", url.getUserInfo());
			urlAuth.base64Credentials = new String(Base64.getEncoder().encode(userInfo.getBytes()));
			urlAuth.url = httpUrl.replace(userInfo + "@", "");
		} else {
			urlAuth.url = httpUrl;
		}
		LOG.debug("url is {}", urlAuth.url);
		return urlAuth;
	}
	
	public HttpURLConnection openConnection() throws Exception {
		connection = (HttpURLConnection) (new URL(url).openConnection());
		connection.setRequestMethod("GET");
		addAuth(connection);
		addIfNotModified(connection);
		connection.setUseCaches(false);
		return connection;
	}
	
	private void addIfNotModified(HttpURLConnection connection) {
		if(lastModified != null) {
			LOG.debug("Adding If-Modified-Since: {}", lastModified);
			connection.setRequestProperty("If-Modified-Since", lastModified);
		}
	}

	public void addAuth(HttpURLConnection connection) {
		if(base64Credentials != null) {
			connection.setRequestProperty("Authorization", "Basic " + base64Credentials);
		}
	}

	public void lastModified() {
		lastModified = connection.getHeaderField("Last-Modified");
	}
}