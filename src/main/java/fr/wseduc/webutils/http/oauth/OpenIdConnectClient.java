/*
 * Copyright © WebServices pour l'Éducation, 2016
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

package fr.wseduc.webutils.http.oauth;

import fr.wseduc.webutils.security.JWT;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public final class OpenIdConnectClient extends OAuth2Client {

	private final JWT jwt;
	private String userInfoUrn;
	private boolean basic = true;
	private String logoutUri;

	public OpenIdConnectClient(URI uri, String clientId, String secret, String authorizeUrn,
			String tokenUrn, String redirectUri, Vertx vertx, int poolSize, String certificatesUri)
			throws URISyntaxException {
		super(uri, clientId, secret, authorizeUrn, tokenUrn, redirectUri, vertx, poolSize);
		this.jwt = new JWT(vertx, secret, (certificatesUri != null ? new URI(certificatesUri) : null));
	}

	@Override
	public void authorizationCodeToken(HttpServerRequest request, String state, Handler<JsonObject> handler) {
		authorizationCodeToken(request, state, null, handler);
	}

	public void authorizationCodeToken(HttpServerRequest request, String state, final String nonce,
			final Handler<JsonObject> handler) {
		super.authorizationCodeToken(request, state, basic, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject res) {
				if ("ok".equals(res.getString("status"))) {
					final JsonObject token = res.getJsonObject("token");
					if (token == null) {
						log.error("invalid token");
						handler.handle(null);
						return;
					}
					final String idToken = token.getString("id_token");
					jwt.verifyAndGet(idToken, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject payload) {
							if (payload == null) {
								log.error("invalid payload.");
								handler.handle(null);
								return;
							}
							final String nce = payload.getString("nonce");
							if (nce != null && !nce.equals(nonce)) {
								log.error("invalid nonce");
								handler.handle(null);
								return;
							}
							payload.put("id_token_hint", idToken);
							if (isNotEmpty(userInfoUrn)) {
								getUserInfo(token.getString("access_token"), payload, handler);
							} else {
								handler.handle(payload);
							}
						}
					});
				} else {
					handler.handle(null);
				}
			}
		});
	}

	private void getUserInfo(String accessToken, final JsonObject payload, final Handler<JsonObject> handler) {
		getProtectedResource(userInfoUrn, accessToken, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				if (resp.statusCode() == 200) {
					resp.bodyHandler(new Handler<Buffer>() {
						@Override
						public void handle(Buffer buffer) {
							try {
								JsonObject j = new JsonObject(buffer.toString());
								payload.mergeIn(j);
							} catch (RuntimeException e) {
								log.error("Get userinfo error.", e);
							} finally {
								handler.handle(payload);
							}
						}
					});
				}else {
					handler.handle(payload);
				}
			}
		});
	}

	public String logoutUri(String state, String hint, String callback) {
		if (isNotEmpty(logoutUri)) {
			StringBuilder sb = new StringBuilder();
			try {
				sb.append(logoutUri).append("?id_token_hint=").append(hint).append("&state=").append(state)
						.append("&post_logout_redirect_uri=").append(URLEncoder.encode(callback, "UTF-8"));
				return sb.toString();
			} catch (UnsupportedEncodingException e) {
				log.error(e.getMessage(), e);
			}
		}
		return callback;
	}

	public void setUserInfoUrn(String userInfoUrn) {
		this.userInfoUrn = userInfoUrn;
	}

	public void setLogoutUri(String logoutUri) {
		this.logoutUri = logoutUri;
	}

	public void setBasic(boolean basic) {
		this.basic = basic;
	}

}
