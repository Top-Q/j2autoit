/**
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import jsystem.utils.FileUtils;
import jsystem.utils.exec.Command;
import jsystem.utils.exec.Execute;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.webserver.WebServer;

import com.jsystem.j2autoit.history.HistoryFile;
import com.jsystem.j2autoit.logger.Log;

public class AutoItAgent implements AutoIt {
	private static final String NEW_LINE = "\n";
	private static final String AUTOIT_REGISTRY_KEY = 
		"\"HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\AutoIt3{0}.exe\"";
	private static final Pattern PATTERN_EXTRACTING_AUTOIT_LOCATION = 
		Pattern.compile("[A-E]\\:(?:\\\\[a-zA-Z\\s0-9\\(\\)]{2,20}){0,6}\\\\[a-zA-Z\\s0-9_]{2,20}\\.[a-zA-Z\\s0-9]{2,20}");
	
	private static File agentWorkDir = new File(System.getProperty("user.dir"));
	private static WebServer webServer = null;
	private static Boolean serverState = true;
	private static final Integer DEFAULT_HistorySize = 1000;
	private static Boolean isAutoDeleteFiles = true;
	private static Integer webServicePort = 8888;
	private static Long shutDownTimeOut = 5000L;
	private static Boolean isForceAutoItShutDown = false;
	private static Boolean isDebug = false;
	private static Boolean isUseScreenShot = false;
	private static String autoIt_Location = null;

	/**
	 * Launch the server side
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Log.initLog();
			isDebug = AutoItProperties.DEBUG_MODE_KEY.getValue(isDebug);
			isAutoDeleteFiles = AutoItProperties.AUTO_DELETE_TEMPORARY_SCRIPT_FILE_KEY.getValue(isAutoDeleteFiles);
			if (isAutoDeleteFiles) {
				Integer tempHistory = AutoItProperties.AUTO_IT_SCRIPT_HISTORY_SIZE_KEY.getValue(DEFAULT_HistorySize);
				
				HistoryFile.init();
				HistoryFile.setHistory_Size(tempHistory);
			}
			isForceAutoItShutDown = AutoItProperties.FORCE_AUTO_IT_PROCESS_SHUTDOWN_KEY.getValue(isForceAutoItShutDown);
			webServicePort = AutoItProperties.AGENT_PORT_KEY.getValue(webServicePort);
			serverState = AutoItProperties.SERVER_UP_ON_INIT_KEY.getValue(serverState);
			
			Log.setLogMode(false, isDebug);
			Runtime.getRuntime().addShutdownHook(new ExitThread());
			Log.info(System.getProperty("user.dir") + NEW_LINE);
			Log.info("AutoIt Location: " + getAutoExecuterItLocation("Unable to find") + NEW_LINE);
			startAutoItWebServer(webServicePort);
			if (serverState) {
				serverState = false;
				runWebServer();
			}

			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

		} catch (Exception exception) {
			Log.throwable(exception.getMessage() + NEW_LINE, exception);
		}

		/* Turn off metal's use of bold fonts */
		UIManager.put("swing.boldMetal", Boolean.FALSE);
		//Schedule a job for the event-dispatching thread:
		//adding TrayIcon.
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				createAndShowGUI();
			}
		});
	}
	
	@Override
	public Map<String, Comparable<?>> runScript(String script, String workDir, String autoItLocation, int timeout) throws Exception{

		File sfile = File.createTempFile("autoit", ".au3"); //create autoit file

		StringBuffer buf = new StringBuffer();

		buf.append("Local $var = ").
		append(script).
		append(NEW_LINE).
		append("Local $rc = @error\nConsoleWrite($var)\nExit($rc)\n");

		FileOutputStream out = new FileOutputStream(sfile);
		out.write(buf.toString().getBytes("UTF-8"));
		out.close();

		Map<String, Comparable<?>> result = executeAutoitFile(sfile.getAbsolutePath(), workDir, autoItLocation, timeout, new Vector<Object>());

		return result;
	}

	public Map<String, Comparable<?>> executeAutoitFile(String fullPath, String workDir, String autoItLocation, int timeout, Vector<Object> params) {
		
		Exception threwOne = null;
		Hashtable<String, Comparable<?>> result = new Hashtable<String, Comparable<?>>();
		File sfile = new File(fullPath);
		if (!sfile.exists()) {
			System.out.println(agentWorkDir.getAbsolutePath());
			System.out.println("Couldn't find " + sfile);
			return result;
		}
		Command cmd = new Command();
		cmd.setTimeout(timeout);

		String[] commandParams = new String[3 + params.size()];
		commandParams[0] = getAutoExecuterItLocation(autoItLocation);;
		commandParams[1] = "/ErrorStdOut";
		commandParams[2]= sfile.getAbsolutePath();

		Log.info("Parameters:\n");
		for (int index = 0 ; index < params.size() ; index++) {
			Log.info(params.get(index).toString() + NEW_LINE);
			commandParams[index + 3] = params.get(index).toString();
		}
		
		cmd.setCmd(commandParams);
		File workingDirectory = new File(workDir);
		cmd.setDir(workingDirectory.exists()?workingDirectory:agentWorkDir);

		try {
			Execute.execute(cmd, true);
		} catch (Exception e) {
			threwOne = e;
		}

		Log.info(" \n");
		Log.info(" \n");

		String scriptText = "";
		try {
			scriptText = FileUtils.read(sfile);
			Pattern pattern = Pattern.compile("^Local \\$var = ([\\w\\d\\p{Punct} ]+)$",Pattern.MULTILINE);
			Matcher matcher = pattern.matcher(scriptText);
			if (matcher.find()) {
				Log.info("AutoIt Command : " + matcher.group(matcher.groupCount()) + NEW_LINE);
			}
		} catch (IOException ioException) {
			Log.throwable(ioException.getMessage(), ioException);
		}
		String stdoutText = cmd.getStdout().toString();
		int returnCodeValue = cmd.getReturnCode();
		String stderrText = cmd.getStderr().toString();

		if (isUseScreenShot || threwOne != null || !stderrText.isEmpty()) {
			String windowName = UUID.randomUUID().toString();
			new ScreenShotThread(windowName).start();
			Log.infoLog("A screenshot with the uuid : " + windowName + NEW_LINE);
		} 

		Log.messageLog(SCRIPT + ":\n" + scriptText + NEW_LINE);
		Log.messageLog(STDOUT + ":\n" + stdoutText + NEW_LINE);
		Log.messageLog(RETURN + ":\n" + returnCodeValue + NEW_LINE);
		Log.messageLog(STDERR + ":\n" + stderrText + NEW_LINE);


		result.put(SCRIPT, scriptText);
		result.put(STDOUT, stdoutText);
		result.put(RETURN, returnCodeValue);
		result.put(STDERR, stderrText);

		if (isDebug) {
			if (isAutoDeleteFiles) {
				HistoryFile.addFile(sfile);
				Log.infoLog("Adding " + sfile.getAbsolutePath() + " to \"For deletion files list\"\n");
			}
		} else {
			sfile.deleteOnExit();
		}
		
		return result;
	}

	@Override
	public int getFile(String user, String password, String host,int port, String fileName, String location) throws Exception{
		getFileFtp(user, password, host, port, fileName, location);
		return 0;
	}

	@Override
	public int createFile(String fileName, String content) throws IOException{	
		File outFile = new File(fileName);
		if (outFile.exists()) {
			outFile.delete();
		}
		FileWriter out = new FileWriter(outFile);
		out.write(content);
		out.close();
		return 0;
	}

	private static void getFileFtp(String user, String password, String host,int port, String fileName, String location) throws Exception{
		Log.info("\nretrieve " + fileName + NEW_LINE);
		FTPClient client = new FTPClient();

		client.connect(host, port); //connect to the management ftp server
		int reply = client.getReplyCode(); // check connection

		if (!FTPReply.isPositiveCompletion(reply)) {
			throw new Exception("FTP fail to connect");
		}

		if (!client.login(user, password)) {  //check login
			throw new Exception("FTP fail to login");
		}

		//		FileOutputStream fos = null;
		try {
			File locationFile = new File(location);
			File dest = new File(locationFile, fileName);
			if (dest.exists()) {
				dest.delete();
			} else {
				locationFile.mkdirs();
			}
			boolean status=client.changeWorkingDirectory("/");
			Log.info("chdir-status:" + status + NEW_LINE);
			client.setFileTransferMode(FTPClient.BINARY_FILE_TYPE);
			client.setFileType(FTPClient.BINARY_FILE_TYPE);
			client.enterLocalActiveMode(); 

			InputStream in = client.retrieveFileStream(fileName);
			if (in == null) {
				Log.error("Input stream is null\n");
				throw new Exception("Fail to retrieve file " + fileName);
			}
			Thread.sleep(3000);
			saveInputStreamToFile(in, new File(location, fileName));
		} finally {
			client.disconnect();
		}

	}
	
	public static void saveInputStreamToFile(InputStream in, File file) throws Exception {
		Log.info("Send file to " + file.getName() + NEW_LINE);
		if (file.getParentFile() != null) { 
			file.getParentFile().mkdirs();
		}
		FileOutputStream fos = new FileOutputStream(file);
		byte[] buf = new byte[4000];
		int c;
		while (true) {
			c = in.read(buf);
			if (c == -1) {
				break;
			}
			Log.info(".");
			fos.write(buf, 0, c);
		}
		fos.close();
	}

	@Override
	public int deleteLocation(String location) throws Exception{
		FileUtils.deltree(location);
		return 0;
	}

	@Override
	public int unzipFile(String filePath, String distDir) throws Exception{
		File dist = new File(distDir);
		if (dist.exists()) {
			FileUtils.deltree(dist);
		}
		dist.mkdirs();
		FileUtils.extractZipFile(new File(filePath), dist);
		return 0;
	}

	@Override
	public boolean isFileExist(String fileName) {
		File file = new File(fileName);
		return file.exists();
	}
	
	private static void createAndShowGUI() {
		//Check the SystemTray support
		if (!SystemTray.isSupported()) {
			Log.error("SystemTray is not supported\n");
			return;
		}
		final PopupMenu popup = new PopupMenu();
		final TrayIcon trayIcon =
			new TrayIcon(createImage("images/jsystem_ico.gif", "tray icon"));
		final SystemTray tray = SystemTray.getSystemTray();

		// Create a popup menu components
		final MenuItem aboutItem = new MenuItem("About");
		final MenuItem startAgentItem = new MenuItem("Start J2AutoIt Agent");
		final MenuItem stopAgentItem = new MenuItem("Stop J2AutoIt Agent");
		final MenuItem exitItem = new MenuItem("Exit");
		final MenuItem changePortItem = new MenuItem("Setting J2AutoIt Port");
		final MenuItem deleteTemporaryFilesItem = new MenuItem("Delete J2AutoIt Script");
		final MenuItem temporaryHistoryFilesItem = new MenuItem("History Size");
		final MenuItem displayLogFile = new MenuItem("Log");
		final MenuItem forceShutDownTimeOutItem = new MenuItem("Force ShutDown TimeOut");
		final MenuItem writeConfigurationToFile = new MenuItem("Save Configuration To File");
		final CheckboxMenuItem debugModeItem = new CheckboxMenuItem("Debug Mode", isDebug);
		final CheckboxMenuItem forceAutoItShutDownItem = new CheckboxMenuItem("Force AutoIt Script ShutDown", isForceAutoItShutDown);
		final CheckboxMenuItem autoDeleteHistoryItem = new CheckboxMenuItem("Auto Delete History", isAutoDeleteFiles);
		final CheckboxMenuItem useAutoScreenShot = new CheckboxMenuItem("Auto Screenshot", isUseScreenShot);

		//Add components to popup menu
		popup.add(aboutItem);
		popup.add(writeConfigurationToFile);
		popup.addSeparator();
		popup.add(forceAutoItShutDownItem);
		popup.add(forceShutDownTimeOutItem);
		popup.addSeparator();
		popup.add(debugModeItem);
		popup.add(displayLogFile);
		popup.add(useAutoScreenShot);
		popup.addSeparator();
		popup.add(autoDeleteHistoryItem);
		popup.add(deleteTemporaryFilesItem);
		popup.add(temporaryHistoryFilesItem);
		popup.addSeparator();
		popup.add(changePortItem);
		popup.addSeparator();
		popup.add(startAgentItem);
		popup.add(stopAgentItem);
		popup.addSeparator();
		popup.add(exitItem);


		trayIcon.setToolTip("J2AutoIt Agent");
		trayIcon.setImageAutoSize(true);
		trayIcon.setPopupMenu(popup);
		final DisplayLogFile displayLog = new DisplayLogFile();
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			Log.error("TrayIcon could not be added.\n");
			return;
		}

		trayIcon.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
			}
			@Override
			public void mouseEntered(MouseEvent e) {
			}
			@Override
			public void mouseExited(MouseEvent e) {
			}
			@Override
			public void mousePressed(MouseEvent e) {
				startAgentItem.setEnabled(!serverState);
				stopAgentItem.setEnabled(serverState);
				deleteTemporaryFilesItem.setEnabled(isDebug && HistoryFile.containEntries());
				autoDeleteHistoryItem.setEnabled(isDebug);
				temporaryHistoryFilesItem.setEnabled(isDebug && isAutoDeleteFiles);
				displayLogFile.setEnabled(isDebug);
				forceShutDownTimeOutItem.setEnabled(isForceAutoItShutDown);
			}
			@Override
			public void mouseReleased(MouseEvent e) {
			}
		});

		writeConfigurationToFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				AutoItProperties.DEBUG_MODE_KEY.setValue(isDebug.toString());
				AutoItProperties.AUTO_DELETE_TEMPORARY_SCRIPT_FILE_KEY.setValue(isAutoDeleteFiles.toString());
				AutoItProperties.AUTO_IT_SCRIPT_HISTORY_SIZE_KEY.setValue(DEFAULT_HistorySize.toString());
				AutoItProperties.FORCE_AUTO_IT_PROCESS_SHUTDOWN_KEY.setValue(isForceAutoItShutDown.toString());
				AutoItProperties.AGENT_PORT_KEY.setValue(webServicePort.toString());
				AutoItProperties.SERVER_UP_ON_INIT_KEY.setValue(serverState.toString());
				if (!AutoItProperties.savePropertiesFileSafely()) {
					Log.error("Fail to save properties file");
				}
			}
		});

		debugModeItem.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				isDebug = (e.getStateChange() == ItemEvent.SELECTED);
				deleteTemporaryFilesItem.setEnabled(isDebug && HistoryFile.containEntries());
				Log.infoLog("Keeping the temp file is " + (isDebug ? "en" : "dis") + "able\n");
				trayIcon.displayMessage((isDebug ? "Debug" : "Normal") + 
						" Mode", "The system will " + 
						(isDebug ? "not " : "") + 
						"delete \nthe temporary autoIt scripts." + 
						(isDebug ? "\nSee log file for more info." : ""), MessageType.INFO);
			}
		});

		useAutoScreenShot.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				isUseScreenShot = (e.getStateChange() == ItemEvent.SELECTED);
				Log.infoLog("Auto screenshot is " + (isUseScreenShot ? "" : "in") + "active\n");
			}
		});

		forceAutoItShutDownItem.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				isForceAutoItShutDown = (e.getStateChange() == ItemEvent.SELECTED);
				Log.infoLog((isForceAutoItShutDown ? "Force" : "Soft") + " AutoIt Script ShutDown\n");
				trayIcon.displayMessage("AutoIt Script Termination",(isForceAutoItShutDown ? "Hard shutdown" : "Soft shutdown"), MessageType.INFO);
			}
		});

		autoDeleteHistoryItem.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				isAutoDeleteFiles = (e.getStateChange() == ItemEvent.SELECTED);
				Log.infoLog((isAutoDeleteFiles ? "Auto" : "Manual") + " AutoIt Script Deletion\n");
				trayIcon.displayMessage("AutoIt Script Deletion",(isAutoDeleteFiles ? "Auto" : "Manual") + " Mode", MessageType.INFO);
				if (isAutoDeleteFiles) {
					HistoryFile.init();
				} else {
					HistoryFile.close();
				}
			}
		});

		forceShutDownTimeOutItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				String timeOutAsString = JOptionPane.showInputDialog("Please Insert Force ShutDown TimeOut (in seconds)", String.valueOf(shutDownTimeOut / 1000));
				try {
					shutDownTimeOut = 1000 * Long.parseLong(timeOutAsString);
				} catch (Exception e2) {
				}
				Log.infoLog("Setting the force shutdown time out to : " + (shutDownTimeOut / 1000) + " seconds.\n");
			}
		});

		displayLogFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				try {
					displayLog.reBuild(Log.getCurrentLogs());
					displayLog.actionPerformed(actionEvent);
				} catch (Exception e2) {
				}
			}
		});

		temporaryHistoryFilesItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				String historySize = JOptionPane.showInputDialog("Please Insert History AutoIt Script Files", String.valueOf(HistoryFile.getHistory_Size()));
				try {
					int temp = Integer.parseInt(historySize);
					if (temp > 0) {
						if (HistoryFile.getHistory_Size() != temp) { 
							HistoryFile.setHistory_Size(temp);
							Log.infoLog("The history files size is " + historySize + NEW_LINE);
						}
					} else {
						Log.warning("Illegal History Size: " + historySize + NEW_LINE);
					}
				} catch (Exception exception) {
					Log.throwableLog(exception.getMessage(), exception);
				}
			}
		});

		deleteTemporaryFilesItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				HistoryFile.forceDeleteAll();
			}
		});

		startAgentItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				runWebServer();
			}
		});

		stopAgentItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				shutDownWebServer();
			}
		});

		aboutItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				JOptionPane.showMessageDialog(null, "J2AutoIt Agent By JSystem And Aqua Software");
			}
		});

		changePortItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				String portAsString = JOptionPane.showInputDialog("Please Insert new Port", String.valueOf(webServicePort));
				if (portAsString != null) {
					try {
						int temp = Integer.parseInt(portAsString);
						if (temp > 1000) {
							if (temp != webServicePort) {
								webServicePort = temp;
								shutDownWebServer();
								startAutoItWebServer(webServicePort);
								runWebServer();
							}
						} else {
							Log.warning("Port number should be greater then 1000\n");
						}
					} catch (Exception exception) {
						Log.error("Illegal port number\n");
						return;
					}
				}
			}
		});

		exitItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				tray.remove(trayIcon);
				shutDownWebServer();
				Log.info("Exiting from J2AutoIt Agent\n");
				System.exit(0);
			}
		});
	}

	//Obtain the image URL
	protected static Image createImage(String path, String description) {
		URL imageURL = AutoItAgent.class.getResource(path);

		if (imageURL == null) {
			Log.error("Resource not found: " + path + NEW_LINE);
			return null;
		} else {
			return (new ImageIcon(imageURL, description)).getImage();
		}
	}

	private static void startAutoItWebServer(int port) {
		
		if (webServer != null) {
			webServer = null;
			System.gc();
		}
		InetAddress addr = null;
		try {
			addr = InetAddress.getLocalHost();
			webServer = new WebServer(port, addr);
			Log.info("Setting J2AutoIt Agent to use address: " + addr.getHostAddress() + ":" + port + NEW_LINE);
		} catch (Exception exception) {
			webServer = new WebServer(port);
			Log.info("Setting J2AutoIt Agent to use port: " + port + NEW_LINE);
		}
		try {
			PropertyHandlerMapping phm = new PropertyHandlerMapping();
			phm.addHandler("autoit", AutoItAgent.class);
			XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();
			xmlRpcServer.setHandlerMapping(phm);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	private static void shutDownWebServer() {
		if (serverState) {
			serverState = false;
			webServer.shutdown();
			Log.info("J2AutoIt Agent Stopped\n");
		}
	}
	private static void runWebServer() {
		if (!serverState) {
			serverState = true;
			try {
				webServer.start();
			}catch (Exception e) {
				throw new RuntimeException(e);
			}
			Log.info("J2AutoIt Agent Started\n");
		}
	}

	@Override
	public String revealAutoIt3Location() throws Exception{
		return getAutoExecuterItLocation("Unable to find autoIt location");
	}
	
	private static String getAutoExecuterItLocation(String defaulValue) {
		if (autoIt_Location == null || autoIt_Location.trim().isEmpty()) {
			String autoIt32 = MessageFormat.format(AUTOIT_REGISTRY_KEY, "");
			String autoIt64 = MessageFormat.format(AUTOIT_REGISTRY_KEY, "_x64");
			for (String currentKey : new String[] {autoIt32, autoIt64}) {
				try {
					return autoIt_Location = extractLocation(currentKey);
				} catch (Exception exception) {
				}
			}
			return defaulValue;
		}
		return autoIt_Location;
	}

	private static String extractLocation(String regkey) throws Exception {
		String cmd = "reg query " + regkey + " /ve";
		Process child = Runtime.getRuntime().exec(cmd);
		child.waitFor();
		BufferedReader br = new BufferedReader(new InputStreamReader(child.getInputStream()));
		StringBuffer sb = new StringBuffer("");
		String line = null;
		while ((line = br.readLine()) != null) {
			sb.append(line).append(NEW_LINE);
		}		
		Matcher mat = PATTERN_EXTRACTING_AUTOIT_LOCATION.matcher(sb.toString());
		if (mat.find()) {
			return mat.group(mat.groupCount()).trim();
		} else {
			throw new Exception("Unable to find AutoIt Location");
		}
	}

	@Override
	public int killProcess(String image) {
		try {
			Thread.sleep(shutDownTimeOut);
			Process taskKillProcess = Runtime.getRuntime().exec("TASKKILL /F /IM " + image);
			Log.infoLog("Forcing AutoIt Script Shutdown!!\n");
			StringBuilder sb = new StringBuilder("");
			BufferedReader br = new BufferedReader(new InputStreamReader(taskKillProcess.getInputStream()));
			String line = null;
			while((line = br.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					sb.append(line).append(NEW_LINE);
				}
			}
			if (sb.length() > 0) {
				Log.infoLog(sb.toString());
			} else {
				br = new BufferedReader(new InputStreamReader(taskKillProcess.getErrorStream()));
				line = null;
				while((line = br.readLine()) != null) {
					if (!line.trim().isEmpty()) {
						sb.append(line).append(NEW_LINE);
					}
				}
				Log.errorLog(sb.toString());
			}
		} catch (Exception e) {
			Log.errorLog(e.getMessage());
		}
		return 0;
	}

	@Override
	public int killAutoItProcess() {
		killProcess("AutoIt3.exe");
		return 0;
	}

	@Override
	public int shutdownComputer(String switches) throws Exception {
		String command = "shutdown " + switches;
		Process process = Runtime.getRuntime().exec(command);
		return process.waitFor();
	}
	
	@Override
	public boolean isAutoItActive() throws Exception{
		return isProcessStillActive("AutoIt3.exe");
	}

	@Override
	public boolean isProcessStillActive(String processName) throws Exception{
		String cmd = "TASKLIST.EXE /FO CSV /NH /FI \"IMAGENAME eq " + processName +"\"";
		Process child = Runtime.getRuntime().exec(cmd);
		Log.infoLog("Running the command line : " + cmd + NEW_LINE);
		return readFromStream("ErrorStream : ", child.getErrorStream()) && 
						readFromStream("InputStream : ", child.getInputStream());
	}
	
	private boolean readFromStream(String location, InputStream inputStream) throws Exception{
		BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
		StringBuilder sb = new StringBuilder("");
		String line = null;
		while((line = br.readLine()) != null) {
			if (line.trim().toLowerCase().contains("no tasks")) {
				Log.infoLog(location + line + NEW_LINE);
				br.close();
				return false;
			} else {
				sb.append(line).append(NEW_LINE);
			}
		}
		Log.infoLog(location + sb.toString());
		sb = null;
		br.close();
		return true;
	}

	@Override
	public String retrieveSystemProperty(String key) throws Exception{
		return System.getProperty(key);
	}

	@Override
	public int setSilentMode(boolean silentMode) {
		Log.setLogMode(silentMode, isDebug);
		return 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Comparable<?>> executeAutoitFile(String fullPath, String workDir, String autoItLocation, int timeout, Object... params) throws Exception {
		Vector<Object> parameters = new Vector<Object>();
		if (params.length == 1 && params[0] instanceof Vector) {
			parameters = (Vector<Object>) params[0];
		} else {
			for (Object param : params) {
				parameters.add(param);
			}
		}
		return executeAutoitFile(fullPath, workDir, autoItLocation, timeout, parameters);
	}
	
}

class ExitThread extends Thread {
	
	public ExitThread() {
	}
	
	@Override
	public void run() {
		try {
			HistoryFile.close();
		} catch (Exception e) {
		}
		try {
			Log.closeLog();
		} catch (Exception e) {
		}
	}
	
}

class DisplayLogFile extends JFrame implements ActionListener {
	private static final long serialVersionUID = 2092378207577576792L;
	JTextArea _resultArea = new JTextArea(25,40);
	Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
	private int x = (int)(double) dim.getWidth() * 2/3;
	private int y = (int)(double) dim.getHeight() * 5/11;

	public DisplayLogFile() {
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		reBuild("");
	}
	
	public void reBuild(String text) {
		reBuild("J2AutoIt Log Buffer", text);
	}
	
	public void reBuild(String title,String text) {
		_resultArea.setText(text);
		_resultArea.setEditable(false);
		JScrollPane scrollingArea = new JScrollPane(_resultArea);
		JPanel content = new JPanel();
		content.setLayout(new BorderLayout());
		content.add(scrollingArea, BorderLayout.CENTER);
		this.setTitle(title);
		this.setLocation(x, y);
		this.setContentPane(content);
		this.pack();
	}

	@Override
	public void actionPerformed(ActionEvent actionEvent) {
		setVisible(true);
	}
}

class ScreenShotThread extends Thread {
	private static final Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
	private File theImageFile = null;
	
	public ScreenShotThread(String imageID) {
		File parent = new File("snapshots");
		parent.mkdir();
		theImageFile = new File(parent, "Snapshot_" + imageID + ".png");
	}
	
	@Override
	public void run() {
		try {
			Robot robot = new Robot();
			BufferedImage bufferedImage = robot.createScreenCapture(screenRect);
			ImageIO.write(bufferedImage, "png", theImageFile);
			Log.infoLog("Created image file : " + theImageFile.getAbsolutePath() + "\n");
			robot = null;
		} catch (Exception exception) {
			Log.throwable(exception.getMessage(), exception);
		}
	}
}
