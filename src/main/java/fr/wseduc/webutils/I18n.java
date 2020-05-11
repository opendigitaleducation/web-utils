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

package fr.wseduc.webutils;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import fr.wseduc.webutils.data.FileResolver;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.SecureHttpServerRequest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class I18n {

	private static final Logger log = LoggerFactory.getLogger(I18n.class);
	private final static String messagesDir = FileResolver.absolutePath("i18n");
	private final static Locale defaultLocale = Locale.ENGLISH;
	private final static Locale defaultLocale2 = Locale.FRENCH;
	public final static String DEFAULT_DOMAIN = "default-domain";
	private Map<String, Map<Locale, JsonObject>> messagesByDomains = new HashMap<>();
	private Map<String, Map<Locale, JsonObject>> messagesByThemes = new HashMap<>();

	private I18n(){}

	private static class I18nHolder {
		private static final I18n instance = new I18n();
	}

	public static I18n getInstance() {
		return I18nHolder.instance;
	}

	public void init(Vertx vertx) {
		try {
			if (vertx.fileSystem().existsBlocking(messagesDir)) {
				Map<Locale, JsonObject> messages = messagesByDomains.get(DEFAULT_DOMAIN);
				if (messages == null) {
					messages = new HashMap<>();
					messagesByDomains.put(DEFAULT_DOMAIN, messages);
				}
				for(String path : vertx.fileSystem().readDirBlocking(messagesDir)) {
					if (vertx.fileSystem().propsBlocking(path).isRegularFile()) {
						Locale l = Locale.forLanguageTag(new File(path).getName().split("\\.")[0]);
						JsonObject jo = new JsonObject(vertx.fileSystem().readFileBlocking(path).toString());
						messages.put(l,jo);
					}
				}
			} else {
				log.warn("I18n directory " + messagesDir + " doesn't exist.");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public String translate(String key, String domain, String acceptLanguage, String... args) {
		return translate(key, domain, getLocale(acceptLanguage), args);
	}

	@Deprecated
	public String translate(String key, Locale locale, String... args) {
		return translate(key, DEFAULT_DOMAIN, locale, args);
	}

	public String translate(String key, String domain, Locale locale, String... args) {
		if (key == null) return "";
		Map<Locale, JsonObject> messages = getMessagesMap(domain);
		if (messages == null) {
			return key;
		}
		JsonObject bundle = messages.get(locale) != null ? messages.get(locale) : messages.get(defaultLocale);
		if (bundle == null) {
			return key;
		}
		String text =  bundle.getString(key) != null ? bundle.getString(key) : key;
		if (args.length > 0) {
			try {
				for (int i = 0; i < args.length; i++) {
					text = text.replaceAll("\\{" + i + "\\}", args[i]);
				}
			} catch (RuntimeException e) {
				log.error("Error replacing i18n variable", e);
			}
		}
		return text;
	}

	private Map<Locale, JsonObject> getMessagesMap(String domain) {
		return getMessagesMap(domain, false);
	}

	private Map<Locale, JsonObject> getMessagesMap(String domain, Boolean byTheme) {
		final Map<String, Map<Locale, JsonObject>> map = byTheme ? messagesByThemes : messagesByDomains;
		Map<Locale, JsonObject> messages = map.get(domain);
		if (messages == null) {
			messages = messagesByDomains.get(DEFAULT_DOMAIN);
		}
		return messages;
	}

	@Deprecated
	public JsonObject load(String acceptLanguage) {
		return load(acceptLanguage, DEFAULT_DOMAIN);
	}

	@Deprecated
	public JsonObject load(String acceptLanguage, String domain) {
		Map<Locale, JsonObject> messages = getMessagesMap(domain);
		if (messages == null) {
			return new JsonObject();
		}
		Locale l = getLocale(acceptLanguage);
		JsonObject bundle = messages.get(l) != null ? messages.get(l) : messages.get(defaultLocale);
		if (bundle == null) {
			bundle = messages.get(defaultLocale2);
		}
		return bundle;
	}

	public JsonObject load(HttpServerRequest request) {
		final String domain = Renders.getHost(request);
		final String acceptLanguage = I18n.acceptLanguage(request);
		String themeName = null;

		if (request instanceof SecureHttpServerRequest) {
			JsonObject session = ((SecureHttpServerRequest) request).getSession();
			if (session != null && session.getJsonObject("cache") != null &&
					session.getJsonObject("cache").getJsonObject("preferences") != null &&
					Utils.isNotEmpty(session.getJsonObject("cache").getJsonObject("preferences").getString("theme"))) {
				try {
					themeName = session.getJsonObject("cache").getJsonObject("preferences")
							.getString("theme");
				} catch (DecodeException e) {
					log.error("Error getting language in cache.", e);
				}
			}
		}

		Map<Locale, JsonObject> messages = themeName != null ? getMessagesMap(themeName, true) : getMessagesMap(domain);
		if (messages == null) {
			return new JsonObject();
		}
		Locale l = getLocale(acceptLanguage);
		JsonObject bundle = messages.get(l) != null ? messages.get(l) : messages.get(defaultLocale);
		if (bundle == null) {
			bundle = messages.get(defaultLocale);
		}
		return bundle;
	}

	/* Dummy implementation. Just use the first langage option ...
	 * Header example : "Accept-Language:fr,en-us;q=0.8,fr-fr;q=0.5,en;q=0.3"
	 */
	private Locale getLocale(String acceptLanguage) {
		if (acceptLanguage == null) {
			acceptLanguage = "fr";
		}
		String[] langs = acceptLanguage.split(",");
		return Locale.forLanguageTag(langs[0].split("-")[0]);
	}

	public static String acceptLanguage(HttpServerRequest request) {
		final String acceptLanguage = request.headers().get("Accept-Language") != null ?
				request.headers().get("Accept-Language") : "fr";
		if (request instanceof SecureHttpServerRequest) {
			JsonObject session = ((SecureHttpServerRequest) request).getSession();
			if (session != null && session.getJsonObject("cache") != null &&
					session.getJsonObject("cache").getJsonObject("preferences") != null &&
					Utils.isNotEmpty(session.getJsonObject("cache").getJsonObject("preferences").getString("language"))) {
				try {
					JsonObject language = new JsonObject(session.getJsonObject("cache").getJsonObject("preferences")
							.getString("language"));
					return language.getString(DEFAULT_DOMAIN, acceptLanguage);
				} catch (DecodeException e) {
					log.error("Error getting language in cache.", e);
				}
			}
		}
		return acceptLanguage;
	}

	@Deprecated
	public void add(Locale locale, JsonObject keys) {
		add(DEFAULT_DOMAIN, locale, keys);
	}

	public void add(String domain, Locale locale, JsonObject keys) {
		add(domain, locale, keys, false);
	}

	public void add(String domain, Locale locale, JsonObject keys, Boolean byTheme) {
		Map<Locale, JsonObject> messages = byTheme ?
				messagesByThemes.getOrDefault(domain, messagesByDomains.get(domain)) : messagesByDomains.get(domain);
		if (messages == null) {
			HashMap<Locale, JsonObject> defaultMessages = (HashMap<Locale, JsonObject>)
					messagesByDomains.get(DEFAULT_DOMAIN);
			if (defaultMessages == null) return;
			messages = new HashMap<>();
			for(Locale l : defaultMessages.keySet()){
				messages.put(l, defaultMessages.get(l).copy());
			}
			final Map<String, Map<Locale, JsonObject>> map = byTheme ? messagesByThemes : messagesByDomains;
			map.put(domain, messages);
		}
		JsonObject m = messages.get(locale);
		if (m == null) {
			messages.put(locale, keys);
		} else {
			m.mergeIn(keys);
		}
	}

	public JsonArray getLanguages(String domain) {
		final Map<Locale, JsonObject> messages = getMessagesMap(domain);
		final JsonArray languages = new JsonArray();
		if (messages != null) {
			for (Locale l : messages.keySet()) {
				languages.add(l.getLanguage());
			}
		}
		return languages;
	}

}
