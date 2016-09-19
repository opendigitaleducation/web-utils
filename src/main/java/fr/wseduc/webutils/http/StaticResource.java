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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

public class StaticResource {

	private static final SimpleDateFormat format =
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.UK);
	static {
		format.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public static void addLastModifiedHeader(HttpServerResponse response, Date resourceLastModified) {
		response.headers().add("Last-Modified", format.format(resourceLastModified));
	}

	public static void addLastModifiedHeader(HttpServerResponse response, String resourceLastModified) {
		response.headers().add("Last-Modified", resourceLastModified);
	}

	public static boolean checkLastModified(HttpServerRequest request, String resourceLastModified) {
		String ims = request.headers().get("If-Modified-Since");
		if (ims != null && resourceLastModified != null) {
			try {
				Date imsDate = format.parse(ims);
				Date rlm = format.parse(resourceLastModified);
				return imsDate != null && rlm != null &&
						imsDate.getTime() >= rlm.getTime();
			} catch (ParseException | NumberFormatException e) {
				return false;
			}
		}
		return false;
	}

	public static void serveRessource(HttpServerRequest request, String ressourcePath,
			String resourceLastModified) {
		serveRessource(request, ressourcePath, resourceLastModified, false);
	}

	public static void serveRessource(HttpServerRequest request, String ressourcePath,
			String resourceLastModified, boolean dev) {
		if (dev) {
			request.response().headers().add("Cache-Control", "max-age=0, no-cache, must-revalidate");
		}
		addLastModifiedHeader(request.response(), resourceLastModified);
		if (checkLastModified(request, resourceLastModified)) {
			request.response().setStatusCode(304).end();
		} else {
			request.response().sendFile(ressourcePath);
		}
	}

	public static String formatDate(long lastModified) {
		return formatDate(new Date(lastModified));
	}

	public static String formatDate(Date lastModified) {
		return format.format(lastModified);
	}

}
