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
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.ServerCookieEncoder;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;

import fr.wseduc.webutils.security.HmacSha1;

public class CookieHelper {

	private String signKey;
	private Logger log;

	private CookieHelper(){}

	private static class CookieHolder {
		private static final CookieHelper instance = new CookieHelper();
	}

	public static CookieHelper getInstance() {
		return CookieHolder.instance;
	}

	public void init(String signkey, Logger log) {
		this.signKey = signkey;
		this.log = log;
	}

	public static String get(String name, HttpServerRequest request) {
		if (request.headers().get("Cookie") != null) {
			Set<Cookie> cookies = CookieDecoder.decode(request.headers().get("Cookie"));
			for (Cookie c : cookies) {
				if (c.getName().equals(name)) {
					return c.getValue();
				}
			}
		}
		return null;
	}

	public static void set(String name, String value, HttpServerRequest request) {
		set(name, value, Long.MIN_VALUE, request);
	}

	public static void set(String name, String value, long timeout, HttpServerRequest request) {
		set(name, value, timeout, "/", request);
	}

	public static void set(String name, String value, long timeout, String path, HttpServerRequest request) {
		Cookie cookie = new DefaultCookie(name, value);
		cookie.setMaxAge(timeout);
		cookie.setSecure("https".equals(Renders.getScheme(request)));
		if (path != null && !path.trim().isEmpty()) {
			cookie.setPath(path);
		}
		request.response().headers().add("Set-Cookie", ServerCookieEncoder.encode(cookie));
	}

	public void setSigned(String name, String value, long timeout, HttpServerRequest request) {
		setSigned(name, value, timeout, "/", request);
	}

	public void setSigned(String name, String value, long timeout, String path, HttpServerRequest request) {
		Cookie cookie = new DefaultCookie(name, value);
		cookie.setMaxAge(timeout);
		cookie.setSecure("https".equals(Renders.getScheme(request)));
		if (path != null && !path.trim().isEmpty()) {
			cookie.setPath(path);
		}
		if (signKey != null) {
			try {
				signCookie(cookie);
			} catch (InvalidKeyException | NoSuchAlgorithmException
					| IllegalStateException | UnsupportedEncodingException e) {
				log.error(e);
				return;
			}
			request.response().headers().add("Set-Cookie", ServerCookieEncoder.encode(cookie));
		}
	}

	private void signCookie(Cookie cookie)
			throws InvalidKeyException, NoSuchAlgorithmException,
			IllegalStateException, UnsupportedEncodingException {
		String signature = HmacSha1.sign(
				cookie.getDomain()+cookie.getName()+
				cookie.getPath()+cookie.getValue(), signKey);
		cookie.setValue(cookie.getValue() + ":" + signature);
	}

	public String getSigned(String name, HttpServerRequest request) {
		return getSigned(name, "/", request);
	}

	public String getSigned(String name, String path, HttpServerRequest request) {
		if (request.headers().get("Cookie") != null) {
			Set<Cookie> cookies = CookieDecoder.decode(request.headers().get("Cookie"));
			for (Cookie c : cookies) {
				if (c.getName().equals(name) && c.getValue().contains(":")) {
					int idx = c.getValue().lastIndexOf(":");
					if (idx > c.getValue().length() - 1) continue;
					String value = c.getValue().substring(0, idx);
					String signature = c.getValue().substring(idx+1);
					String calcSign = null;
					String cookiePath = path;
					if (cookiePath == null || cookiePath.trim().isEmpty()) {
						cookiePath = c.getPath();
					}
					try {
						calcSign = HmacSha1.sign(
								c.getDomain()+c.getName()+
								cookiePath+value, signKey);
					} catch (InvalidKeyException | NoSuchAlgorithmException
							| IllegalStateException
							| UnsupportedEncodingException e) {
					}
					if (calcSign != null && calcSign.equals(signature)) {
						return value;
					}
				}
			}
		}
		return null;
	}
}
