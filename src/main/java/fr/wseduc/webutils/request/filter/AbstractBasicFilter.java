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


import fr.wseduc.webutils.security.SecureHttpServerRequest;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

import java.util.Base64;

public abstract class AbstractBasicFilter {

	public void validate(final SecureHttpServerRequest request, final Handler<Boolean> handler) {
		String authorization = request.headers().get("Authorization");
		if (authorization != null && authorization.startsWith("Basic ")) {
			String credentials = new String(Base64.getDecoder().decode(authorization.substring(6)));
			final String[] c = credentials.split(":");
			if (c.length == 2) {
				request.pause();
				validateClientScope(c[0], c[1], new Handler<String>() {
					@Override
					public void handle(String scope) {
						boolean res = scope != null && !scope.trim().isEmpty();
						if (res) {
							request.setAttribute("client_id", c[0]);
							request.setAttribute("scope", scope);
							request.setAttribute("authorization_type", "Basic");
						}
						request.resume();
						handler.handle(res);
					}
				});
			} else {
				handler.handle(false);
			}
		} else {
			handler.handle(false);
		}
	}

	public boolean hasBasicHeader(HttpServerRequest request) {
		String authorization = request.headers().get("Authorization");
		return authorization != null && authorization.startsWith("Basic ");
	}

	protected abstract void validateClientScope(String clientId, String secret, Handler<String> handler);

}
