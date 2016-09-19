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

import java.util.ArrayList;
import java.util.List;

import fr.wseduc.webutils.request.AccessLogger;
import fr.wseduc.webutils.security.XssSecuredHttpServerRequest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;

import fr.wseduc.webutils.security.SecureHttpServerRequest;
/*
 * Implement a Security Handler with a pre-configurate filters chain 
 */
public abstract class SecurityHandler implements Handler<HttpServerRequest> {

	static protected List<Filter> chain = new ArrayList<>();
	static {
		chain.add(new AccessLoggerFilter(new AccessLogger()));
		chain.add(new UserAuthFilter());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Handler<Boolean> chainToHandler(final HttpServerRequest request) {
		final Handler [] handlers = new Handler[chain.size()];
		handlers[chain.size() - 1] = new Handler<Boolean>() {

			@Override
			public void handle(Boolean access) {
				if (Boolean.TRUE.equals(access)) {
					filter(request);
				} else {
					chain.get(chain.size() - 1).deny(request);
				}
			}
		};
		for (int i = chain.size() - 2; i >= 0; i--) {
			final int idx = i;

			handlers[i] = new Handler<Boolean>() {

				@Override
				public void handle(Boolean access) {
					if (Boolean.TRUE.equals(access)) {
						chain.get(idx + 1).canAccess(request, handlers[idx + 1]);
					} else {
						chain.get(idx).deny(request);
					}
				}
			};
		}

		return handlers[0];
	}

	@Override
	public void handle(HttpServerRequest request) {
		if (chain != null && !chain.isEmpty()) {
			SecureHttpServerRequest sr = new XssSecuredHttpServerRequest(request);
			chain.get(0).canAccess(sr, chainToHandler(sr));
		} else {
			filter(request);
		}
	}

	public static void addFilter(Filter filter) {
		chain.add(filter);
	}

	public static void clearFilters() {
		chain.clear();
	}

	public static void setVertx(Vertx vertx) {
		if (chain != null) {
			for (Filter f : chain) {
				if (f instanceof WithVertx) {
					((WithVertx) f).setVertx(vertx);
				}
			}
		}
	}

	public abstract void filter(HttpServerRequest request);
}
