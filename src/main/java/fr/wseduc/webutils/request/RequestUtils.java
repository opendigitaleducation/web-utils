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

package fr.wseduc.webutils.request;

import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.XSSUtils;
import fr.wseduc.webutils.validation.JsonSchemaValidator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestUtils {

	private static final Logger log = LoggerFactory.getLogger(RequestUtils.class);
	private static final JsonSchemaValidator validator = JsonSchemaValidator.getInstance();
	private static final Pattern versionPatter = Pattern.compile("version=([0-9]+\\.[0-9]+)");

	public static void bodyToJson(final HttpServerRequest request, final Handler<JsonObject> handler) {
		request.bodyHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer event) {
				try {
					JsonObject json = new JsonObject(XSSUtils.stripXSS(event.toString("UTF-8")));
					handler.handle(json);
				} catch (RuntimeException e) {
					log.warn(e.getMessage(), e);
					Renders.badRequest(request, e.getMessage());
				}
			}
		});
	}

	public static void bodyToJsonArray(final HttpServerRequest request, final Handler<JsonArray> handler) {
		request.bodyHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer event) {
				try {
					String obj = XSSUtils.stripXSS(event.toString("UTF-8"));
					JsonArray json = new JsonArray(obj);
					handler.handle(json);
				} catch (RuntimeException e) {
					log.warn(e.getMessage(), e);
					Renders.badRequest(request, e.getMessage());
				}
			}
		});
	}

	public static void bodyToJson(final HttpServerRequest request, final String schema,
			final Handler<JsonObject> handler) {
		request.bodyHandler(new Handler<Buffer>() {
			@Override
			public void handle(Buffer event) {
				try {
					final JsonObject json = new JsonObject(XSSUtils.stripXSS(event.toString("UTF-8")));
					validator.validate(schema, json, event1 -> {
						if (event1.succeeded()) {
							if ("ok".equals(event1.result().body().getString("status"))) {
								handler.handle(json);
							} else {
								log.debug(event1.result().body().getString("message"));
								log.debug(event1.result().body()
										.getJsonArray("report", new JsonArray()).encodePrettily());
								Renders.badRequest(request, event1.result().body().getString("error"));
							}
						} else {
							log.error("Validate async error.", event1.cause());
							Renders.badRequest(request, event1.cause().getMessage());
						}
					});
				} catch (RuntimeException e) {
					log.warn(e.getMessage(), e);
					Renders.badRequest(request, e.getMessage());
				}
			}
		});
	}

	public static String acceptVersion(HttpServerRequest request) {
		final String accept = request.headers().get("Accept");
		return getAcceptVersion(accept);
	}

	public static String getAcceptVersion(String accept) {
		Matcher m;
		if (accept != null && (m = versionPatter.matcher(accept)).find()) {
			return m.group(1);
		}
		return "";
	}

}
