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

package fr.wseduc.webutils.data;

import io.vertx.core.json.JsonObject;

import java.io.File;

public class FileResolver {

	private String basePath;

	private FileResolver() {}

	public void setBasePath(JsonObject config) {
		setBasePath(config.getString("cwd"));
	}

	public void setBasePath(String basePath) {
		if (basePath != null) {
			this.basePath = basePath + (!basePath.endsWith(File.separator) ? File.separator : "");
		} else {
			this.basePath = "";
		}
	}

	private static class FileResolverHolder {
		private static final FileResolver instance = new FileResolver();
	}

	public static FileResolver getInstance() {
		return FileResolverHolder.instance;
	}

	public String getAbsolutePath(String file) {
		if (file == null || file.isEmpty() || file.startsWith(File.separator)) return file;
		return basePath + file;
	}

	public static String absolutePath(String file) {
		return FileResolver.getInstance().getAbsolutePath(file);
	}

}
