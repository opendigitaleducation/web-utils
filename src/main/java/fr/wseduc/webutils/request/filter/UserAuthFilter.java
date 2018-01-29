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

package fr.wseduc.webutils.request.filter;

import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.CookieHelper;
import fr.wseduc.webutils.security.SecureHttpServerRequest;
import fr.wseduc.webutils.security.oauth.OAuthResourceProvider;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.LocalMap;

public class UserAuthFilter implements Filter, WithVertx {

	private static final Logger log = LoggerFactory.getLogger(UserAuthFilter.class);
	public static final String SESSION_ID = "oneSessionId";
	private final OAuthResourceProvider oauth;
	private final AbstractBasicFilter basicFilter;
	private Vertx vertx;

	public UserAuthFilter() {
		this.oauth = null;
		this.basicFilter  = null;
	}

	public UserAuthFilter(OAuthResourceProvider oauth) {
		this.oauth = oauth;
		this.basicFilter = null;
	}

	public UserAuthFilter(OAuthResourceProvider oauth, AbstractBasicFilter basicFilter) {
		this.oauth = oauth;
		this.basicFilter = basicFilter;
	}

	@Override
	public void canAccess(HttpServerRequest request, Handler<Boolean> handler) {
		String oneSeesionId = CookieHelper.getInstance().getSigned(SESSION_ID, request);
		if (oneSeesionId != null && !oneSeesionId.trim().isEmpty()) {
			handler.handle(true);
		} else if (basicFilter != null && request instanceof SecureHttpServerRequest &&
				basicFilter.hasBasicHeader(request)) {
			basicFilter.validate((SecureHttpServerRequest) request, handler);
		} else if (oauth != null && request instanceof SecureHttpServerRequest &&
				oauth.hasBearerHeader(request)) {
			oauth.validToken((SecureHttpServerRequest) request, handler);
		} else {
			handler.handle(false);
		}
	}

	@Override
	public void deny(HttpServerRequest request) {
		redirectLogin(vertx, request);
	}

	public static void redirectLogin(Vertx vertx, HttpServerRequest request) {
		String callBack = "";
		String location = "";
		String scheme = Renders.getScheme(request);
		String host = Renders.getHost(request);
		try {
			callBack = scheme + "://" + host + request.uri();
			location = scheme + "://" + host;
			if (request.headers().get("X-Forwarded-For") == null) {
				location = location.split(":")[0] + ":8009";
			}
			callBack = URLEncoder.encode(callBack, "UTF-8");
			LocalMap<Object, Object> confServer = null;
			if (vertx != null) {
				confServer = vertx.sharedData().getLocalMap("server");
			}
			String loginUri = null;
			if (confServer != null) {
				loginUri = (String) confServer.get("loginUri");
			}
			if (loginUri != null && !loginUri.trim().isEmpty()) {
				if (loginUri.startsWith("http")) {
					location = loginUri;
				} else {
					location += loginUri;
				}
				String callbackParam = (String) confServer.get("callbackParam");
				if (callbackParam != null && !callbackParam.trim().isEmpty()) {
					location += (location.contains("?") ? "&" : "?") + callbackParam + "=" + callBack;
				}
			} else {
				location += "/auth/login?callback=" + callBack;
			}
		} catch (UnsupportedEncodingException ex) {
			log.error(ex.getMessage(), ex);
		}
		if (CookieHelper.getInstance().getSigned(SESSION_ID, request) != null) {
			CookieHelper.set(SESSION_ID, "", 0l, request);
			CookieHelper.set("authenticated", "", 0l, request);
		}
		request.response().setStatusCode(302);
		request.response().putHeader("Location", location);
		request.response().end();
	}

	@Override
	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

}
