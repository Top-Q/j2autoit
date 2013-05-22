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

import java.util.Map;

/**
 * Interface is used to support both local and remote execution of AutoIt 
 *
 */
public interface AutoIt extends AutoItConstants{
	
	/**
	 * Run an AutoIt script
	 * 
	 * @param script	The script content string
	 * @param workDir	The directory to execute the script from
	 * @param autoItLocation	The location of the AutoIt executable file
	 * @param timeout Timeout for the script (miliseconds)
	 * @return	a Map of script execution results
	 * @throws Exception
	 */
	public Map<String, Comparable<?>> runScript(String script, String workDir, String autoItLocation,int timeout) throws Exception;	
	/**
	 * Retrieve a file from the FtpServer
	 * 
	 * @param user	FTP user
	 * @param password	FTP password
	 * @param host	FTP host address
	 * @param port	FTP port
	 * @param fileName	File to retrieve
	 * @param location	Location to put the file
	 * @return	
	 * @throws Exception
	 */
	public int getFile(String user, String password, String host,int port, String fileName, String location) throws Exception;
	
	/**
	 * Create a new file
	 * 
	 * @param fileName	The file name
	 * @param content	The content to put in the file
	 * @return
	 * @throws Exception
	 */
	public int createFile(String fileName, String content) throws Exception;
	
	/**
	 * Delete a directory\File
	 * 
	 * @param location Directory\File name
	 * @return
	 * @throws Exception
	 */
	public int deleteLocation(String location) throws Exception;
	
	/**
	 * Extract a zip file
	 * 
	 * @param filePath	The Zipped file location
	 * @param distDir	The directory to extract to
	 * @return
	 * @throws Exception
	 */
	public int unzipFile(String filePath, String distDir) throws Exception;
	
	/**
	 * Check if a file exists
	 * 
	 * @param fileName	The file name
	 * @return
	 * @throws Exception
	 */
	public boolean isFileExist(String fileName) throws Exception;
	
	/**
	 * Retrieve a system property
	 * 
	 * @param key	The system property key to search
	 * @return
	 * @throws Exception
	 */
	public String retrieveSystemProperty(String key) throws Exception;
	
	/**
	 * Get AutoIt location from the registry
	 * 
	 * @return	The AutoIt full path location
	 * @throws Exception
	 */
	public String revealAutoIt3Location() throws Exception;

	/**
	 * Kill all current running AutoIt processes
	 * 
	 * @return
	 * @throws Exception
	 */
	public int killAutoItProcess() throws Exception;

	/**
	 * Kill all instances of a given process name
	 * 
	 * @param processName The process to kill
	 * @return
	 * @throws Exception
	 */
	public int killProcess(String processName) throws Exception;

	/**
	 * Check if a process is still active
	 * 
	 * @param processName	The process to search
	 * @return
	 * @throws Exception
	 */
	public boolean isProcessStillActive(String processName) throws Exception;

	/**
	 * Check if AutoIt process is active
	 * 
	 * @return
	 * @throws Exception
	 */
	public boolean isAutoItActive() throws Exception;
	
	/**
	 * Perform computer shutdown operation
	 * @return exit value
	 * @throws Exception
	 */
	public int shutdownComputer(String switches) throws Exception;

	/**
	 * 
	 * Execute an AutoIt file with given timeout and parameters
	 * 
	 * @param fullPath	Script file full path
	 * @param workDir	The directory to execute the script from
	 * @param autoItLocation	The location of the AutoIt executable
	 * @param timeout	The timeout for the script execution in miliseconds
	 * @param params The script parameters
	 * @return
	 * @throws Exception
	 */
	public Map<String, Comparable<?>> executeAutoitFile(String fullPath, String workDir, String autoItLocation, int timeout, Object... params) throws Exception;
	
	/**
	 * By default, silent mode is off.<br>
	 * Setting silent mode to True, will disable all logging to console\logger
	 * 
	 * @param silentMode
	 * @return
	 * @throws Exception
	 */
	public int setSilentMode(boolean silentMode) throws Exception;
	
}
