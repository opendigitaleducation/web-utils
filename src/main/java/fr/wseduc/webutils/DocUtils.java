package fr.wseduc.webutils;

import java.io.*;

public class DocUtils {

    public static void swaggerDoc(Object ob) {
        ClassLoader classLoader = ob.getClass().getClassLoader();
        File folder = new File(classLoader.getResource("jsonschema").getFile());
        File swagger = new File(classLoader.getResource("Swagger-doc.json").getFile());
        try {
            OutputStream outputStream = new FileOutputStream(swagger, true);
            StringBuilder sb = new StringBuilder();
            sb.append(",\"definitions\":{");
            String line;
            for (final File fileEntry : folder.listFiles()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(fileEntry), "UTF-8"));
                sb.append("\""+fileEntry.getName()+"\":");
                while((line = r.readLine()) != null) {
                    sb.append(line);
                }
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("}}");
            outputStream.write(sb.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
