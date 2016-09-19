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

package fr.wseduc.webutils.eventbus;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class ResultMessage implements Message<JsonObject> {

	private final JsonObject body;

	public ResultMessage() {
		this(null);
	}

	public ResultMessage(JsonObject j) {
		if (j == null) {
			body = new JsonObject().put("status", "ok");
		} else {
			body = j;
			if (!j.containsKey("status")) {
				j.put("status", "ok");
			}
		}
	}

	public ResultMessage put(String attr, Object o) {
		body.put(attr, o);
		return this;
	}

	public ResultMessage error(String message) {
		body.put("status", "error");
		body.put("message", message);
		return this;
	}

	@Override
	public String address() {
		return null;
	}

	@Override
	public MultiMap headers() {
		return null;
	}

	@Override
	public JsonObject body() {
		return body;
	}

	@Override
	public String replyAddress() {
		return null;
	}

	@Override
	public boolean isSend() {
		return false;
	}

	@Override
	public void reply(Object message) {

	}

	@Override
	public <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler) {

	}

	@Override
	public void reply(Object message, DeliveryOptions options) {

	}

	@Override
	public <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {

	}

	@Override
	public void fail(int failureCode, String message) {

	}

}
