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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class XSSUtils {

	private XSSUtils() {}

	private static final Pattern[] patterns = new Pattern[]{
			Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
//			Pattern.compile("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
//			Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("<script>", Pattern.CASE_INSENSITIVE),
			Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
			Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
			Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
			Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
			Pattern.compile("('|\")\\s*on(click|context|mouse|dblclick|key|abort|error|before|hash|load|page|" +
					"resize|scroll|unload|blur|change|focus|in|reset|search|select|submit|drag|drop|copy|cut|paste|" +
					"after| before|can|end|duration|emp|play|progress|seek|stall|sus|time|volume|waiting|message|open|touch|" +
					"on|off|pop|show|storage|toggle|wheel)(.*?)=",
					Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
	};

	public static MultiMap safeMultiMap(MultiMap m) {
		for (String name : m.names()) {
			List<String> values = m.getAll(name);
			List<String> safeValues = new ArrayList<>();
			if (values == null) continue;
			for (String value: values) {
				safeValues.add(stripXSS(value));
			}
			m.set(name, safeValues);
		}
		return m;
	}

	public static String stripXSS(String value) {
		if (value != null) {
			//value = ESAPI.encoder().canonicalize(value);
			value = value.replaceAll("\0", "");
			for (Pattern scriptPattern : patterns){
				value = scriptPattern.matcher(value).replaceAll("");
			}
		}
		return value;
	}

}
