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

package fr.wseduc.webutils.security;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;

import static fr.wseduc.webutils.security.XSSUtils.safeMultiMap;


public class XssSecuredHttpServerRequest extends SecureHttpServerRequest {

	public XssSecuredHttpServerRequest(HttpServerRequest request) {
		super(request);
	}

	@Override
	public MultiMap formAttributes() {
		return safeMultiMap(super.formAttributes());
	}

	@Override
	public MultiMap params() {
		return safeMultiMap(super.params());
	}

	@Override
	public MultiMap headers() {
		return safeMultiMap(super.headers());
	}

}
