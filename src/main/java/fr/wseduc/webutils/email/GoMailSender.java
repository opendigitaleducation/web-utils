/*
 * Copyright © WebServices pour l'Éducation, 2017
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

package fr.wseduc.webutils.email;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.wseduc.webutils.DefaultAsyncResult;
import fr.wseduc.webutils.eventbus.ResultMessage;
import fr.wseduc.webutils.exception.AsyncResultException;
import fr.wseduc.webutils.exception.InvalidConfigurationException;
import fr.wseduc.webutils.Either;

import static fr.wseduc.webutils.DefaultAsyncResult.handleAsyncError;
import static fr.wseduc.webutils.DefaultAsyncResult.handleAsyncResult;
import static fr.wseduc.webutils.Utils.isNotEmpty;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GoMailSender extends NotificationHelper implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(GoMailSender.class);
	private final HttpClient httpClient;
	private final String platform;
	private final String basicAuthHeader;
	private final ObjectMapper mapper;
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	public GoMailSender(Vertx vertx, JsonObject config)
			throws InvalidConfigurationException, URISyntaxException {
		super(vertx, config);

		if (config != null && isNotEmpty(config.getString("uri")) && isNotEmpty(config.getString("user"))
				&& isNotEmpty(config.getString("password")) && isNotEmpty(config.getString("platform"))) {
			ByteArrayOutputStream userAndPassword = new ByteArrayOutputStream();
			try {
				userAndPassword.write(config.getString("user").getBytes());
				userAndPassword.write(':');
				userAndPassword.write(config.getString("password").getBytes());
			} catch (IOException e) {
				log.error(e);
			}
			basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(userAndPassword.toByteArray());
			final URI uri = new URI(config.getString("uri"));
			final HttpClientOptions options = new HttpClientOptions()
					.setDefaultHost(uri.getHost())
					.setDefaultPort(uri.getPort())
					.setSsl("https".equals(uri.getScheme()))
					.setMaxPoolSize(16)
					.setKeepAlive(false);
			httpClient = vertx.createHttpClient(options);
			platform = config.getString("platform");
			mapper = new ObjectMapper();
			mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"));
			mapper.addMixInAnnotations(Bounce.class, GoMailBounceMixIn.class);

		} else {
			throw new InvalidConfigurationException("missing.parameters");
		}
	}

	@Override
	protected void sendEmail(JsonObject json, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		if (json == null || json.getJsonArray("to") == null || json.getString("from") == null
				|| json.getString("subject") == null || json.getString("body") == null) {
			log.error(json);
			handler.handle(new DefaultAsyncResult<>(new AsyncResultException("invalid.parameters")));
			return;
		}
		if (json.getJsonArray("to").size() > 1) {
			final AtomicInteger count = new AtomicInteger(json.getJsonArray("to").size());
			final AtomicBoolean success = new AtomicBoolean(true);
			final JsonArray errors = new JsonArray();
			final Handler<AsyncResult<Message<JsonObject>>>	h = ar -> {
				if (ar.failed()) {
					success.set(false);
					errors.add(ar.cause().getMessage());
				}
				if (count.decrementAndGet() == 0) {
					if (success.get()) {
						handleAsyncResult(new ResultMessage(), handler);
					} else {
						handleAsyncError(errors.encode(), handler);
					}
				}
			};
			for (Object to : json.getJsonArray("to")) {
				send(json.copy().put("to", new JsonArray().add(to.toString())), h);
			}
		} else {
			send(json, handler);
		}
	}

	private void send(JsonObject json, final Handler<AsyncResult<Message<JsonObject>>> handler) {
		HttpClientRequest req = httpClient.post("/smtp/" + this.platform, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				if (resp.statusCode() == 200) {
					handler.handle(new DefaultAsyncResult<>(new ResultMessage()));
				} else {
					handler.handle(new DefaultAsyncResult<>(new AsyncResultException("GoMail HTTP status: " + resp.statusMessage())));
				}
			}
		});
		req.putHeader("authorization", basicAuthHeader);
		JsonObject to = new JsonObject();
		JsonObject from = new JsonObject();
		JsonObject subject = new JsonObject();
		JsonObject headers = new JsonObject();

		to.put("address", json.getJsonArray("to").getString(0));

		from.put("address", json.getString("from"));
		subject.put("Subject", json.getString("subject"));

		JsonObject payload = new JsonObject().put("from", from)
				.put("to", to)
				.put("subject", json.getString("subject"))
				.put("body", json.getString("body"));

		/* TODO: Uncomment when GoMail supports multiple recipients
		if (json.getJsonArray("cc") != null && json.getJsonArray("cc").size() > 0) {
			JsonArray cc = new JsonArray();
			for (Object o : json.getJsonArray("cc")) {
				JsonObject ccRecipient = new JsonObject();
				ccRecipient.put("address", o.toString());
				cc.add(ccRecipient);
			}
			payload.put("cc", cc);
		}
		if (json.getJsonArray("bcc") != null && json.getJsonArray("bcc").size() > 0) {
			JsonArray bcc = new JsonArray();
			for (Object o : json.getJsonArray("bcc")) {
				JsonObject bccRecipient = new JsonObject();
				bccRecipient.put("address", o.toString());
				bcc.add(bccRecipient);
			}
			payload.put("bcc", bcc);
		}
		*/

		if (json.getJsonArray("headers") != null) {
			for (Object o : json.getJsonArray("headers")) {
				if (!(o instanceof JsonObject))
					continue;
				JsonObject h = (JsonObject) o;
				headers.put(h.getString("name"), h.getString("value"));
			}
		}
		if (headers.size() > 0) {
			payload.put("headers", headers);
		}
		req.end(payload.encode());
	}

	@Override
	public void hardBounces(Date date, Handler<Either<String, List<Bounce>>> handler) {
		hardBounces(date, null, handler);
	}

	@Override
	public void hardBounces(Date startDate, Date endDate, final Handler<Either<String, List<Bounce>>> handler) {
		if (startDate == null) {
			handler.handle(new Either.Left<String, List<Bounce>>("invalid.date"));
			return;
		}
		final DateFormat df = new SimpleDateFormat(DATE_FORMAT);
		final String start = df.format(startDate);
		HttpClientRequest req = httpClient.get("/bounce/hard/since/" + start, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				if (resp.statusCode() == 200) {
					resp.bodyHandler(new Handler<Buffer>() {
						@Override
						public void handle(Buffer buffer) {
							try {
								JsonObject res = new JsonObject(buffer.toString());

								JsonArray l = res.getJsonArray("hard_bounces");
								if (l == null || l.size() == 0) {
									handler.handle(
											new Either.Right<String, List<Bounce>>(Collections.<Bounce>emptyList()));
									return;
								}
								List<Bounce> bounces = mapper.readValue(l.encode(), new TypeReference<List<Bounce>>() {
								});
								handler.handle(new Either.Right<String, List<Bounce>>(bounces));

							} catch (RuntimeException | IOException e) {
								handler.handle(new Either.Left<String, List<Bounce>>(e.getMessage()));
								log.error(e.getMessage(), e);
							}
						}
					});
				} else {
					handler.handle(new Either.Left<String, List<Bounce>>("GoMail HTTP status: " + resp.statusMessage()));
				}
			}
		});
		req.putHeader("authorization", basicAuthHeader);
		req.end();
	}
}
