package org.guido.agent.transformer.configuration;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

import org.guido.agent.transformer.logger.GuidoLogger;

class URLAuth  {
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 15000;

	private static GuidoLogger LOG = GuidoLogger.getLogger("URLAuth");
	
	String base64Credentials;
	String url;
	
	String lastModified = null;
	String lastETag = null;
	String displayableUrl = "";
	
	HttpURLConnection connection;
	
	private URLAuth() {}
	
	static public URLAuth createFrom(String httpUrl, String userName, String password) throws MalformedURLException {
		LOG.debug("url={}, username={}, password={}",
				httpUrl, userName, (password == null ? "null" : "********"));
		URLAuth urlAuth = new URLAuth();
		urlAuth.displayableUrl = httpUrl;
		if(userName == null || password == null) {
			int lastAt = httpUrl.lastIndexOf('@');
			if(lastAt != -1) {
				int urlStartIndex = httpUrl.indexOf("://");
				String credentials = httpUrl.substring(urlStartIndex + 3, lastAt);
				urlAuth.base64Credentials = new String(Base64.getEncoder().encode(credentials.getBytes()));
				urlAuth.displayableUrl = httpUrl.replace(credentials + "@", hidePassword(credentials) + "@");
				httpUrl = httpUrl.replace(credentials + "@", "");
			}
		} else if(userName != null && password != null) {
			String credentials = userName + ":" + password;
			urlAuth.base64Credentials = new String(Base64.getEncoder().encode(credentials.getBytes()));
		}
		LOG.info("URL is {}", urlAuth.displayableUrl);
		new URL(httpUrl); // will throw an exception if malformed
		urlAuth.url = httpUrl; 
		return urlAuth;
	}
	
	private static String hidePassword(String userInfo) {
		int lastIndex = userInfo.indexOf(":");
		if(lastIndex > 0) {
			return userInfo.substring(0, lastIndex) + ":******";
		}
		return userInfo;
	}

	public HttpURLConnection openConnection() throws Exception {
		connection = (HttpURLConnection) (new URL(url).openConnection());
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		addAuth(connection);
		if(addIfNoneMatch(connection) == false) {
			addIfNotModified(connection);
		}
		connection.setUseCaches(false);
		return connection;
	}
	
	public String displayableURL() {
		return displayableUrl;
	}
	
	private boolean addIfNoneMatch(HttpURLConnection connection) {
		if(lastETag != null) {
			LOG.debug("Adding If-None-Match: {}", lastETag);
			connection.setRequestProperty("If-None-Match", lastETag);
			return true;
		}
		return false;
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

	public void lastETag() {
		lastETag = connection.getHeaderField("ETag");
	}

	public void quietClose() {
		try {
			connection.disconnect();
		} catch(Exception e) {
			
		}
	}
//	@Override
//	public void run() {
//		for(;;) {
//			try {
//				Thread.sleep(Long.MAX_VALUE);
//			} catch (InterruptedException e) {
//				quietClose();
//			}
//		}
//	}
}