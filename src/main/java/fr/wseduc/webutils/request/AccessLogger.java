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

package fr.wseduc.webutils.request;

import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.LoggerFactory;

public class AccessLogger {

	protected static final io.vertx.core.logging.Logger log = LoggerFactory.getLogger(AccessLogger.class);

	public void log(HttpServerRequest request, Handler<Void> handler) {
		log.trace(formatLog(request));
		handler.handle(null);
	}

	protected String formatLog(HttpServerRequest request) {
		return Renders.getIp(request) + " " + request.method() + " " + request.path() + getQuery(request);
	}

	private String getQuery(HttpServerRequest request) {
		if (request.query() != null) {
			return "?" + request.query();
		}
		return "";
	}

}
