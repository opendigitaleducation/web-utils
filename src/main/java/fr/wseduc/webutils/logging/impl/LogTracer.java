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

package fr.wseduc.webutils.logging.impl;


import fr.wseduc.webutils.logging.Tracer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class LogTracer implements Tracer {

	private static final Logger log = LoggerFactory.getLogger(LogTracer.class);

	@Override
	public void setName(String name) {

	}

	@Override
	public void info(String logMessage) {
		log.info(logMessage);
	}

	@Override
	public void error(String logMessage) {
		log.error(logMessage);
	}

}
