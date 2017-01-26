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
import fr.wseduc.webutils.eventbus.ResultMessage;
import fr.wseduc.webutils.exception.InvalidConfigurationException;
import fr.wseduc.webutils.Either;
import static fr.wseduc.webutils.Utils.isNotEmpty;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Container;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

	public GoMailSender(Vertx vertx, Container container, JsonObject config)
			throws InvalidConfigurationException, URISyntaxException {
		super(vertx, container);

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
			basicAuthHeader = "Basic " + Base64.encodeBytes(userAndPassword.toByteArray());
			URI uri = new URI(config.getString("uri"));
			httpClient = vertx.createHttpClient().setHost(uri.getHost()).setPort(uri.getPort()).setMaxPoolSize(16)
					.setSSL("https".equals(uri.getScheme())).setKeepAlive(false);
			platform = config.getString("platform");
			mapper = new ObjectMapper();
			mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"));
			mapper.addMixInAnnotations(Bounce.class, GoMailBounceMixIn.class);

		} else {
			throw new InvalidConfigurationException("missing.parameters");
		}
	}

	@Override
	protected void sendEmail(JsonObject json, final Handler<Message<JsonObject>> handler) {
		if (json == null || json.getArray("to") == null || json.getString("from") == null
				|| json.getString("subject") == null || json.getString("body") == null) {
			log.error(json);
			handler.handle(new ResultMessage().error("invalid.parameters"));
			return;
		}
		if (json.getArray("to").size() > 1) {
			final AtomicInteger count = new AtomicInteger(json.getArray("to").size());
			final AtomicBoolean success = new AtomicBoolean(true);
			final JsonArray errors = new JsonArray();
			final Handler<Message<JsonObject>> 	h = new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					if (!"ok".equals(message.body().getString("status"))) {
						success.set(false);
						errors.addString(message.body().getString("message"));
					}
					if (count.decrementAndGet() == 0) {
						if (success.get()) {
							handler.handle(new ResultMessage());
						} else {
							handler.handle(new ResultMessage().error(errors.encode()));
						}
					}
				}
			};
			for (Object to : json.getArray("to")) {
				send(json.copy().putArray("to", new JsonArray().addString(to.toString())), h);
			}
		} else {
			send(json, handler);
		}
	}

	private void send(JsonObject json, final Handler<Message<JsonObject>> handler) {
		HttpClientRequest req = httpClient.post("/smtp/" + this.platform, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				if (resp.statusCode() == 200) {
					handler.handle(new ResultMessage());
				} else {
					handler.handle(new ResultMessage().error("GoMail HTTP status: " + resp.statusMessage()));					
				}
			}
		});
		req.putHeader("authorization", basicAuthHeader);
		JsonObject to = new JsonObject();
		JsonObject from = new JsonObject();
		JsonObject subject = new JsonObject();
		JsonObject headers = new JsonObject();

		to.putString("address", json.getArray("to").get(0).toString());

		from.putString("address", json.getString("from"));
		subject.putString("Subject", json.getString("subject"));

		JsonObject payload = new JsonObject().putObject("from", from)
				.putObject("to", to)
				.putString("subject", json.getString("subject"))
				.putString("body", json.getString("body"));

		/* TODO: Uncomment when GoMail supports multiple recipients
		if (json.getArray("cc") != null && json.getArray("cc").size() > 0) {
			JsonArray cc = new JsonArray();
			for (Object o : json.getArray("cc")) {
				JsonObject ccRecipient = new JsonObject();
				ccRecipient.putString("address", o.toString());
				cc.add(ccRecipient);
			}
			payload.putArray("cc", cc);
		}
		if (json.getArray("bcc") != null && json.getArray("bcc").size() > 0) {
			JsonArray bcc = new JsonArray();
			for (Object o : json.getArray("bcc")) {
				JsonObject bccRecipient = new JsonObject();
				bccRecipient.putString("address", o.toString());
				bcc.add(bccRecipient);
			}
			payload.putArray("bcc", bcc);
		}
		*/

		if (json.getArray("headers") != null) {
			for (Object o : json.getArray("headers")) {
				if (!(o instanceof JsonObject))
					continue;
				JsonObject h = (JsonObject) o;
				headers.putString(h.getString("name"), h.getString("value"));
			}
		}
		if (headers.size() > 0) {
			payload.putObject("headers", headers);
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

								JsonArray l = res.getArray("hard_bounces");
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
