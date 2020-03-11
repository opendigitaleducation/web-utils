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

package fr.wseduc.webutils.security.oauth;

import fr.wseduc.webutils.DefaultAsyncResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.security.SecureHttpServerRequest;

public class DefaultOAuthResourceProvider implements OAuthResourceProvider {

	private final EventBus eb;
	private static final String OAUTH_ADDRESS = "wse.oauth";

	public DefaultOAuthResourceProvider(EventBus eb) {
		this.eb = eb;
	}

	protected void getOAuthInfos(final SecureHttpServerRequest request, final JsonObject payload, final Handler<AsyncResult<JsonObject>> handler){
		eb.send(OAUTH_ADDRESS, payload, new Handler<AsyncResult<Message<JsonObject>>>() {

			@Override
			public void handle(AsyncResult<Message<JsonObject>> event) {
				if (event.succeeded()) {
					Message<JsonObject> res = event.result();
					request.resume();
					if ("ok".equals(res.body().getString("status"))) {
						handler.handle(new DefaultAsyncResult<>(res.body()));
					} else {
						handler.handle(new DefaultAsyncResult<>(new Exception("Failed to authenticate")));
					}
				} else {
					handler.handle(new DefaultAsyncResult<>(event.cause()));
				}
			}
		});
	}

	@Override
	public void validToken(final SecureHttpServerRequest request, final Handler<Boolean> handler) {
		JsonObject headers = new JsonObject();
		for (String name : request.headers().names()) {
			headers.put(name, request.headers().get(name));
		}
		JsonObject params = new JsonObject();
		for (String name : request.params().names()) {
			params.put(name, request.params().get(name));
		}
		JsonObject json = new JsonObject()
		.put("headers", headers)
		.put("params", params);
		request.pause();
		getOAuthInfos(request, json, res->{
			if(res.succeeded()){
				request.setAttribute("client_id", res.result().getString("client_id"));
				request.setAttribute("remote_user", res.result().getString("remote_user"));
				request.setAttribute("scope", res.result().getString("scope"));
				request.setAttribute("authorization_type", "Bearer");
				handler.handle(customValidation(request));
			}else{
				handler.handle(false);
			}
		});
	}

	protected boolean customValidation(SecureHttpServerRequest request) {
		return true;
	}

	@Override
	public boolean hasBearerHeader(HttpServerRequest request) {
		String authorization = request.headers().get("Authorization");
		return authorization != null && authorization.startsWith("Bearer ");
	}

}
