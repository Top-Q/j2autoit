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

import java.net.URL;
import java.util.Map;
import java.util.Vector;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;

import com.aqua.filetransfer.ftp.FTPServer;

/**
 * A Mediator for sending xml-rpc requests to remote Autoit Agent
 * 
 * @author Nizan Freedman
 *
 */
public class AutoItRemoteInvoker implements AutoIt {

	private FTPServer ftps;
	String host = "127.0.0.1";
	int port = 8888;
	
	public AutoItRemoteInvoker(String host, int port, FTPServer ftps){
		super();
		this.host = host;
		this.port = port;
		this.ftps = ftps;
	}
	
	@Override
	public int createFile(String fileName, String content) throws Exception {
		execute("createFile", fileName, content);
		return 0;
	}

	@Override
	public int deleteLocation(String location) throws Exception {
		execute("deleteLocation", location);
		return 0;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Comparable<?>> executeAutoitFile(String fullPath,
			String workDir, String autoItLocation, int timeout,
			Vector<Object> params) throws Exception {
		return (Map<String, Comparable<?>>) execute("executeAutoitFile", fullPath, workDir, autoItLocation, timeout, params);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Comparable<?>> executeAutoitFile(String fullPath,
			String workDir, String autoItLocation, int timeout,
			Object... params) throws Exception {
		Vector<Object> parameters = new Vector<Object>();
		if (params.length == 1 && params[0] instanceof Vector){
			parameters = (Vector<Object>) params[0];
		}else{
			for (Object param : params){
				parameters.add(param);
			}
		}
		return executeAutoitFile(fullPath, workDir, autoItLocation, timeout, parameters);
	}

	@Override
	public int getFile(String user, String password, String host, int port,
			String fileName, String location) throws Exception {
		execute("getFile", ftps.getDefaultUserName(),ftps.getDefaultUserPassword(),host,port,fileName,location);
		return 0;
	}

	@Override
	public boolean isAutoItActive() throws Exception {
		Object obj = execute("isAutoItActivate");
		return Boolean.valueOf(obj.toString());
	}

	@Override
	public boolean isFileExist(String fileName) throws Exception {
		boolean res = (Boolean) execute("isFileExist", fileName);
		return res;
	}

	@Override
	public boolean isProcessStillActive(String processName) throws Exception {
		Object obj = execute("isProcessStillActive", processName);
		return Boolean.valueOf(obj.toString());
	}

	@Override
	public int killAutoItProcess() throws Exception {
		execute("killAutoItProcess");
		return 0;
	}

	@Override
	public int killProcess(String processName) throws Exception {
		execute("killProcess", processName);
		return 0;
	}

	@Override
	public String retrieveSystemProperty(String key) throws Exception {
		Object obj = execute("retrieveSystemProperty", key);
		return obj.toString();
	}

	@Override
	public String revealAutoIt3Location() throws Exception {
		Object obj = execute("revealAutoIt3Location");
		return obj.toString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Comparable<?>> runScript(String script, String workDir, String autoItLocation,int timeout) throws Exception{
	    return (Map<String, Comparable<?>>) execute("runScript", script, workDir, autoItLocation, timeout);
	}

	@Override
	public int unzipFile(String filePath, String distDir) throws Exception {
		execute("unzipFile", filePath, distDir);
		return 0;
	}
	
	/**
	 * creates an XmlRpcClient and calls it's execute method to connect to a
	 * server
	 * 
	 * @param command
	 * @param params
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public Object execute(String command, Object...objects) throws Exception {
		command = "autoit." + command;
		Vector<Object> params = new Vector<Object>();
		if (objects.length==1 && objects[0] instanceof Vector<?>){
			params = (Vector<Object>) objects[0];
		}else{
			for (Object object : objects){
				params.add(object);
			}
		}
		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL(new StringBuilder().append("http://").append(host).append(":").append(port).append("/RPC2").toString()));
		XmlRpcClient client = new XmlRpcClient();
		client.setTransportFactory(new XmlRpcCommonsTransportFactory(client));
		client.setConfig(config);
		config.setEnabledForExtensions(true);
		Object returnedObject = client.execute(command, params);
		if (returnedObject instanceof Exception) {
			throw (Exception) returnedObject;
		}
		return returnedObject;	
	}

	@Override
	public int shutdownComputer(String switches) throws Exception {
		Object obj = execute("shutdownComputer", switches);
		return Integer.parseInt(obj.toString());
	}

	@Override
	public int setSilentMode(boolean silentMode) throws Exception {
		Object obj = execute("setSilentMode", silentMode);
		return Integer.parseInt(obj.toString());
	}

}
