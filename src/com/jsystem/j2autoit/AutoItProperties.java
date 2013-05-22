/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
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
package com.jsystem.j2autoit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import jsystem.utils.FileUtils;

import com.jsystem.j2autoit.logger.Log;

/**
 * @author Kobi Gana
 *
 */
public enum AutoItProperties {
	
	AUTOIT_PROPERTIES_FILE_NAME("autoit.properties"),
	DEBUG_MODE_KEY("debugMode"),
	AUTO_DELETE_TEMPORARY_SCRIPT_FILE_KEY("autoDeleteTemporaryScriptFile"),
	AUTO_IT_SCRIPT_HISTORY_SIZE_KEY("autoItScriptHistorySize"),
	FORCE_AUTO_IT_PROCESS_SHUTDOWN_KEY("forceAutoItProcessShutdown"),
	AGENT_PORT_KEY("agentPort"),
	SERVER_UP_ON_INIT_KEY("serverUpOnInit");
	protected static Properties properties = new Properties();
	static{
		try {
			File file = new File(AUTOIT_PROPERTIES_FILE_NAME.getKey());
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				properties.load(fis);
			}
		} catch (Exception e) {
		}
	}
	private String key = null;
	AutoItProperties(String key){
		this.key = key;
	}
	public String getKey() {
		return key;
	}

	@SuppressWarnings("unchecked")
	public <E> E getValue(E e) {
		try {
			Method method = e.getClass().getMethod("valueOf", String.class);
			Object object = properties.get(getKey());
			return (E) method.invoke(null, object == null ? e.toString() : object.toString());
		} catch (Exception e1) {
		}
		return null;
	}
	
	public void setValue(Object object) {
		properties.put(getKey(), object);
	}
	
	public static boolean savePropertiesFileSafely() {
		File currentFile = new File(AUTOIT_PROPERTIES_FILE_NAME.getKey());
		File tempFile = null;
		if (currentFile.exists()) {
			tempFile = new File(AUTOIT_PROPERTIES_FILE_NAME.getKey() + ".temp");
			try {
				FileUtils.copyFile(currentFile, tempFile);
			} catch (Exception exception) {
				Log.throwableLog(exception.getMessage(), exception);
				tempFile.delete();
				return false;
			}
			currentFile.delete();
		}
		try {
			currentFile.createNewFile();
		} catch (Exception exception) {
			Log.throwableLog(exception.getMessage(), exception);
			if (tempFile != null) {
				try {
					FileUtils.copyFile(tempFile, currentFile);
				} catch (Exception exception2) {
					Log.throwableLog(exception2.getMessage(), exception2);
					return false;
				}
				tempFile.delete();
			}
			return false;
		}
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(currentFile);
			properties.store(fileOutputStream, null);
		} catch (Exception exception1) {
			if (tempFile != null) {
				try {
					currentFile.delete();
					FileUtils.copyFile(tempFile, currentFile);
				} catch (Exception exception3) {
					Log.throwableLog(exception3.getMessage(), exception3);
					return false;
				}
				tempFile.delete();
			}
		} finally {
			if (fileOutputStream != null) {
				try {
					fileOutputStream.close();
				} catch (Exception exception2) {
					Log.throwableLog(exception2.getMessage(), exception2);
					if (tempFile != null) {
						try {
							FileUtils.copyFile(tempFile, currentFile);
						} catch (Exception exception3) {
							Log.throwableLog(exception3.getMessage(), exception3);
							return false;
						}
						tempFile.delete();
					}
					return false;
				}
			}
			if (tempFile != null) {
				tempFile.delete();
			}
		}
		return true;
	}
}
