/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.wseduc.webutils.http;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;

public class HttpClientUtils {

	public static void sendFile(Vertx vertx, String uri, int port, String content,
			MultiMap headers, String filename,
			String contentType, Handler<HttpClientResponse> handler) {
		HttpClientOptions options = new HttpClientOptions().setDefaultPort(port);
		HttpClientRequest req = vertx.createHttpClient(options).post(uri, handler);

		final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
		Buffer buffer = Buffer.buffer();
		final String body = "--" + boundary + "\r\n" +
				"Content-Disposition: form-data; name=\"file\"; filename=\""+ filename +"\"\r\n" +
				"Content-Type: " + contentType + "\r\n" +
				"\r\n" +
				content + "\r\n" +
				"--" + boundary + "--\r\n";

		buffer.appendString(body);
		req.headers().addAll(headers);
		req.headers().set("content-length", String.valueOf(buffer.length()));
		req.headers().set("content-type", "multipart/form-data; boundary=" + boundary);
		req.write(buffer).end();
	}

	public static void proxy(final HttpServerRequest req, HttpClient client) {
		proxy(req, client, null, null, null);
	}

	public static void proxy(final HttpServerRequest req, HttpClient client, String prefix) {
		proxy(req, client, prefix, null, null);
	}

	public static void proxy(final HttpServerRequest req, HttpClient client,
			String prefix, String replacement) {
		proxy(req, client, prefix, replacement, null);
	}

	public static void proxy(final HttpServerRequest req, HttpClient client,
			String prefix, String replacement, final JsonObject defaultResult) {
		String uri = req.uri();
		if (prefix != null && !prefix.trim().isEmpty()) {
			if (replacement != null && !replacement.trim().isEmpty()) {
				uri = uri.replaceFirst(prefix, replacement);
			} else {
				uri = uri.replaceFirst(req.path(), prefix + req.path());
			}
		}
		final HttpClientRequest cReq = client.request(req.method(), uri,
				new Handler<HttpClientResponse>() {
			public void handle(HttpClientResponse cRes) {
				if (defaultResult != null && defaultResult.getString("content") != null &&
						(cRes.statusCode() < 200 || (cRes.statusCode() >= 300 &&
						cRes.statusCode() != 304))) {
					if (defaultResult.getJsonObject("headers") != null) {
						for (String header: defaultResult.getJsonObject("headers").fieldNames()) {
							req.response().headers().add(header,
									defaultResult.getJsonObject("headers").getString(header));
						}
					}
					if ("file".equals(defaultResult.getString("type"))) {
						req.response().sendFile(defaultResult.getString("content"));
					} else {
						req.response().end(defaultResult.getString("content"));
					}
				} else {
					req.response().setStatusCode(cRes.statusCode());
					req.response().headers().setAll(cRes.headers());
					req.response().setChunked(true);
					cRes.handler(data -> req.response().write(data));
					cRes.endHandler(new Handler<Void>() {
						@Override
						public void handle(Void event) {
							req.response().end();
						}
					});
				}
			}
		});
		cReq.headers().setAll(req.headers());
		cReq.putHeader("Host", req.host());
		cReq.setChunked(true);
		req.handler(new Handler<Buffer>() {
			public void handle(Buffer data) {
				cReq.write(data);
			}
		});
		req.endHandler(new Handler<Void>() {
			public void handle(Void v) {
				cReq.end();
			}
		});
	}

}
