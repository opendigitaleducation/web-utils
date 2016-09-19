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

import fr.wseduc.webutils.request.AccessLogger;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

public class AccessLoggerFilter implements Filter {

	private final AccessLogger accessLogger;

	public AccessLoggerFilter(AccessLogger accessLogger) {
		this.accessLogger = accessLogger;
	}

	@Override
	public void canAccess(HttpServerRequest request, final Handler<Boolean> handler) {
		accessLogger.log(request, new Handler<Void>() {
			@Override
			public void handle(Void v) {
				handler.handle(true);
			}
		});
	}

	@Override
	public void deny(HttpServerRequest request) {

	}

}
