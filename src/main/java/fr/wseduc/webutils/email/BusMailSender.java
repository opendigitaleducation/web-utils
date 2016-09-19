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

package fr.wseduc.webutils.email;


import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;

public class BusMailSender extends NotificationHelper {

	protected String emailAddress;
	private final EventBus eb;

	protected BusMailSender(Vertx vertx, JsonObject config) {
		this(vertx, config, null);
	}

	public BusMailSender(Vertx vertx, JsonObject config, String emailAddress) {
		super(vertx, config);
		this.eb = vertx.eventBus();
		this.emailAddress = emailAddress;
	}

	protected void sendEmail(JsonObject json, Handler<AsyncResult<Message<JsonObject>>> handler) {
		try {
			json.put("body", new String(json.getString("body").getBytes("UTF-8"), "ISO-8859-1"));
			eb.send(emailAddress, json, handler);
		} catch (UnsupportedEncodingException e) {
			log.error(e.getMessage(), e);
			handler.handle(new DefaultAsyncResult<>(e));
		}
	}

}
