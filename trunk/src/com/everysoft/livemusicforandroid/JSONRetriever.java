package com.everysoft.livemusicforandroid;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONRetriever {
	private String mUrl;
	
	public JSONRetriever(String url) {
		mUrl = url;
	}
	
	public JSONObject getJSON() throws ParseException, ClientProtocolException, JSONException, IOException {
		DefaultHttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGetRequest = new HttpGet(mUrl);
		
		httpGetRequest.setHeader("Accept", "application/json");
		httpGetRequest.setHeader("Accept-Encoding", "gzip");

		HttpEntity response = httpClient.execute(httpGetRequest).getEntity();
		
		if (response != null) {
			Header contentEncoding = response.getContentEncoding();
			if (contentEncoding != null && contentEncoding.getValue().contains("gzip")) {
				response = new GzipEntityWrapper(response);
			}
			return new JSONObject(EntityUtils.toString(response));
		}
		else {
			throw new IOException("Unable to retrieve content!");
		}
	}
}

final class GzipEntityWrapper extends HttpEntityWrapper {
    public GzipEntityWrapper(final HttpEntity entity) {
        super(entity);
    }

    public InputStream getContent()
        throws IOException, IllegalStateException {
        return new GZIPInputStream(wrappedEntity.getContent());
    }

    public long getContentLength() {
        return -1;
    }
}