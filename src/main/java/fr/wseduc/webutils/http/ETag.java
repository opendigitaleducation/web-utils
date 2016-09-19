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

package fr.wseduc.webutils.http;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

public class ETag {

	public static void addHeader(HttpServerResponse response, String fileId) {
		response.headers().add("ETag", fileId);
	}

	public static boolean check(HttpServerRequest request, String fileId) {
		String inm = request.headers().get("If-None-Match");
		if (inm != null) {
			return inm.equals(fileId);
		}
		return false;
	}

}
