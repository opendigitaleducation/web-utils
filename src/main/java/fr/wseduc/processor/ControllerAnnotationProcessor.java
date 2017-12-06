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

package fr.wseduc.processor;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes({"fr.wseduc.security.SecuredAction", "fr.wseduc.bus.BusAddress",
		"fr.wseduc.rs.Get", "fr.wseduc.rs.Post", "fr.wseduc.rs.Delete", "fr.wseduc.rs.Put",
		"fr.wseduc.rs.ApiDoc", "fr.wseduc.rs.ApiPrefixDoc"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ControllerAnnotationProcessor extends AbstractProcessor {

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			return false;
		}
		route(roundEnv);
		securedAction(roundEnv);
		apiDoc(roundEnv);
		return false;
	}

	private void apiDoc(RoundEnvironment roundEnv) {
		final Map<String,Set<String>> apis = new HashMap<>();

		String prefix = "";
		for (Element element : roundEnv.getElementsAnnotatedWith(ApiPrefixDoc.class)) {
			ApiPrefixDoc annotation = element.getAnnotation(ApiPrefixDoc.class);
			if (annotation == null) continue;
			prefix = annotation.value();
			if (prefix.isEmpty()) {
				prefix = element.getSimpleName().toString().toLowerCase();
			}
		}

		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}


		for (Element element : roundEnv.getElementsAnnotatedWith(ApiDoc.class)) {
			ApiDoc annotation = element.getAnnotation(ApiDoc.class);
			SecuredAction securedAnnotation = element.getAnnotation(SecuredAction.class);
			Delete deleteAnnotation = element.getAnnotation(Delete.class);
			Put putAnnotation = element.getAnnotation(Put.class);
			Get getAnnotation = element.getAnnotation(Get.class);
			Post postAnnotation = element.getAnnotation(Post.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null ||
					(deleteAnnotation == null && putAnnotation == null &&
							getAnnotation == null && postAnnotation == null)) {
				continue;
			}
			String path;
			String method;
			if (getAnnotation != null) {
				path = getAnnotation.value();
				method = "GET";
			} else if (postAnnotation != null) {
				path = postAnnotation.value();
				method = "POST";
			} else if (putAnnotation != null) {
				path = putAnnotation.value();
				method = "PUT";
			} else {
				path = deleteAnnotation.value();
				method = "DELETE";
			}

			String notes = "";
			if (securedAnnotation != null) {
				switch (securedAnnotation.type()) {
					case WORKFLOW:
						notes = "Workflow action.";
						break;
					case RESOURCE:
						notes = "Resource action.";
						break;
					case AUTHENTICATED:
						notes = "Authenticated action.";
						break;
				}
			} else {
				notes = "";
			}
			Set<String> controllerRoutes = getController(apis, clazz);
			controllerRoutes.add("{ \"path\" : \"" + prefix + path + "\", \"operations\" : [{" +
					"\"method\" : \"" + method + "\", " +
					"\"summary\" : \"" + annotation.value() + "\"," +
					"\"notes\" : \"" + notes + "\", " +
					"\"nickname\" : \"" + clazz.getQualifiedName().toString() + "_" +
					element.getSimpleName().toString() + "\", " +
					"\"parameters\" : [] " +
					"}]}");
		}

		final Map<String,Set<String>> swagger = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry : apis.entrySet()) {
			StringBuilder sb = new StringBuilder();
			sb.append("{\"swaggerVersion\":\"1.2\", \"resourcePath\" : \"")
					.append(prefix).append("\", \"apis\" : [");
			for (String s : entry.getValue()) {
				sb.append(s).append(",");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append("]}");
			Set<String> s = new HashSet<>();
			s.add(sb.toString());
			swagger.put(entry.getKey(), s);
		}
		writeFile("Swagger-", swagger);
	}

	private void route(RoundEnvironment roundEnv) {
		final Map<String,Set<String>> routes = new HashMap<>();

		for (Element element : roundEnv.getElementsAnnotatedWith(Post.class)) {
			Post annotation = element.getAnnotation(Post.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}
			Set<String> controllerRoutes = getController(routes, clazz);
			controllerRoutes.add("{ \"httpMethod\" : \"POST\", \"path\" : \"" +
					annotation.value() + "\", \"method\" : \"" + element.getSimpleName().toString() +
					"\", \"regex\" : " + annotation.regex() + "}");
		}

		for (Element element : roundEnv.getElementsAnnotatedWith(Get.class)) {
			Get annotation = element.getAnnotation(Get.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}
			Set<String> controllerRoutes = getController(routes, clazz);
			controllerRoutes.add("{ \"httpMethod\" : \"GET\", \"path\" : \"" +
					annotation.value() + "\", \"method\" : \"" + element.getSimpleName().toString() +
					"\", \"regex\" : " + annotation.regex() + "}");
		}

		for (Element element : roundEnv.getElementsAnnotatedWith(Put.class)) {
			Put annotation = element.getAnnotation(Put.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}
			Set<String> controllerRoutes = getController(routes, clazz);
			controllerRoutes.add("{ \"httpMethod\" : \"PUT\", \"path\" : \"" +
					annotation.value() + "\", \"method\" : \"" + element.getSimpleName().toString() +
					"\", \"regex\" : " + annotation.regex() + "}");
		}

		for (Element element : roundEnv.getElementsAnnotatedWith(Delete.class)) {
			Delete annotation = element.getAnnotation(Delete.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}
			Set<String> controllerRoutes = getController(routes, clazz);
			controllerRoutes.add("{ \"httpMethod\" : \"DELETE\", \"path\" : \"" +
					annotation.value() + "\", \"method\" : \"" + element.getSimpleName().toString() +
					"\", \"regex\" : " + annotation.regex() + "}");
		}

		for (Element element : roundEnv.getElementsAnnotatedWith(BusAddress.class)) {
			BusAddress annotation = element.getAnnotation(BusAddress.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}
			Set<String> controllerRoutes = getController(routes, clazz);
			controllerRoutes.add("{ \"httpMethod\" : \"BUS\", \"path\" : \"" +
					annotation.value() + "\", \"method\" : \"" + element.getSimpleName().toString() +
					"\", \"local\" : " + annotation.local() + "}");
		}

		writeFile("", routes);
	}

	private Set<String> getController(Map<String, Set<String>> routes, TypeElement clazz) {
		Set<String> controllerRoutes = routes.get(clazz.getQualifiedName().toString());
		if (controllerRoutes == null) {
			controllerRoutes = new TreeSet<>(Collections.reverseOrder());
			routes.put(clazz.getQualifiedName().toString(), controllerRoutes);
		}
		return controllerRoutes;
	}

	private void securedAction(RoundEnvironment roundEnv) {
		final Map<String,Set<String>> actions = new HashMap<>();

		for (Element element : roundEnv.getElementsAnnotatedWith(SecuredAction.class)) {
			SecuredAction annotation = element.getAnnotation(SecuredAction.class);
			TypeElement clazz = (TypeElement) element.getEnclosingElement();
			if(annotation == null || !isMethod(element) || clazz == null) {
				continue;
			}

			checkRights(annotation, clazz);
			Set<String> controllerActions = getController(actions, clazz);
			controllerActions.add("{ \"name\" : \"" + clazz.getQualifiedName().toString() + "|" +
					element.getSimpleName().toString() +
					"\", \"displayName\" : \"" + annotation.value() + "\", \"type\" : \"" +
					annotation.type().name() + "\"}");
		}

		writeFile("SecuredAction-", actions);
	}

	protected void checkRights(SecuredAction annotation, TypeElement clazz) {
		if (ActionType.WORKFLOW.equals(annotation.type()) && annotation.value().isEmpty()) {
			throw new RuntimeException("Workflow action can't contains empty value.");
		}
	}

	protected void writeFile(String prefixFilename, Map<String, Set<String>> actions) {
		Filer filer = processingEnv.getFiler();
		for (Map.Entry<String,Set<String>> e : actions.entrySet()) {
			try {
				String controller = e.getKey();
				FileObject f = filer.getResource(StandardLocation.CLASS_OUTPUT, "",
						prefixFilename + controller + ".json");
				BufferedReader r = new BufferedReader(new InputStreamReader(f.openInputStream(), "UTF-8"));
				String line;
				while((line = r.readLine()) != null) {
					e.getValue().add(line);
				}
				r.close();
			} catch (FileNotFoundException x) {
				// doesn't exist
			} catch (IOException ex) {
				error("Failed to load existing secured actions : " + ex);
			}
		}

		for (Map.Entry<String,Set<String>> e : actions.entrySet()) {
			try {
				String path = prefixFilename + e.getKey() + ".json";
				processingEnv.getMessager().printMessage(Kind.NOTE,"Writing "+ path);
				FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path);
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(f.openOutputStream(), "UTF-8"));
				for (String value : e.getValue()) {
					pw.println(value);
				}
				pw.close();
			} catch (IOException ex) {
				error("Failed to write secured actions : " + ex);
			}
		}
	}

	protected boolean isMethod(Element element) {
		return ((element != null) && ElementKind.METHOD.equals(element.getKind()));
	}

	protected void error(String message) {
		processingEnv.getMessager().printMessage(Kind.ERROR, message);
	}

}
