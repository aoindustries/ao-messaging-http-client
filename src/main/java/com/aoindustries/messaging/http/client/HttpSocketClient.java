/*
 * ao-messaging-http-client - Client for asynchronous bidirectional messaging over HTTP.
 * Copyright (C) 2014, 2015, 2016, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-messaging-http-client.
 *
 * ao-messaging-http-client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-messaging-http-client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-messaging-http-client.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.messaging.http.client;

import com.aoindustries.concurrent.Callback;
import com.aoindustries.concurrent.Executors;
import com.aoindustries.io.AoByteArrayOutputStream;
import com.aoindustries.messaging.http.HttpSocket;
import com.aoindustries.messaging.http.HttpSocketContext;
import com.aoindustries.security.Identifier;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Element;

/**
 * Client component for bi-directional messaging over HTTP.
 */
public class HttpSocketClient extends HttpSocketContext {

	private static final boolean DEBUG = false;

	private static final int CONNECT_TIMEOUT = 15 * 1000;

	private final Executors executors = new Executors();

	public HttpSocketClient() {
	}

	@Override
	public void close() {
		try {
			super.close();
		} finally {
			executors.dispose();
		}
	}

	/**
	 * Asynchronously connects.
	 */
	public void connect(
		final String endpoint,
		final Callback<? super HttpSocket> onConnect,
		final Callback<? super Exception> onError
	) {
		executors.getUnbounded().submit(() -> {
			try {
				// Build request bytes
				AoByteArrayOutputStream bout = new AoByteArrayOutputStream();
				try {
					try (DataOutputStream out = new DataOutputStream(bout)) {
						out.writeBytes("action=connect");
					}
				} finally {
					bout.close();
				}
				long connectTime = System.currentTimeMillis();
				URL endpointURL = new URL(endpoint);
				HttpURLConnection conn = (HttpURLConnection)endpointURL.openConnection();
				conn.setAllowUserInteraction(false);
				conn.setConnectTimeout(CONNECT_TIMEOUT);
				conn.setDoOutput(true);
				conn.setFixedLengthStreamingMode(bout.size());
				conn.setInstanceFollowRedirects(false);
				conn.setReadTimeout(CONNECT_TIMEOUT);
				conn.setRequestMethod("POST");
				conn.setUseCaches(false);
				// Write request
				OutputStream out = conn.getOutputStream();
				try {
					out.write(bout.getInternalByteArray(), 0, bout.size());
					out.flush();
				} finally {
					out.close();
				}
				// Get response
				int responseCode = conn.getResponseCode();
				if(responseCode != 200) throw new IOException("Unexpect response code: " + responseCode);
				if(DEBUG) System.out.println("DEBUG: HttpSocketClient: connect: got connection");
				DocumentBuilder builder = builderFactory.newDocumentBuilder();
				Element document = builder.parse(conn.getInputStream()).getDocumentElement();
				if(!"connection".equals(document.getNodeName())) throw new IOException("Unexpected root node name: " + document.getNodeName());
				Identifier id = Identifier.valueOf(document.getAttribute("id"));
				if(DEBUG) System.out.println("DEBUG: HttpSocketClient: connect: got id=" + id);
				HttpSocket httpSocket = new HttpSocket(
					HttpSocketClient.this,
					id,
					connectTime,
					endpointURL
				);
				if(DEBUG) System.out.println("DEBUG: HttpSocketClient: connect: adding socket");
				addSocket(httpSocket);
				if(onConnect!=null) {
					if(DEBUG) System.out.println("DEBUG: HttpSocketClient: connect: calling onConnect");
					onConnect.call(httpSocket);
				}
			} catch(Exception exc) {
				if(onError!=null) {
					if(DEBUG) System.out.println("DEBUG: HttpSocketClient: connect: calling onError");
					onError.call(exc);
				}
			}
		});
	}
}
