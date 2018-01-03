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

package fr.wseduc.webutils.validation;

import fr.wseduc.webutils.data.FileResolver;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.util.List;


public class JsonSchemaValidator {

	private static final String JSONSCHEMA_PATH = FileResolver.absolutePath("jsonschema");
	private String address;
	private EventBus eb;
	private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

	private JsonSchemaValidator() {}

	private static class JsonSchemaValidatorHolder {
		private static final JsonSchemaValidator instance = new JsonSchemaValidator();
	}

	public static JsonSchemaValidator getInstance() {
		return JsonSchemaValidatorHolder.instance;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setEventBus(EventBus eb) {
		this.eb = eb;
	}

	public void loadJsonSchema(final String keyPrefix, Vertx vertx) {
		final FileSystem fs = vertx.fileSystem();
		fs.exists(JSONSCHEMA_PATH, new Handler<AsyncResult<Boolean>>() {
			@Override
			public void handle(AsyncResult<Boolean> event) {
				if (event.failed() || Boolean.FALSE.equals(event.result())) {
					log.debug("Json schema directory not found.");
					return;
				}
				fs.readDir(JSONSCHEMA_PATH, new Handler<AsyncResult<List<String>>>() {
					@Override
					public void handle(AsyncResult<List<String>> event) {
						if (event.succeeded()) {
							for (final String path : event.result()) {
								final String key = keyPrefix + path.substring(
										path.lastIndexOf(File.separatorChar) + 1, path.lastIndexOf('.'));
								fs.readFile(path, new Handler<AsyncResult<Buffer>>() {
									@Override
									public void handle(AsyncResult<Buffer> event) {
										if (event.succeeded()) {
											JsonObject j = new JsonObject()
													.put("action","addSchema")
													.put("key", key)
													.put("jsonSchema",
															new JsonObject(event.result().toString()));
											eb.publish(address, j);
										} else {
											log.error("Error loading json schema : " + path, event.cause());
										}
									}
								});
							}
						} else {
							log.error("Error loading json schemas.", event.cause());
						}
					}
				});
			}
		});
	}

	public void validate(String schema, JsonObject json, Handler<AsyncResult<Message<JsonObject>>> handler) {
		JsonObject j = new JsonObject()
				.put("action", "validate")
				.put("key", schema)
				.put("json", json);

		eb.send(address, j, new DeliveryOptions().setSendTimeout(10000), handler);
	}

}
