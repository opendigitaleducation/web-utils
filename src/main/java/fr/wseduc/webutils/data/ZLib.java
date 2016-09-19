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


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ZLib {

	public static byte[] compress(byte[] data) throws IOException {
		Deflater deflater = new Deflater();
		return deflaterProcess(data, deflater);
	}

	public static byte[] deflate(byte[] data) throws IOException {
		Deflater deflater = new Deflater(Deflater.DEFLATED, true);
		return deflaterProcess(data, deflater);
	}

	private static byte[] deflaterProcess(byte[] data, Deflater deflater) throws IOException {
		deflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		deflater.finish();
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			int count = deflater.deflate(buffer);
			outputStream.write(buffer, 0, count);
		}
		outputStream.close();
		return outputStream.toByteArray();
	}

	public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
		Inflater inflater = new Inflater();
		inflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		byte[] buffer = new byte[1024];
		while (!inflater.finished()) {
			int count = inflater.inflate(buffer);
			if (count > 0) {
				outputStream.write(buffer, 0, count);
			} else {
				break;
			}
		}
		outputStream.close();
		inflater.end();
		return outputStream.toByteArray();
	}

	public static String compressAndEncode(String data) throws IOException {
		return Base64.getEncoder().encodeToString(compress(data.getBytes()));
	}

	public static String deflateAndEncode(String data) throws IOException {
		return Base64.getEncoder().encodeToString(deflate(data.getBytes()));
	}

}
