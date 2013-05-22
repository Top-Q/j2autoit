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

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import jsystem.framework.system.SystemObjectImpl;
import jsystem.utils.FileUtils;
import jsystem.utils.StringUtils;

import com.aqua.filetransfer.ftp.FTPServer;

public class AutoItClient extends SystemObjectImpl implements AutoItConstants {

	private static final int DEFAULT_TIME_OUT = 30;

	private static final String FTP_HOME_DIR = "c:\\ftpserver";

	String host = "127.0.0.1";
	private String name = "";
	int port = 8888;
	String autoItLocation;
	String workDir = System.getProperty("user.dir");

	private static FTPServer ftps;
	private static int counter = 0;
	private static int ftpPort = 9000;
	private Process serverProcess;
	private int scriptTimeout = DEFAULT_TIME_OUT;

	/**
	 * Direct connection to agent in case running without the XML-RPC
	 */
	private AutoIt agent;
	/**
	 * if set to True will not use an XML-RPC connection and execute the agent
	 * directly
	 */
	boolean runAgentDirectly = false;

	private boolean silentMode = false;

	public AutoItClient() {
		super();
	}

	public AutoItClient(String name) {
		this();
		this.name = name;
	}

	public AutoItClient(Process serverProcess, String host, int port) {
		this();
		this.serverProcess = serverProcess;
		this.port = port;
		this.host = host;
	}

	private static synchronized void launchFtpServer() throws Exception {
		if (ftps != null) {
			return;
		}
		ftps = new FTPServer();
		File ftpHomeDir = new File(FTP_HOME_DIR);
		if (!ftpHomeDir.exists()) {
			ftpHomeDir.mkdir();
		}
		ftps.setDefaultUserHomeDirectory(FTP_HOME_DIR);
		ftps.setPort(ftpPort);
		ftps.init();
		ftps.startServer();
	}

	@Override
	public synchronized void init() throws Exception {
		super.init();
		counter++;
		if (isRunAgentDirectly()) {
			agent = new AutoItAgent();
		} else {
			try {
				launchFtpServer();
			} catch (Exception exception) {
				exception.printStackTrace();
			}
			agent = new AutoItRemoteInvoker(host, port, ftps);
		}
		try {
			agent.setSilentMode(silentMode);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	@Override
	public void close() {
		counter--;
		if (counter == 0) {
			try {
				if (ftps != null) {
					ftps.stopServer();
				}
			} catch (Exception exception) {
				report.report("Failed to close FTP server");
				exception.printStackTrace();
			}
		}
		super.close();
	}

	/**
	 * run script on the remote machine with default 30 seconds timeout
	 * 
	 * @param script
	 * @return
	 * @throws Exception
	 */
	public Map<String, Comparable<?>> runRemoteScript(String script) throws Exception {
		return runRemoteScript(script, scriptTimeout);
	}

	/**
	 * run script on the remote machine with timeout
	 * 
	 * @param script
	 * @param timeout
	 *            Timeout in seconds
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Comparable<?>> runRemoteScript(String script, int timeout) throws Exception {
		Object returnedObject = agent.runScript(script, workDir, autoItLocation, timeout * 1000);
		if (returnedObject instanceof Exception) {
			throw (Exception) returnedObject;
		} else if (returnedObject instanceof Map<?, ?>) {
			String stderr = (String) (((Map<?, ?>) returnedObject).get(STDERR));
			if (!stderr.isEmpty()) {
				throw new TimeoutException();
			}
			return (Map<String, Comparable<?>>) returnedObject;
		} else {
			return null;
		}
	}

	/**
	 * Add quote to the var.
	 * 
	 * @param toQuote
	 * @return
	 */
	private static String quote(String toQuote) {
		return "\"" + toQuote + "\"";
	}

	/**
	 * Create the autoit command
	 * 
	 * @param command
	 *            - The command as it is written in autoit.
	 * @param vars
	 * @return
	 */
	public static String commandCreate(String command, Object... vars) {
		// ITAI: I removed the method commandCreate(String,boolean,Object...)
		// because java 7 is more strict regarding ambiguous methods and we had
		// problems compiling the project.

		StringBuffer buf = new StringBuffer();
		boolean isQuote = true;
		int firstIndex = 0;
		if (vars[0] instanceof Boolean) {
			isQuote = (Boolean) vars[0];
			firstIndex = 1;
		}
		buf.append(command);
		buf.append("(");
		for (int index = firstIndex; index < vars.length; index++) {
			if (index != firstIndex) {
				buf.append(", ");
			}
			if (vars[index] instanceof Integer) {
				buf.append(vars[index].toString());
			} else {
				if (isQuote) {
					buf.append(quote(vars[index].toString()));
				} else {
					buf.append(vars[index].toString());
				}

			}
		}
		buf.append(")");
		return buf.toString();

	}

	/**
	 * Sends a file to a remote machine
	 * 
	 * @param file
	 * @param remoteLocation
	 * @param managementIpAddress
	 * @throws Exception
	 */
	public synchronized void sendFile(File file, String remoteLocation, String managementIpAddress) throws Exception {
		if (runAgentDirectly) {
			FileUtils.copyFile(file, new File(remoteLocation, file.getName())); // copy
																				// file
																				// to
																				// the
																				// root
																				// folder
																				// of
																				// the
																				// ftp
																				// server
		} else {
			FileUtils.copyFile(file, new File(FTP_HOME_DIR, file.getName())); // copy
																				// file
																				// to
																				// the
																				// root
																				// folder
																				// of
																				// the
																				// ftp
																				// server
			report.report("Address : " + managementIpAddress);
			agent.getFile(ftps.getDefaultUserName(), ftps.getDefaultUserPassword(), managementIpAddress, ftpPort,
					file.getName(), remoteLocation);
		}
	}

	/**
	 * create file in the remote machine.
	 * 
	 * @param fileName
	 * @param content
	 * @throws Exception
	 */
	public synchronized void createFile(String fileName, String content) throws Exception {
		agent.createFile(fileName, content);
	}

	public void deleteLocation(String location) throws Exception {
		agent.deleteLocation(location);
	}

	/**
	 * Unzip file in the remote machine.
	 */
	public void unzipFile(String file, String dir) throws Exception {
		agent.unzipFile(file, dir);
	}

	public Process getServerProcess() {
		return serverProcess;
	}

	public void setServerProcess(Process serverProcess) {
		this.serverProcess = serverProcess;
	}

	public void screenCapture() throws Exception {
		BufferedImage screencapture = new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit()
				.getScreenSize()));

		// Save as JPEG
		File file = new File(report.getCurrentTestFolder(), "screencapture.jpg");
		ImageIO.write(screencapture, "jpg", file);
		report.addLink("Screen capture", "screencapture.jpg");

	}

	public void sendCmd(String cmd) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("Run", false, cmd));
		processResult("Wait for windows active, title: ", result);
		System.out.println(result.get(STDOUT));
	}

	/**
	 * Minimizes all windows.<br>
	 * <b>Remarks:</b><br>
	 * Send("#m") is a possible alternative.<br>
	 * 
	 * @throws Exception
	 */
	public void winMinimizeAll() throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinMinimizeAll"));
		processResult("Minimize all Windows: ", result);
	}

	/**
	 * Undoes a previous WinMinimizeAll function.<br>
	 * <b>Remarks:</b><br>
	 * Send("#+m") is a possible alternative.<br>
	 * 
	 * @throws Exception
	 */
	public void winMinimizeAllUndo() throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinMinimizeAllUndo"));
		processResult("Undoes a previous win minimize all function: ", result);
	}

	/**
	 * 
	 * @throws Exception
	 */
	public String[] winGetPos(String title, String internalText) throws Exception {
		if (!winExists(title, internalText)) {
			return null;
		}

		String cmd = "WinGetPos(\"" + title + "\",\"" + internalText
				+ "\")\nConsoleWrite($var[0] & \" \" & $var[1] & \" \" & $var[2] & \" \" & $var[3])";
		Map<?, ?> result = runRemoteScript(cmd);
		processResult("Wait for windows active, title: ", result);
		String posArray[] = result.get(STDOUT).toString().split(" ");
		return posArray;
	}

	/**
	 * Get All Autoit classes of a specific window title
	 * 
	 * @param title
	 */
	public String[] winGetClassList(String title) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinGetClassList", title));
		processResult("Get autoit class list of specific window, title: ", result);
		String posArray[] = result.get(STDOUT).toString().split("\n");
		return posArray;
	}

	public void setWindowFocus(String title, String internalText, int numberOfTimeToWait) throws Exception {
		for (int i = 0; i < numberOfTimeToWait; i++) {
			if (winWaitActive(title, internalText, 2)) {
				break;
			}
			send("!{TAB}");
		}

	}

	/**
	 * set window to be on top
	 * 
	 * Return Value
	 * 
	 * Success: Returns 1. Failure: Returns 0 if window is not found.
	 * 
	 * @param title
	 *            - The title of the window to affect. See Title special
	 *            definition.
	 * @param internalText
	 *            - text The text of the window to affect.
	 * @param flag
	 *            - flag Determines whether the window should have the "TOPMOST"
	 *            flag set. 1=set on top flag, 0 = remove on top flag
	 */
	public void winSetOnTop(String title, String internalText, int flag) throws Exception {
		processResult("Set the next window title : " + title + " on top",
				runRemoteScript(commandCreate("WinSetOnTop", title, internalText, flag)));
	}

	/**
	 * prints the results of the script command
	 * 
	 * @param title
	 * @param result
	 */
	private void processResult(String title, Map<?, ?> result) {
		report.report(name + " " + title, (String) result.get(SCRIPT), true);
	}

	/**
	 * Disable/enable the mouse and keyboard.<br>
	 * <b>Remarks:</b><br>
	 * If BlockInput is enabled, the Alt keypress cannot be sent!<br>
	 * The table below shows how BlockInput behavior depends on the Windows
	 * version; however, pressing Ctrl+Alt+Del on any platform will re-enable
	 * input due to a Windows API feature.
	 * <table border="1">
	 * <tr>
	 * <th>Operating System</th>
	 * <th>"BlockInput" Results</th>
	 * </tr>
	 * <tr>
	 * <td>Windows 2000</td>
	 * <td>User input is blocked and AutoIt can simulate most input.</td>
	 * </tr>
	 * <tr>
	 * <td>Windows XP</td>
	 * <td>User input is blocked and AutoIt can simulate most input. See
	 * exceptions below.</td>
	 * </tr>
	 * <tr>
	 * <td>Windows Vista</td>
	 * <td>User input is blocked and AutoIt can simulate most input if
	 * #requireAdmin is used. See exceptions below.</td>
	 * </tr>
	 * </table>
	 * <br>
	 * If you are using Windows XP then you should be aware that a hotfix
	 * released in between SP1 and SP2 limited Blockinput so that the ALT key
	 * could NOT be sent. This was fixed in XP SP2.<br>
	 * <br>
	 * Note that functions such as WinMove() or Send() will still work when
	 * BlockInput is enabled because BlockInput just affects user interaction
	 * with the keyboard or the mouse not what is done with AutoIt functions
	 * (aside from the exceptions in the table above).<br>
	 * 
	 * @param flag
	 *            <ul>
	 *            <li>false - Enable user input</li>
	 *            <li>true - Disable user input</li>
	 *            </ul>
	 * @return true if succeed
	 * @throws Exception
	 */
	public boolean blockInput(boolean flag) throws Exception {
		Map<String, Comparable<?>> result = runRemoteScript(commandCreate("BlockInput", (flag ? 1 : 0)));
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * Checks if the current user has full administrator privileges.
	 * 
	 * @return <b>true</b> if the current user has administrator privileges.<br>
	 *         <b>false</b> if user lacks admin privileges.
	 * @throws Exception
	 */
	public boolean isAdmin() throws Exception {
		Map<String, Comparable<?>> result = runRemoteScript(commandCreate("IsAdmin"));
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * Pings a host and returns the roundtrip-time.
	 * 
	 * @param host
	 *            - Can be i.e. "www.autoitscript.com" or "87.106.244.38".
	 * @param timeout
	 *            - [optional] Is the time to wait for an answer in milliseconds
	 *            (default is 4000).
	 * @return <ul>
	 *         <li><b>Success:</b> Returns the roundtrip-time in milliseconds (
	 *         greater than 0 ).</li>
	 *         <li><b>Failure:</b> Returns 0 if host is not pingable or other
	 *         network errors occurred</li>
	 *         </ul>
	 * @throws Exception
	 */
	public int ping(String host, int timeout) throws Exception {
		Map<String, Comparable<?>> result = null;
		if (timeout > 0) {
			result = runRemoteScript(commandCreate("Ping", host, timeout));
		} else {
			result = runRemoteScript(commandCreate("Ping", host));
		}
		return Integer.parseInt(result.get(STDOUT).toString());
	}

	/**
	 * Pings a host and returns the roundtrip-time.
	 * 
	 * @param host
	 *            - Can be i.e. "www.autoitscript.com" or "87.106.244.38".
	 * @return <ul>
	 *         <li><b>Success:</b> Returns the roundtrip-time in milliseconds (
	 *         greater than 0 ).</li>
	 *         <li><b>Failure:</b> Returns 0 if host is not pingable or other
	 *         network errors occurred</li>
	 *         </ul>
	 * @throws Exception
	 */
	public int ping(String host) throws Exception {
		return ping(host, -1);
	}

	/**
	 * Gets the current state of a control
	 * 
	 * @param controlId
	 * @throws Exception
	 */
	public void guiCtrlGetState(String controlId) throws Exception {
		processResult("Control click, id: " + controlId, runRemoteScript(commandCreate("GUICtrlGetState", controlId)));
	}

	/**
	 * Sets text of a control
	 * 
	 * @param title
	 *            - title of the window to access
	 * @param text
	 *            - text of the window to access
	 * @param controlId
	 *            - Control to interact with
	 * @param newTextToSet
	 *            - new text to be set into the control
	 * @throws Exception
	 */
	public void controlSend(String title, String text, String controlIdString, String cmdToSend) throws Exception {
		processResult("Control send",
				runRemoteScript(commandCreate("ControlSend", title, text, controlIdString, cmdToSend)));
	}

	/**
	 * Writes data to the STDOUT stream. Some text editors can read this stream
	 * as can other programs which may be expecting data on this stream.
	 * 
	 * @param text
	 *            - the text to send to the agent
	 * @throws Exception
	 */
	public void consoleWrite(String text) throws Exception {
		processResult("Console write : ", runRemoteScript("\"" + text + "\""));
	}

	/**
	 * Retrieves text from a control.
	 * 
	 * @param title
	 * @param text
	 * @param controlId
	 * @param newTextToSet
	 * @throws Exception
	 */
	public String controlGetText(String title, String text, String controlId) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("ControlGetText", title, text, controlId));
		processResult("Get text from control: " + title, result);
		return String.valueOf(result.get(STDOUT));
	}

	/**
	 * Sets text of a control
	 * 
	 * @param title
	 *            - title of the window to access
	 * @param text
	 *            - text of the window to access
	 * @param controlId
	 *            - Control to interact with
	 * @param newTextToSet
	 *            - new text to be set into the control
	 * @throws Exception
	 */
	public void controlSetText(String title, String text, int controlId, String newTextToSet) throws Exception {
		String controlIdString = "[ID:" + controlId + "]";
		controlSetText(title, text, controlIdString, newTextToSet);
	}

	public void controlSetText(String title, String text, String classType, int instance, String newTextToSet)
			throws Exception {
		String controlIdString = "[CLASS:" + classType + ";INSTANCE:" + instance + "]";
		controlSetText(title, text, controlIdString, newTextToSet);
	}

	public void controlSetText(String title, String text, String controlIdString, String newTextToSet) throws Exception {
		processResult("Control set text: ",
				runRemoteScript(commandCreate("ControlSetText", title, text, controlIdString, newTextToSet)));
	}

	public void controlSetText(String title, String text, String controlIdString, String newTextToSet, Integer flag)
			throws Exception {
		processResult("Control set text: ",
				runRemoteScript(commandCreate("ControlSetText", title, text, controlIdString, newTextToSet, flag)));
	}

	/**
	 * Sets input focus to a given control on a window
	 * 
	 * @param title
	 *            - title of the window to access
	 * @param text
	 *            - text of the window to access
	 * @param controlId
	 *            - Control to interact with
	 * @throws Exception
	 */
	public void controlFocus(String title, String text, int controlId) throws Exception {
		processResult("Control focus, id: " + controlId,
				runRemoteScript(commandCreate("ControlFocus", title, text, "[ID:" + controlId + "]")));
	}

	/**
	 * Sets input focus to a given control on a window
	 * 
	 * @param title
	 * @param text
	 * @param controlIdString
	 * @throws Exception
	 */
	public void controlFocus(String title, String text, String controlIdString) throws Exception {
		processResult("Control focus: ", runRemoteScript(commandCreate("ControlFocus", title, text, controlIdString)));
	}

	/**
	 * Sends a mouse click command to a given control
	 * 
	 * @param title
	 *            - title of the window to access
	 * @param text
	 *            - text of the window to access
	 * @param controlId
	 *            - Control to interact with
	 * @throws Exception
	 */
	public void controlClick(String title, String text, int controlId) throws Exception {
		String cmd = "[ID:" + controlId + "]";
		processResult("Control click, id: " + controlId,
				runRemoteScript(commandCreate("ControlClick", title, text, cmd)));
	}

	/**
	 * 
	 * @param title
	 *            = title of the window
	 * @param text
	 *            - internal text
	 * @param classType
	 *            - class of the object like TButton
	 * @param instance
	 *            - the instance of the object
	 * @throws Exception
	 */
	public void controlClick(String title, String text, String classType, int instance) throws Exception {
		String cmd = "[CLASS:" + classType + "; INSTANCE:" + instance + "]";
		controlClick(title, text, cmd);
	}

	public void controlClick(String title, String text, String cmd) throws Exception {
		processResult("Control click : " + cmd, runRemoteScript(commandCreate("ControlClick", title, text, cmd)));
	}

	/**
	 * @param title
	 *            - title of the window to access
	 * @param text
	 *            - text of the window to access
	 * @param controlId
	 *            - control to interact with
	 * @param stringCommand
	 *            - command to send to the control example list:
	 *            <p>
	 *            "IsVisible", "" = Returns 1 if Control is visible, else 0<br>
	 *            "IsEnabled", "" = Returns 1 if Control is enabled, else 0<br>
	 *            "ShowDropDown", "" = Drops a ComboBox<br>
	 *            "HideDropDown", "" = UNdrops a ComboBox<br>
	 *            "AddString", '' = Adds a string to the end in a ListBox or
	 *            ComboBox<br>
	 *            "DelString", = Deletes a string according to occurrence in a
	 *            ListBox or ComboBox<br>
	 *            "FindString", '' = Returns occurrence ref of the exact string
	 *            in a ListBox or ComboBox<br>
	 *            "SetCurrentSelection", = Sets selection to occurrence ref in a
	 *            ListBox or ComboBox<br>
	 *            "SelectString", '' = Sets selection according to string in a
	 *            ListBox or ComboBox<br>
	 *            "IsChecked", "" = Returns 1 if Button is checked, else 0<br>
	 *            "Check", "" = Checks radio or check Button<br>
	 *            "UnCheck", "" = Unchecks radio or check Button<br>
	 *            "GetCurrentLine", "" = Returns the line # where the caret is
	 *            in an Edit<br>
	 *            "GetCurrentCol", "" = Returns the column # where the caret is
	 *            in an Edit<br>
	 *            "GetCurrentSelection", "" = Returns name of the currently
	 *            selected item in a ListBox or ComboBox<br>
	 *            "GetLineCount", "" = Returns number of lines in an Edit<br>
	 *            "GetLine", = Returns text at line number passed of an Edit<br>
	 *            "GetSelected", "" = Returns selected text of an Edit<br>
	 *            "EditPaste", '' = Pastes the 'string' at the Edit's caret
	 *            position<br>
	 *            "CurrentTab", "" = Returns the current Tab shown of a
	 *            SysTabControl32<br>
	 *            "TabRight", "" = Moves to the next tab to the right of a
	 *            SysTabControl32<br>
	 *            "TabLeft", "" = Moves to the next tab to the left of a
	 *            SysTabControl32<br>
	 *            </p>
	 * @throws Exception
	 */
	public void controlCommand(String title, String text, int controlId, String stringCommand) throws Exception {
		processResult(
				"Control command, id: " + controlId,
				runRemoteScript(commandCreate("ControlCommand", title, text, "[ID:" + controlId + "]", stringCommand,
						"")));
	}

	public void controlCommand(String title, String text, String controlString, String stringCommand) throws Exception {
		processResult("Control command",
				runRemoteScript(commandCreate("ControlCommand", title, text, controlString, stringCommand, "")));
	}

	/**
	 * Deletes a value from a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @param key
	 *            - <b>[optional]</b> The key name in the .ini file to delete.<br>
	 *            If the key name is not given the entire section is deleted.<br>
	 *            The Default keyword may also be used which will cause the
	 *            section to be deleted.
	 * @return <b>true</b> if Success or <b>false</b> if the INI file does not
	 *         exist or if the file is read-only.
	 * @throws Exception
	 */
	public boolean iniDelete(String filename, String section, String key) throws Exception {
		Map<?, ?> result;
		if (StringUtils.isEmpty(key)) {
			result = runRemoteScript(commandCreate("IniDelete", filename, section));
		} else {
			result = runRemoteScript(commandCreate("IniDelete", filename, section, key));
		}
		processResult("Delete ini file: ", result);
		Object stdOut = result.get(STDOUT);
		if (stdOut != null && !StringUtils.isEmpty(stdOut.toString())) {
			return Boolean.parseBoolean(stdOut.toString());
		}
		return false;
	}

	/**
	 * Deletes a value from a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @return <b>true</b> if Success or <b>false</b> if the INI file does not
	 *         exist or if the file is read-only.
	 * @throws Exception
	 */
	public boolean iniDelete(String filename, String section) throws Exception {
		return iniDelete(filename, section, null);
	}

	/**
	 * Reads a value from a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @param key
	 *            - The key name in the in the .ini file.
	 * @param defaultValue
	 *            - The default value to return if the requested key is not
	 *            found.
	 * @return <ul>
	 *         <li>Success: Returns the requested key value.</li>
	 *         <li>Failure: Returns the default string if requested key not
	 *         found.</li>
	 *         </ul>
	 * @throws Exception
	 */
	public String iniRead(String filename, String section, String key, String defaultValue) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("IniRead", filename, section, key, defaultValue));
		processResult("Read a value from ini file: ", result);
		Object stdOut = result.get(STDOUT);
		if (stdOut != null && !StringUtils.isEmpty(stdOut.toString())) {
			return stdOut.toString();
		}
		return defaultValue;
	}

	/**
	 * Renames a section in a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @param newSection
	 *            - The new section name.
	 * @param overwrite
	 *            <b>[optional]</b>
	 *            <ol>
	 *            <li>false (Default) - Fail if "new section" already exists.</li>
	 *            <li>true - Overwrite "new section". This will erase any
	 *            existing keys in "new section"</li>
	 *            </ol>
	 * @return <ul>
	 *         <li>Success: Non-zero.</li>
	 *         <li>Failure: Zero (0)</li>
	 *         </ul>
	 * @throws Exception
	 */
	public int iniRenameSection(String filename, String section, String newSection, boolean overwrite) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("IniRenameSection", filename, section, newSection,
				overwrite ? 1 : 0));
		processResult("Rename section in ini file: ", result);
		Object stdOut = result.get(STDOUT);
		if (stdOut != null && !StringUtils.isEmpty(stdOut.toString())) {
			try {
				return Integer.parseInt(stdOut.toString());
			} catch (Exception exception) {
			}
		}
		return 0;
	}

	/**
	 * Renames a section in a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @param newSection
	 *            - The new section name.
	 * @return <ul>
	 *         <li>Success: Non-zero.</li>
	 *         <li>Failure: Zero (0)</li>
	 *         </ul>
	 * @throws Exception
	 */
	public int iniRenameSection(String filename, String section, String newSection) throws Exception {
		return iniRenameSection(filename, section, newSection, false);
	}

	/**
	 * Writes a value to a standard format .ini file.<br>
	 * <b>Remarks</b><br>
	 * <b>A standard ini file looks like:<br>
	 * [SectionName]<br>
	 * Key=Value<br>
	 * </b> If file does not exist, it is created. Any directories that do not
	 * exist, will not be created.<br>
	 * Keys and/or sections are added to the end and are not sorted in any
	 * way."?<br>
	 * When writing a value that is quoted, the quotes are stripped. In order to
	 * write the quote marks to the value,<br>
	 * you must double up the quoting. For example: ""This is a test"" will
	 * produce "This is a test" in the file.<br>
	 * Leading and trailing whitespace is stripped. In order to preserve the
	 * whitespace, the string must be quoted.<br>
	 * For example, " this is a test" will preserve the whitespace but per
	 * above, the quotation marks are stripped.<br>
	 * Multi-line values are not possible.<br>
	 * 
	 * @param filename
	 *            - The filename of the .ini file.
	 * @param section
	 *            - The section name in the .ini file.
	 * @param key
	 *            - The key name in the in the .ini file.
	 * @param value
	 *            - The value to write/change.
	 * @return true if success
	 * @throws Exception
	 */
	public boolean iniWrite(String filename, String section, String key, String value) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("IniWrite", filename, section, key, value));
		processResult("Write new value into ini file: ", result);
		Object stdOut = result.get(STDOUT);
		if (stdOut != null && !StringUtils.isEmpty(stdOut.toString())) {
			try {
				return Boolean.parseBoolean(stdOut.toString());
			} catch (Exception exception) {
			}
		}
		return false;
	}

	/**
	 * Runs an external program under the context of a different user.<br>
	 * 
	 * @param username
	 *            - The username to log on with.
	 * @param domain
	 *            - The domain to authenticate against.
	 * @param password
	 *            - The password for the user.
	 * @param logon_flag
	 *            <ul>
	 *            <li>0 - Interactive logon with no profile.</li>
	 *            <li>1 - Interactive logon with profile.</li>
	 *            <li>2 - Network credentials only.</li>
	 *            <li>4 - Inherit the calling processes environment instead of
	 *            the user's.</li>
	 *            </ul>
	 * @param program
	 *            - The full path of the program (EXE, BAT, COM, or PIF) to run
	 *            (see remarks).
	 * @param workingdir
	 *            - The working directory. If not specified, then the value of @SystemDir
	 *            will be used. This is not the path to the program.
	 * @param show_Flag
	 *            - The "show" flag of the executed program:
	 *            <ul>
	 *            <li>@SW_HIDE = Hidden window (or Default keyword)</li>
	 *            <li>@SW_MINIMIZE = Minimized window</li>
	 *            <li>@SW_MAXIMIZE = Maximized window</li>
	 *            </ul>
	 * @param opt_flag
	 *            - Controls various options related to how the parent and child
	 *            process interact.
	 *            <ul>
	 *            <li>0x1 ($STDIN_CHILD) = Provide a handle to the child's STDIN
	 *            stream</li>
	 *            <li>0x2 ($STDOUT_CHILD) = Provide a handle to the child's
	 *            STDOUT stream</li>
	 *            <li>0x4 ($STDERR_CHILD) = Provide a handle to the child's
	 *            STDERR stream</li>
	 *            <li>0x8 ($STDERR_MERGED) = Provides the same handle for STDOUT
	 *            and STDERR. Implies both $STDOUT_CHILD and $STDERR_CHILD.</li>
	 *            <li>0x10 ($STDIO_INHERIT_PARENT) = Provide the child with the
	 *            parent's STDIO streams. This flag can not be combined with any
	 *            other STDIO flag. This flag is only useful when the parent is
	 *            compiled as a Console application.</li>
	 *            <li>0x10000 ($RUN_CREATE_NEW_CONSOLE) = The child console
	 *            process should be created with it's own window instead of
	 *            using the parent's window. This flag is only useful when the
	 *            parent is compiled as a Console application.</li>
	 *            </ul>
	 * @return <ul>
	 *         <li>Success: The PID of the process that was launched.</li>
	 *         <li>Failure: Returns 0</li>
	 *         </ul>
	 *         <b>Remarks:</b><br>
	 *         Paths with spaces need to be enclosed in quotation marks.<br>
	 * <br>
	 *         It is important to specify a working directory the user you are
	 *         running as has access to, otherwise the function will fail.<br>
	 * <br>
	 *         It is recommended that you only load the user's profile is you
	 *         are sure you need it. There is a small chance a profile can be
	 *         stuck in memory under the right conditions. If a script using
	 *         RunAs() happens to be running as the SYSTEM account (for example,
	 *         if the script is running as a service) and the user's profile is
	 *         loaded, then you must take care that the script remains running
	 *         until the child process closes.<br>
	 * <br>
	 *         When running as an administrator, the Secondary Logon (RunAs)
	 *         service must be enabled or this function will fail. This does not
	 *         apply when running as the SYSTEM account.<br>
	 * <br>
	 *         After running the requested program the script continues. To
	 *         pause execution of the script until the spawned program has
	 *         finished use the RunAsWait function instead.<br>
	 * <br>
	 *         Providing the Standard I/O parameter with the proper values
	 *         permits interaction with the child process through the
	 *         StderrRead, StdinWrite and StdoutRead functions. Combine the flag
	 *         values (or use $STDERR_CHILD, $STDIN_CHILD & $STDOUT_CHILD,
	 *         defined in Constants.au3) to manage more than one stream.<br>
	 * <br>
	 *         In order for the streams to close, the following conditions must
	 *         be met: 1) The child process has closed it's end of the stream
	 *         (this happens when the child closes). 2) AutoIt must read any
	 *         captured streams until there is no more data. 3) If STDIN is
	 *         provided for the child, StdinWrite() must be called to close the
	 *         stream. Once all streams are detected as no longer needed, all
	 *         internal resources will automatically be freed. StdioClose can be
	 *         used to force the STDIO streams closed.<br>
	 * <br>
	 *         The "load profile" and "network credentials only" options are
	 *         incompatible. Using both will produce undefined results.<br>
	 * <br>
	 *         There is an issue in the Windows XP generation of Windows which
	 *         prevents STDIO redirection and the show flag from working. See
	 *         Microsoft Knowledge Base article KB818858 for more information
	 *         about which versions are affected as well as a hotfix for the
	 *         issue. User's running Windows 2000, Windows XP SP2 or later, or
	 *         Windows Vista are not affected.<br>
	 * <br>
	 * @throws Exception
	 */
	public int runAs(String username, String domain, String password, int logon_flag, String program,
			String workingdir, String show_Flag, int opt_flag) throws Exception {
		String script = null;
		if (workingdir == null) {
			script = commandCreate("RunAs", quote(username), quote(domain), quote(password), logon_flag, quote(program));
		} else {
			if (StringUtils.isEmpty(show_Flag)) {
				script = commandCreate("RunAs", quote(username), quote(domain), quote(password), logon_flag,
						quote(program), quote(workingdir));
			} else {
				if (opt_flag != 1 && opt_flag != 2 && opt_flag != 4 && opt_flag != 8 && opt_flag != 10
						&& opt_flag != 10000) {
					script = commandCreate("RunAs", quote(username), quote(domain), quote(password), logon_flag,
							quote(program), quote(workingdir), show_Flag);
				} else {
					script = commandCreate("RunAs", quote(username), quote(domain), quote(password), logon_flag,
							quote(program), quote(workingdir), show_Flag, opt_flag);
				}
			}
		}
		Map<String, Comparable<?>> result = runRemoteScript(script);
		return Integer.valueOf(result.get(STDOUT).toString());
	}

	public int runAs(String username, String domain, String password, int logon_flag, String program,
			String workingdir, String show_Flag) throws Exception {
		return runAs(username, domain, password, logon_flag, program, workingdir, show_Flag, 0);
	}

	public int runAs(String username, String domain, String password, int logon_flag, String program, String workingdir)
			throws Exception {
		return runAs(username, domain, password, logon_flag, program, workingdir, null);
	}

	public int runAs(String username, String domain, String password, int logon_flag, String program) throws Exception {
		return runAs(username, domain, password, logon_flag, program, null);
	}

	/**
	 * Runs an external program under the context of a different user and pauses
	 * script execution until the program finishes.<br>
	 * 
	 * @param username
	 *            - The username to log on with.
	 * @param domain
	 *            - The domain to authenticate against.
	 * @param password
	 *            - The password for the user.
	 * @param logon_flag
	 *            <ul>
	 *            <li>0 - Interactive logon with no profile.</li>
	 *            <li>1 - Interactive logon with profile.</li>
	 *            <li>2 - Network credentials only.</li>
	 *            <li>4 - Inherit the calling processes environment instead of
	 *            the user's.</li>
	 *            </ul>
	 * @param program
	 *            - The full path of the program (EXE, BAT, COM, or PIF) to run
	 *            (see remarks).
	 * @param workingdir
	 *            - The working directory. If not specified, then the value of @SystemDir
	 *            will be used. This is not the path to the program.
	 * @param show_Flag
	 *            - The "show" flag of the executed program:
	 *            <ul>
	 *            <li>@SW_HIDE = Hidden window (or Default keyword)</li>
	 *            <li>@SW_MINIMIZE = Minimized window</li>
	 *            <li>@SW_MAXIMIZE = Maximized window</li>
	 *            </ul>
	 * @param opt_flag
	 *            - Controls various options related to how the parent and child
	 *            process interact.
	 *            <ul>
	 *            <li>0x10000 ($RUN_CREATE_NEW_CONSOLE) = The child console
	 *            process should be created with it's own window instead of
	 *            using the parent's window. This flag is only useful when the
	 *            parent is compiled as a Console application.</li>
	 *            </ul>
	 * @return <ul>
	 *         <li>Success: Returns the exit code of the program that was run.</li>
	 *         <li>Failure: Returns 0</li>
	 *         </ul>
	 *         <b>Remarks:</b><br>
	 *         Paths with spaces need to be enclosed in quotation marks.<br>
	 * <br>
	 *         It is important to specify a working directory the user you are
	 *         running as has access to, otherwise the function will fail.<br>
	 * <br>
	 *         It is recommended that you only load the user's profile is you
	 *         are sure you need it. There is a small chance a profile can be
	 *         stuck in memory under the right conditions. If a script using
	 *         RunAs() happens to be running as the SYSTEM account (for example,
	 *         if the script is running as a service) and the user's profile is
	 *         loaded, then you must take care that the script remains running
	 *         until the child process closes.<br>
	 * <br>
	 *         When running as an administrator, the Secondary Logon (RunAs)
	 *         service must be enabled or this function will fail. This does not
	 *         apply when running as the SYSTEM account.<br>
	 * <br>
	 *         After running the requested program the script continues. To
	 *         pause execution of the script until the spawned program has
	 *         finished use the RunAsWait function instead.<br>
	 * <br>
	 *         Providing the Standard I/O parameter with the proper values
	 *         permits interaction with the child process through the
	 *         StderrRead, StdinWrite and StdoutRead functions. Combine the flag
	 *         values (or use $STDERR_CHILD, $STDIN_CHILD & $STDOUT_CHILD,
	 *         defined in Constants.au3) to manage more than one stream.<br>
	 * <br>
	 *         In order for the streams to close, the following conditions must
	 *         be met: 1) The child process has closed it's end of the stream
	 *         (this happens when the child closes). 2) AutoIt must read any
	 *         captured streams until there is no more data. 3) If STDIN is
	 *         provided for the child, StdinWrite() must be called to close the
	 *         stream. Once all streams are detected as no longer needed, all
	 *         internal resources will automatically be freed. StdioClose can be
	 *         used to force the STDIO streams closed.<br>
	 * <br>
	 *         The "load profile" and "network credentials only" options are
	 *         incompatible. Using both will produce undefined results.<br>
	 * <br>
	 *         There is an issue in the Windows XP generation of Windows which
	 *         prevents STDIO redirection and the show flag from working. See
	 *         Microsoft Knowledge Base article KB818858 for more information
	 *         about which versions are affected as well as a hotfix for the
	 *         issue. User's running Windows 2000, Windows XP SP2 or later, or
	 *         Windows Vista are not affected.<br>
	 * <br>
	 * @throws Exception
	 */
	public int runAsWait(String username, String domain, String password, int logon_flag, String program,
			String workingdir, String show_Flag, int opt_flag) throws Exception {
		String script = null;
		if (workingdir == null) {
			script = commandCreate("RunAsWait", quote(username), quote(domain), quote(password), logon_flag,
					quote(program));
		} else {
			if (StringUtils.isEmpty(show_Flag)) {
				script = commandCreate("RunAsWait", quote(username), quote(domain), quote(password), logon_flag,
						quote(program), quote(workingdir));
			} else {
				if (opt_flag != 10000) {
					script = commandCreate("RunAsWait", quote(username), quote(domain), quote(password), logon_flag,
							quote(program), quote(workingdir), show_Flag);
				} else {
					script = commandCreate("RunAsWait", quote(username), quote(domain), quote(password), logon_flag,
							quote(program), quote(workingdir), show_Flag, opt_flag);
				}
			}
		}
		Map<String, Comparable<?>> result = runRemoteScript(script);
		return Integer.valueOf(result.get(STDOUT).toString());
	}

	public int runAsWait(String username, String domain, String password, int logon_flag, String program,
			String workingdir, String show_Flag) throws Exception {
		return runAsWait(username, domain, password, logon_flag, program, workingdir, show_Flag, 0);
	}

	public int runAsWait(String username, String domain, String password, int logon_flag, String program,
			String workingdir) throws Exception {
		return runAsWait(username, domain, password, logon_flag, program, workingdir, null);
	}

	public int runAsWait(String username, String domain, String password, int logon_flag, String program)
			throws Exception {
		return runAsWait(username, domain, password, logon_flag, program, null);
	}

	public void run(String fileName) throws Exception {
		run(fileName, true);
	}

	public void run(String fileName, boolean isQuote) throws Exception {
		processResult("Run " + fileName, runRemoteScript(commandCreate("Run", isQuote, fileName)));
	}

	public void runScript(String script) throws Exception {
		runScript(script, 0);
	}

	public void runScript(String script, int timeout) throws Exception {
		processResult("Run script: " + script, runRemoteScript(script, timeout));
	}

	public void send(String toSend) throws Exception {
		toSend = toSend.replaceAll("(\r\n|\r|\n)", "{ENTER}");
		String cmd = commandCreate("Send", toSend);
		processResult("Send: " + toSend, runRemoteScript(cmd));
	}

	/**
	 * Put the autoit client to sleep for specific period (in milliseconds).<br>
	 * 
	 * @param delay
	 *            in milliseconds
	 * @throws Exception
	 */
	public void sleep(long delay) throws Exception {
		processResult("Sleep for: " + delay + " milliseconds", runRemoteScript(commandCreate("Sleep", false, delay)));
	}

	/**
	 * Shows, hides, minimizes, maximizes, or restores a window.<br>
	 * 
	 * @param title
	 *            - The title of the window to show.
	 * @param text
	 *            - The text of the window to show.
	 * @param flag
	 *            - The "show" flag of the executed program:<br>
	 *            &#64;SW_HIDE = Hide window.<br>
	 *            &#64;SW_SHOW = Shows a previously hidden window.<br>
	 *            &#64;SW_MINIMIZE = Minimize window.<br>
	 *            &#64;SW_MAXIMIZE = Maximize window.<br>
	 *            &#64;SW_RESTORE = Undoes a window minimization or
	 *            maximization.<br>
	 *            &#64;SW_DISABLE = Disables the window.<br>
	 *            &#64;SW_ENABLE = Enables the window.<br>
	 * @return true for success.
	 * @throws Exception
	 */
	public boolean winSetState(String title, String text, String flag) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinSetState", false, quote(title), quote(text), flag));
		processResult("Window set state: " + title, result);
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Kills the Windows and ignoring any pop-up confirmation windows.<br>
	 * 
	 * @param title
	 * @throws Exception
	 */
	public void winKill(String title) throws Exception {
		processResult("Windows kill: " + title, runRemoteScript(commandCreate("WinKill", title)));
	}

	public void winClose(String title) throws Exception {
		processResult("Windows close: " + title, runRemoteScript(commandCreate("WinClose", title)));
	}

	/**
	 * This method will wait for the window appears with the "title".<br>
	 * 
	 * @param title
	 *            - The title of the window to check.
	 * @throws Exception
	 * @return Success: Returns window handler.<br>
	 *         Failure: Returns 0 if timeout occurred.
	 */
	public boolean winWait(String title) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinWait", title));
		processResult("Window wait: " + title, result);
		return !"0".equals(result.get(STDOUT));
	}

	/**
	 * This method will wait for the window appears with the "title" and "text"
	 * and wait for "timeOut" milliseconds.<br>
	 * 
	 * @param title
	 *            - The title of the window to check.
	 * @param text
	 *            - [optional] The text of the window to check.
	 * @param timeOut
	 *            - [optional] Timeout in seconds
	 * @throws Exception
	 * @return Success: Returns window handler.<br>
	 *         Failure: Returns 0 if timeout occurred.
	 */
	public boolean winWait(String title, String text, int timeOut) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinWait", title, text, timeOut));
		processResult("Window wait: " + title, result);
		return !"0".equals(result.get(STDOUT));
	}

	private boolean checkType(int type) {
		return type > -1 && type < 3;
	}

	/**
	 * Sets the timestamp of one of more files.<br>
	 * <b>REMARK:</b><br>
	 * Use the following code for translate Java Date format to AutoIt date
	 * format:<br>
	 * <code>
	 * Date javaDateFormat = new Date(System.currentTimeMillis());<br>
	 * SimpleDateFormat simpleDateFormat = new SimpleDateFormat("<b>yyyyMMddHHmmss</b>");<br>
	 * String autoItDateFormat = simpleDateFormat.format(javaDateFormat);<br>
	 * </code>
	 * 
	 * @param filePattern
	 *            - File(s) to change, e.g. C:\*.au3, C:\Dir
	 * @param time
	 *            - The new time to set in the format "YYYYMMDDHHMMSS" (Year,
	 *            month, day, hours (24hr clock), seconds). If the time is blank
	 *            "" then the current time is used.
	 * @param type
	 *            - [optional] The timestamp to change:
	 *            <ul>
	 *            <li><b>0 = Modified (default)</b></li>
	 *            <li>1 = Created</li>
	 *            <li>2 = Accessed</li>
	 *            </ul>
	 * @param recurse
	 *            - [optional] If this is set to 1, then directories are
	 *            recursed into. <b>Default is 0 (no recursion)</b>.
	 * @return true if success
	 * @throws Exception
	 */
	public boolean fileSetTime(String filePattern, String time, int type, boolean recurse) throws Exception {
		Map<?, ?> result = null;
		if (checkType(type)) {
			result = runRemoteScript(commandCreate("FileSetTime", filePattern, time, type, recurse ? 1 : 0));
		} else {
			result = runRemoteScript(commandCreate("FileSetTime", filePattern, time, 0, 0));
		}
		return Boolean.parseBoolean(result.get(STDOUT).toString());
	}

	/**
	 * Returns the time and date information for a file.<br>
	 * <b>REMARK:</b><br>
	 * Notice that return values are zero-padded. Use the following code for
	 * translate AutoIt date format to Java Date format:<br>
	 * <code>
	 * SimpleDateFormat simpleDateFormat = new SimpleDateFormat("<b>yyyyMMddHHmmss</b>");<br>
	 * Date date = simpleDateFormat.parse(autoItDateFrmat);<br>
	 * </code>
	 * 
	 * @param filename
	 *            - Filename to check.
	 * @param option
	 *            - [optional] Flag to indicate which timestamp:
	 *            <ul>
	 *            <li><b>0 = Modified (default)</b></li>
	 *            <li>1 = Created</li>
	 *            <li>2 = Accessed</li>
	 *            </ul>
	 * @return date in String (format : YYYYMMDDHHMMSS)
	 * @throws Exception
	 */
	public String fileGetTime(String filename, int option) throws Exception {
		Map<?, ?> result = null;
		if (checkType(option)) {
			result = runRemoteScript(commandCreate("FileGetTime", option, 1));
		} else {
			result = runRemoteScript(commandCreate("FileGetTime", 0, 1));
		}
		return result.get(STDOUT).toString();
	}

	/**
	 * Checks if a file or directory exists.<br>
	 * <code><b>FileExists ( "path" )</b></code><br<
	 * 
	 * @param path
	 *            - The directory or file to check.
	 * @return true if success
	 * @throws Exception
	 */
	public boolean fileExists(String path) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileExists", path));
		processResult("Is " + path + " exist? ", result);
		return "1".equals(result.get(STDOUT).toString());
	}

	public boolean fileCopy(String source, String destination) throws Exception {
		return fileCopy(source, destination, true, false);
	}

	public boolean fileCopy(String source, String destination, boolean addQuote) throws Exception {
		return fileCopy(source, destination, addQuote, false);
	}

	public boolean fileCopy(String source, String destination, boolean addQuote, boolean overWrite) throws Exception {
		if (addQuote) {
			source = quote(source);
			destination = quote(destination);
		}
		Map<?, ?> result = runRemoteScript(commandCreate("FileCopy", source, destination, (overWrite ? 1 : 0)));
		processResult("File copy : " + source + " >>>>> " + destination, result);
		return "1".equals(result.get(STDOUT).toString());
	}

	public boolean fileCopy(String source, String destination, boolean addQuote, boolean overWrite, boolean createPath)
			throws Exception {
		if (addQuote) {
			source = quote(source);
			destination = quote(destination);
		}
		Map<?, ?> result = runRemoteScript(commandCreate("FileCopy", source, destination, (overWrite ? 1 : 0)
				+ (createPath ? 8 : 0)));
		processResult("File copy : " + source + " >>>>> " + destination, result);
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * Writes an environment variable.<br>
	 * <b>Remarks:</b><br>
	 * A environment variable set in this way will only be accessible to
	 * programs that AutoIt spawns (Run, RunWait). <b>Once AutoIt closes, the
	 * variables will cease to exist.</b>
	 * 
	 * @param envvariable
	 *            - Name of the environment variable to set.
	 * @param value
	 *            - [optional] Value to set the environment variable to. If a
	 *            value is not used the environment variable will be deleted.
	 * @return
	 * @throws Exception
	 */
	public boolean envSet(String envvariable, String value) throws Exception {
		Map<?, ?> result = null;
		if (value == null) {
			result = runRemoteScript(commandCreate("EnvSet", envvariable));
		} else {
			result = runRemoteScript(commandCreate("EnvSet", envvariable, value));
		}
		processResult("Environment Set ", result);
		return !"0".equals(result.get(STDOUT).toString());
	}

	/**
	 * Refreshes the OS environment.<br>
	 * <b>Remarks:</b><br>
	 * Similar effect as logging off and then on again. For example, changes to
	 * the %path% environment might not take effect until you call EnvUpdate (or
	 * you logoff/reboot).
	 * 
	 * @throws Exception
	 */
	public void envUpdate() throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("EnvUpdate"));
		processResult("Environment Update ", result);
	}

	/**
	 * Retrieves an environment variable.
	 * 
	 * @param envvariable
	 *            - Name of the environment variable to get such as "TEMP" or
	 *            "PATH".
	 * @return Returns the requested variable (or a blank string if the variable
	 *         does not exist).
	 * @throws Exception
	 */
	public String envGet(String envvariable) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("EnvGet", envvariable));
		processResult("Environment Get ", result);
		return result.get(STDOUT).toString();
	}

	/**
	 * Returned value should be String. when The "ClipGet" Failed to retrieve
	 * from board The error codes are :<br>
	 * 1 mean that the clipboard is empty 2 mean that the clipboard contains a
	 * non-text entry 3 and 4 mean that the clipboard cannot be accessed.
	 * 
	 * @throws Exception
	 */
	public String clipGet() throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("ClipGet"));
		processResult("Clipboard Get ", result);
		return String.valueOf(result.get(STDOUT));
	}

	/**
	 * <b>Writes text to the clipboard.</b><br>
	 * Works like {@link #clipPut(String, boolean)} when boolean is "true"<br>
	 * <b>Remarks:</b>
	 * <ul>
	 * <li>Any existing clipboard contents are overwritten.</li>
	 * <li>An empty string "" will empty the clipboard.</li>
	 * </ul>
	 * 
	 * @param value
	 *            - The text to write to the clipboard.
	 * @return true if operation success.
	 * @throws Exception
	 */
	public boolean clipPut(String value) throws Exception {
		return clipPut(value, true);
	}

	/**
	 * <b>Writes text to the clipboard.</b><br>
	 * <b>Remarks:</b>
	 * <ul>
	 * <li>Any existing clipboard contents are overwritten.</li>
	 * <li>An empty string "" will empty the clipboard.</li>
	 * </ul>
	 * 
	 * @param value
	 *            - The text to write to the clipboard.
	 * @param addQuote
	 * @return true if operation success.
	 * @throws Exception
	 */
	public boolean clipPut(String value, boolean addQuote) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("ClipPut", addQuote, value));
		processResult("Clipboard put the value " + value, result);
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * Retrieves the internal handle of a window. <br>
	 * <b>Remarks</b><br>
	 * This function allows you to use handles to specify windows rather than
	 * "title" and "text".<br>
	 * Once you have obtained the handle you can access the required window even
	 * if its title changes.<br>
	 * 
	 * @param title
	 *            - The title of the window to read.
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @return <ul>
	 *         <li><b>Success:</b> Returns handle to the window.</li>
	 *         <li><b>Failure:</b> Returns "" (blank string) and sets @error to
	 *         1 if no window matches the criteria.</li>
	 *         </ul>
	 * @throws Exception
	 */
	public String winGetHandle(String title, String text) throws Exception {
		Map<?, ?> result = null;
		if (text != null) {
			result = runRemoteScript(commandCreate("WinGetHandle", title, text));
		} else {
			result = runRemoteScript(commandCreate("WinGetHandle", title));
		}
		processResult("Wait for windows active, title: " + title, result);
		return String.valueOf(result.get(STDOUT));
	}

	/**
	 * Retrieves the full title from a window.<br>
	 * <b>Remarks</b><br>
	 * WinGetTitle("[active]") returns the active window's title. WinGetTitle
	 * works on both minimized and hidden windows. If multiple windows match the
	 * criteria, the most recently active window is used.<br>
	 * 
	 * @param title
	 *            - The title of the window to read.
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @return <ul>
	 *         <li><b>Success:</b> Returns a string containing the complete
	 *         window title.</li>
	 *         <li><b>Failure:</b> Returns numeric 0 if no title match.</li>
	 *         </ul>
	 * @throws Exception
	 */
	public String winGetTitle(String title, String text) throws Exception {
		Map<?, ?> result = null;
		if (StringUtils.isEmpty(text)) {
			result = runRemoteScript(commandCreate("WinGetTitle", title));
		} else {
			result = runRemoteScript(commandCreate("WinGetTitle", title, text));
		}
		processResult("The title of window " + title + " is :", result);
		return result.get(STDOUT).toString();
	}

	/**
	 * Pauses execution of the script until the requested window is active.
	 * 
	 * @param title
	 *            - The title of the window to check.
	 * @param text
	 *            - [optional] The text of the window to check.
	 * @param timeout
	 *            - [optional] Timeout in seconds
	 * @return true if succeed, false if timeout occurred
	 * @throws Exception
	 */
	public boolean winWaitActive(String title, String text, int timeout) throws Exception {
		Map<?, ?> result = null;
		if (text != null) {
			if (timeout > 0) {
				result = runRemoteScript(commandCreate("WinWaitActive", title, text, timeout));
			} else {
				result = runRemoteScript(commandCreate("WinWaitActive", title, text));
			}
		} else {
			result = runRemoteScript(commandCreate("WinWaitActive", title));
		}
		processResult("Wait for windows active, title: " + title, result);
		return !"0".equals(result.get(STDOUT));
	}

	public String winGetText(String title, String text) throws Exception {
		Map<?, ?> result = (text == null ? runRemoteScript(commandCreate("WinGetText", title))
				: runRemoteScript(commandCreate("WinGetText", title, text)));
		processResult("Window get text, title: " + title, result);
		return (String) result.get(STDOUT);
	}

	/**
	 * move a file from a given source path to selcted dest path
	 * 
	 * @param sourcePath
	 * @param destPath
	 * @return
	 * @throws Exception
	 */
	public boolean fileMove(String sourcePath, String destPath) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileMove", sourcePath, destPath));
		processResult("Move file : " + sourcePath + " to " + destPath, result);

		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Delete one or more files.
	 * 
	 * @param path
	 *            - The path of the file(s) to delete. Wildcards are supported.
	 * @return - Success: Return 1. Failure: Returns 0 if files are not deleted
	 *         or do not exist.
	 */
	public boolean fileDelete(String path) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileDelete", path));
		processResult("Delete file : " + path, result);

		return "1".equals(result.get(STDOUT));
	}

	public boolean isFileExist(String fileName) throws Exception {
		boolean res = agent.isFileExist(fileName);
		report.report("Is file exist : " + fileName + " results = " + String.valueOf(res));
		return res;
	}

	/**
	 * Checks to see if a specified window exists
	 * 
	 * @param title
	 * @param internalText
	 * @return
	 * @throws Exception
	 */
	public boolean winExists(String title, String internalText) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinExists", title, internalText));
		processResult("Checks to see if a specified window exists " + title, result);

		return "1".equals(result.get(STDOUT));
	}

	public boolean winActivate(String title, String internalText) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinActivate", title, internalText));
		processResult("Sets focus to a window: " + title, result);
		return !"0".equals(result.get(STDOUT));
	}

	public boolean winActivate(String title) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinActivate", title));
		processResult("Sets focus to a window: " + title, result);
		return !"0".equals(result.get(STDOUT));
	}

	/**
	 * Checks to see if a specified window exists and is currently active.<br>
	 * 
	 * @param title
	 * @return true if already window is active
	 * @throws Exception
	 */
	public boolean winActive(String title) throws Exception {
		return winActive(title, null);
	}

	/**
	 * Checks to see if a specified window exists and is currently active.<br>
	 * 
	 * @param title
	 * @param text
	 *            - optional
	 * @return true if already window is active
	 * @throws Exception
	 */
	public boolean winActive(String title, String text) throws Exception {
		boolean isTextNotExist = text == null || "".equals(text);
		Map<?, ?> result = runRemoteScript(isTextNotExist ? commandCreate("WinActive", title) : commandCreate(
				"WinActive", title, text));
		processResult("Is windows : " + title + " already active", result);
		return !"0".equals(result.get(STDOUT));
	}

	/**
	 * Flashes a window in the taskbar.
	 * 
	 * @param title
	 *            - The title of the windows
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @throws Exception
	 */
	public void winFlash(String title) throws Exception {
		winFlash(title, null, 0, 0);
	}

	/**
	 * Flashes a window in the taskbar.
	 * 
	 * @param title
	 *            - The title of the windows
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @throws Exception
	 */
	public void winFlash(String title, String text) throws Exception {
		winFlash(title, text, 0, 0);
	}

	/**
	 * Flashes a window in the taskbar.
	 * 
	 * @param title
	 *            - The title of the windows
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @param flashes
	 *            - [optional] The amount of times to flash the window. Default
	 *            is 4
	 * @throws Exception
	 */
	public void winFlash(String title, String text, int flashes) throws Exception {
		winFlash(title, text, flashes, 0);
	}

	/**
	 * Flashes a window in the taskbar.
	 * 
	 * @param title
	 *            - The title of the windows
	 * @param text
	 *            - [optional] The text of the window to read.
	 * @param flashes
	 *            - [optional] The amount of times to flash the window. Default
	 *            is 4
	 * @param delay
	 *            - The time in milliseconds to sleep between each flash.
	 *            Default 500 ms
	 * @throws Exception
	 */
	public void winFlash(String title, String text, int flashes, long delay) throws Exception {
		String command = null;
		if (text != null) {
			if (flashes > 0) {
				if (delay > 0) {
					command = commandCreate("WinFlash", title, text, flashes, delay);
				} else {
					command = commandCreate("WinFlash", title, text, flashes);
				}
			} else {
				command = commandCreate("WinFlash", title, text);
			}
		} else {
			command = commandCreate("WinFlash", title);
		}
		Map<?, ?> result = runRemoteScript(command);
		processResult("Window flashing: ", result);
	}

	/**
	 * Closes a window.
	 * 
	 * @param title
	 * @param internalText
	 * @return
	 * @throws Exception
	 */
	public boolean winClose(String title, String internalText) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("WinClose", title, internalText));
		processResult("Close window " + title, result);

		return "1".equals(result.get(STDOUT));
	}

	public String getAutoItLocation() {
		return autoItLocation;
	}

	public void processClose(String process) throws Exception {
		processResult("Process close: " + process, runRemoteScript(commandCreate("ProcessClose", process)));
	}

	/**
	 * Checks to see if a specified process exists
	 * 
	 * @param process
	 * @throws Exception
	 */
	public boolean isProcessExists(String process) throws Exception {
		return processExists(process) != 0;
	}

	/**
	 * Returns the Process ID.<br>
	 * 
	 * @param process
	 * @return The process ID.
	 * @throws Exception
	 */
	public int processExists(String process) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("ProcessExists", process));
		processResult("Process exists", result);
		return Integer.parseInt(result.get(STDOUT).toString());
	}

	/**
	 * Perform a mouse click operation.
	 * 
	 * @param button
	 * @param x
	 * @param y
	 * @return
	 * @throws Exception
	 */
	public boolean mouseClick(String button, int x, int y) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("MouseClick", button, x, y));
		processResult("Mouse click", result);

		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Runs an external program using the API.<br>
	 * <b>ShellExecute ( "filename" [, "parameters" [, "workingdir" [, "verb" [,
	 * showflag]]]] )</b><br>
	 * <b>parameters</b> - [optional] Any parameters for the program. Blank ("")
	 * uses none.<br>
	 * <b>workingdir</b> - [optional] The working directory. Blank ("") uses the
	 * current working directory.<br>
	 * <b>verb</b> - [optional] The "verb" to use, common verbs include:<br>
	 * <ol>
	 * <li><i>open</i> = (default) Opens the file specified. The file can be an
	 * executable file, a document file, or a folder</li>
	 * <li><i>edit</i> = Launches an editor and opens the document for editing.
	 * If "filename" is not a document file, the function will fail</li>
	 * <li><i>print</i> = Prints the document file specified. If "filename" is
	 * not a document file, the function will fail</li>
	 * <li><i>properties</i> = Displays the file or folder's properties</li>
	 * </ol>
	 * <b>showflag</b> - [optional] The "show" flag of the executed program:<br>
	 * <ol>
	 * <li><i>@SW_HIDE</i> = Hidden window</li>
	 * <li><i>@SW_MINIMIZE</i> = Minimized window</li>
	 * <li><i>@SW_MAXIMIZE</i> = Maximized window</li>
	 * </ol>
	 * 
	 * @param fileName
	 *            - file to execute
	 * @return <b>Success:</b> Returns 1.<br>
	 *         <b>Failure:</b> Returns 0 and sets @error to non-zero.
	 * @throws Exception
	 */
	public boolean shellExecute(String fileName, String... optionals) throws Exception {
		return shellExecute(fileName, 30, optionals);
	}

	/**
	 * Runs an external program using the API.<br>
	 * <b>ShellExecuteWait ( "filename" [, "parameters" [, "workingdir" [,
	 * "verb" [, showflag]]]] )</b><br>
	 * <b>parameters</b> - [optional] Any parameters for the program. Blank ("")
	 * uses none.<br>
	 * <b>workingdir</b> - [optional] The working directory. Blank ("") uses the
	 * current working directory.<br>
	 * <b>verb</b> - [optional] The "verb" to use, common verbs include:<br>
	 * <ol>
	 * <li><i>open</i> = (default) Opens the file specified. The file can be an
	 * executable file, a document file, or a folder</li>
	 * <li><i>edit</i> = Launches an editor and opens the document for editing.
	 * If "filename" is not a document file, the function will fail</li>
	 * <li><i>print</i> = Prints the document file specified. If "filename" is
	 * not a document file, the function will fail</li>
	 * <li><i>properties</i> = Displays the file or folder's properties</li>
	 * </ol>
	 * <b>showflag</b> - [optional] The "show" flag of the executed program:<br>
	 * <ol>
	 * <li><i>@SW_HIDE</i> = Hidden window</li>
	 * <li><i>@SW_MINIMIZE</i> = Minimized window</li>
	 * <li><i>@SW_MAXIMIZE</i> = Maximized window</li>
	 * </ol>
	 * 
	 * @param fileName
	 *            - file to execute
	 * @return <b>Success:</b> Returns the exit code of the program that was
	 *         run.<br>
	 *         <b>Failure:</b> Returns 0 and sets @error to non-zero.
	 * @throws Exception
	 */
	public int shellExecuteWait(String fileName, String... optionals) throws Exception {
		return shellExecuteWait(fileName, 30, optionals);
	}

	/**
	 * Runs an external program using the API.<br>
	 * <b>ShellExecute ( "filename" [, "parameters" [, "workingdir" [, "verb" [,
	 * showflag]]]] )</b><br>
	 * <b>parameters</b> - [optional] Any parameters for the program. Blank ("")
	 * uses none.<br>
	 * <b>workingdir</b> - [optional] The working directory. Blank ("") uses the
	 * current working directory.<br>
	 * <b>verb</b> - [optional] The "verb" to use, common verbs include:<br>
	 * <ol>
	 * <li><i>open</i> = (default) Opens the file specified. The file can be an
	 * executable file, a document file, or a folder</li>
	 * <li><i>edit</i> = Launches an editor and opens the document for editing.
	 * If "filename" is not a document file, the function will fail</li>
	 * <li><i>print</i> = Prints the document file specified. If "filename" is
	 * not a document file, the function will fail</li>
	 * <li><i>properties</i> = Displays the file or folder's properties</li>
	 * </ol>
	 * <b>showflag</b> - [optional] The "show" flag of the executed program:<br>
	 * <ol>
	 * <li><i>@SW_HIDE</i> = Hidden window</li>
	 * <li><i>@SW_MINIMIZE</i> = Minimized window</li>
	 * <li><i>@SW_MAXIMIZE</i> = Maximized window</li>
	 * </ol>
	 * 
	 * @param fileName
	 *            - file to execute
	 * @return <b>Success:</b> Returns 1.<br>
	 *         <b>Failure:</b> Returns 0 and sets @error to non-zero.
	 * @throws Exception
	 */
	public boolean shellExecute(String fileName, int timeOutInSeconds, String... optionals) throws Exception {
		List<Object> list = new ArrayList<Object>(Arrays.asList(fileName));
		if (optionals != null && optionals.length > 0) {
			list.addAll(Arrays.asList(optionals));
		}
		Map<?, ?> result = runRemoteScript(commandCreate("ShellExecute", list.toArray()), timeOutInSeconds);
		processResult("Shell execute: ", result);

		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Runs an external program using the API.<br>
	 * <b>ShellExecuteWait ( "filename" [, "parameters" [, "workingdir" [,
	 * "verb" [, showflag]]]] )</b><br>
	 * <b>parameters</b> - [optional] Any parameters for the program. Blank ("")
	 * uses none.<br>
	 * <b>workingdir</b> - [optional] The working directory. Blank ("") uses the
	 * current working directory.<br>
	 * <b>verb</b> - [optional] The "verb" to use, common verbs include:<br>
	 * <ol>
	 * <li><i>open</i> = (default) Opens the file specified. The file can be an
	 * executable file, a document file, or a folder</li>
	 * <li><i>edit</i> = Launches an editor and opens the document for editing.
	 * If "filename" is not a document file, the function will fail</li>
	 * <li><i>print</i> = Prints the document file specified. If "filename" is
	 * not a document file, the function will fail</li>
	 * <li><i>properties</i> = Displays the file or folder's properties</li>
	 * </ol>
	 * <b>showflag</b> - [optional] The "show" flag of the executed program:<br>
	 * <ol>
	 * <li><i>@SW_HIDE</i> = Hidden window</li>
	 * <li><i>@SW_MINIMIZE</i> = Minimized window</li>
	 * <li><i>@SW_MAXIMIZE</i> = Maximized window</li>
	 * </ol>
	 * 
	 * @param fileName
	 *            - file to execute
	 * @return <b>Success:</b> Returns the exit code of the program that was
	 *         run.<br>
	 *         <b>Failure:</b> Returns 0 and sets @error to non-zero.
	 * @throws Exception
	 */
	public int shellExecuteWait(String fileName, int timeOutInSeconds, String... optionals) throws Exception {
		List<Object> list = new ArrayList<Object>(Arrays.asList(fileName));
		if (optionals != null && optionals.length > 0) {
			list.addAll(Arrays.asList(optionals));
		}
		Map<?, ?> result = runRemoteScript(commandCreate("ShellExecuteWait", list.toArray()), timeOutInSeconds);
		processResult("Shell execute: ", result);

		return Integer.parseInt(result.get(STDOUT).toString());
	}

	/**
	 * Downloads a file from the internet using the http or ftp protocol.<br>
	 * <b>InetGet ( "URL" [,"filename" [, options [, background]]] )</b><br>
	 * <b>filename</b> - [optional] Local filename to download to.<br>
	 * <b>options</b> - [optional]<br>
	 * <ol>
	 * <li><i>0</i> = (default) Get the file from local cache if available</li>
	 * <li><i>1</i> = Forces a reload from the remote site</li>
	 * <li><i>2</i> = Ignore all SSL errors (with HTTPS connections)</li>
	 * <li><i>4</i> = Use ASCII when transfering files with the FTP protocol
	 * (Can not be combined with flag 8)</li>
	 * <li><i>8</i> = Use BINARY when transfering files with the FTP protocol
	 * (Can not be combined with flag 4). This is the default transfer mode if
	 * none are provided</li>
	 * <li><i>16</i> = By-pass forcing the connection online (See remarks)</li>
	 * </ol>
	 * <b>background</b> - [optional]<br>
	 * <ol>
	 * <li><i>0</i> = (default) Wait until the download is complete before
	 * continuing.</li>
	 * <li><i>1</i> = return immediately and download in the background (see
	 * remarks).</li>
	 * </ol>
	 * 
	 * <b>Note, only one download can be active at once, if you call the
	 * function again before a download is complete it will fail.</b><br>
	 * <b>To abort a download call the function with just the first parameter
	 * set to "abort"</b><br>
	 * <b>To use a username and password when connecting simply prefix the
	 * servername with "username:password@",
	 * e.g."http://myuser:mypassword@www.somesite.com"</b><br>
	 * 
	 * @param url
	 *            - URL of the file to download. See remarks below. Can be
	 *            "abort".
	 * @param timeOutInSeconds
	 * @param optionals
	 *            - filename, options, background
	 * @return Success = true.
	 * @throws Exception
	 */
	public boolean inetGet(String url, int timeOutInSeconds, Object... optionals) throws Exception {
		List<Object> vars = new ArrayList<Object>(Arrays.asList(url));
		if (optionals != null && optionals.length > 0) {
			vars.addAll(Arrays.asList(optionals));
		}
		Map<?, ?> result = runRemoteScript(commandCreate("InetGet", vars.toArray()), timeOutInSeconds);
		String stdout = result.get(STDOUT).toString();
		processResult("Downloaded " + stdout + " bytes ", result);
		return !"0".equals(stdout);
	}

	/**
	 * Returns the size (in bytes) of a file located on the internet.<br>
	 * <b>Not all servers will correctly give the file size, especially when
	 * using a proxy server.</b><br>
	 * <b>To use a username and password when connecting simply prefix the
	 * servername with "username:password@",
	 * e.g."http://myuser:mypassword@www.somesite.com"</b>
	 * 
	 * @param url
	 *            - URL of the file to download.
	 * @return the file size in bytes or 0 if failed.
	 * @throws Exception
	 */
	public int inetGetSize(String url) throws Exception {
		return inetGetSize(url, 0);
	}

	/**
	 * Returns the size (in bytes) of a file located on the internet.<br>
	 * <b>Remarks:</b><br>
	 * Internet Explorer 3 or greater must be installed for this function to
	 * work. (For ftp:// URLs IE 5 is required!)<br>
	 * <br>
	 * The URL parameter should be in the form
	 * "http://www.somesite.com/path/file.html" - just like an address you would
	 * type into your web browser.<br>
	 * <br>
	 * To use a username and password when connecting simply prefix the
	 * servername with "username:password@", e.g.<br>
	 * "http://myuser:mypassword@www.somesite.com"<br>
	 * Not all servers will correctly give the file size, especially when using
	 * a proxy server.<br>
	 * 
	 * @param url
	 *            - URL of the file to download. See remarks below.
	 * @param options
	 *            <b>[optional]</b>
	 *            <ul>
	 *            <li><i>0</i> = (default) Get the file from local cache if
	 *            available</li>
	 *            <li><i>1</i> = Forces a reload from the remote site</li>
	 *            <li><i>2</i> = Ignore all SSL errors (with HTTPS connections)</li>
	 *            <li><i>4</i> = Use ASCII when transfering files with the FTP
	 *            protocol (Can not be combined with flag 8)</li>
	 *            <li><i>8</i> = Use BINARY when transfering files with the FTP
	 *            protocol (Can not be combined with flag 4). This is the
	 *            default transfer mode if none are provided</li>
	 *            </ul>
	 * @return - Returns the size of the file in bytes.
	 * @throws Exception
	 */
	public int inetGetSize(String url, int options) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("InetGetSize", url, options));
		processResult("The " + url + " size: ", result);
		return Integer.parseInt(result.get(STDOUT).toString());
	}

	/**
	 * Returns the size of a file in bytes.<br>
	 * 
	 * @param filename
	 *            - Filename to check.
	 * @return Returns the size of the file in bytes. Or 0 if failed.
	 * @throws Exception
	 */
	public int fileGetSize(String filename) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileGetSize", filename));
		processResult("The file " + filename + " size: ", result);
		return Integer.parseInt(result.get(STDOUT).toString());
	}

	/**
	 * Returns a code string representing a file's attributes. <b>String
	 * returned could contain a combination of these letters "RASHNDOCT":</b>
	 * <ul>
	 * <li>"R" = READONLY</li>
	 * <li>"A" = ARCHIVE</li>
	 * <li>"S" = SYSTEM</li>
	 * <li>"H" = HIDDEN</li>
	 * <li>"N" = NORMA</li>
	 * <li>"D" = DIRECTORY</li>
	 * <li>"O" = OFFLINE</li>
	 * <li>"C" = COMPRESSED (NTFS compression, not ZIP compression)</li>
	 * <li>"T" = TEMPORARY</li>
	 * </ul>
	 * 
	 * @param filename
	 *            - Filename (or directory) to check.
	 * @return Returns a code string representing a files attributes. e.g. RSH
	 * @throws Exception
	 */
	public String fileGetAttrib(String filename) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileGetAttrib", filename));
		processResult("The file " + filename + " attributes: ", result);
		return result.get(STDOUT).toString();
	}

	/**
	 * Creates a directory/folder.<br>
	 * <code><b>DirCreate ( "path" )</b></code> This function will also create
	 * all parent directories given in "path" if they do not already exist.<br>
	 * 
	 * @param path
	 *            - Path of the directory to create.
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirCreate(String path) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DirCreate", path));
		processResult("Create dir: " + path, result);
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Deletes a directory/folder.<br>
	 * <code><b>DirRemove ( "path" [, recurse] )</b></code><br>
	 * Remarks : Some dir attributes can make the removal impossible.<br>
	 * 
	 * @param path
	 *            - Path of the directory to remove.
	 * @param recurse
	 *            - [optional] Use this flag to specify if you want to delete
	 *            sub-directories too.
	 *            <ul>
	 *            <li>0 = (default) do not remove files and sub-directories</li>
	 *            <li>1 = remove files and sub-directories (like the DOS DelTree
	 *            command)</li>
	 *            </ul>
	 * @return true if Success
	 * @throws Exception
	 */
	public boolean dirRemove(String path, boolean recurse) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DirRemove", path, (recurse ? "1" : "0")));
		processResult("Remove dir: " + path, result);
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Deletes a directory/folder. Remarks : Some dir attributes can make the
	 * removal impossible.<br>
	 * (do not remove files and sub-directories), see
	 * <code>dirRemove(String, boolean)</code> for more details.<br>
	 * 
	 * @param path
	 *            - Path of the directory to remove.
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirRemove(String path) throws Exception {
		return dirRemove(path, false);
	}

	/**
	 * Copies a directory and all sub-directories and files (Similar to xcopy).<br>
	 * Remarks : If the destination directory structure doesn't exist, it will
	 * be created (if possible).<br>
	 * 
	 * @param source
	 *            - Path of the source directory (with no trailing backslash).
	 *            e.g. "C:\Path1".
	 * @param destination
	 *            - Path of the destination directory (with no trailing
	 *            backslash). e.g. "C:\Path_Copy".
	 * @param doOverWrite
	 *            - [optional] this flag determines whether to overwrite file if
	 *            they already exist:
	 *            <ul>
	 *            <li>0 = (default) do not overwrite existing files</li>
	 *            <li>1 = overwrite existing files</li>
	 *            </ul>
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirCopy(String source, String destination, boolean doOverWrite) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DirCopy", source, destination, (doOverWrite ? "1" : "0")));
		processResult("Copy directory from: \"" + source + "\" to: \"" + destination + "\"", result);
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Copies a directory and all sub-directories and files (Similar to xcopy).<br>
	 * Remarks : If the destination directory structure doesn't exist, it will
	 * be created (if possible).<br>
	 * <b>This command will not overwrite existing files, see : dirCopy(String,
	 * String, boolean) for more details.</b><br>
	 * 
	 * @param source
	 *            - Path of the source directory (with no trailing backslash).
	 *            e.g. "C:\Path1".
	 * @param destination
	 *            - Path of the destination dir (with no trailing backslash).
	 *            e.g. "C:\Path_Copy".
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirCopy(String source, String destination) throws Exception {
		return dirCopy(source, destination, false);
	}

	/**
	 * Moves a directory and all sub-directories and files.<br>
	 * <code><b>DirMove ( "source dir", "dest dir" [, flag] )</b></code><br>
	 * Remarks :
	 * <ul>
	 * <li>If the source and destination are on different volumes or UNC paths
	 * are used then a copy/delete operation will be performed rather than a
	 * move.</li>
	 * <li>If the destination already exists and the overwrite flag is specified
	 * then the source directory will be moved <b>inside</b> the destination.</li>
	 * <li>Because AutoIt lacks a "DirRename" function, use DirMove to rename a
	 * folder!</li>
	 * </ul>
	 * 
	 * @param source
	 *            - Path of the source directory (with no trailing backslash).
	 *            e.g. "C:\Path1"
	 * @param destination
	 *            - Path of the destination dir (with no trailing backslash).
	 *            e.g. "C:\Path_Copy"
	 * @param doOverWrite
	 *            - [optional] this flag determines whether to overwrite files
	 *            if they already exist:
	 *            <ul>
	 *            <li>0 = (default) do not overwrite existing files</li>
	 *            <li>1 = overwrite existing files</li>
	 *            </ul>
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirMove(String source, String destination, boolean doOverWrite) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DirMove", source, destination, (doOverWrite ? "1" : "0")));
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Moves a directory and all sub-directories and files.<br>
	 * <code><b>DirMove ( "source dir", "dest dir" [, flag] )</b></code><br>
	 * <b>This command will not overwrite existing files, see : dirMove(String,
	 * String, boolean) for more details.</b><br>
	 * Remarks :
	 * <ul>
	 * <li>If the source and destination are on different volumes or UNC paths
	 * are used then a copy/delete operation will be performed rather than a
	 * move.</li>
	 * <li>If the destination already exists and the overwrite flag is specified
	 * then the source directory will be moved <b>inside</b> the destination.</li>
	 * <li>Because AutoIt lacks a "DirRename" function, use DirMove to rename a
	 * folder!</li>
	 * </ul>
	 * 
	 * @param source
	 *            - Path of the source directory (with no trailing backslash).
	 *            e.g. "C:\Path1"
	 * @param destination
	 *            - Path of the destination dir (with no trailing backslash).
	 *            e.g. "C:\Path_Copy"
	 * @return true if success
	 * @throws Exception
	 */
	public boolean dirMove(String source, String destination) throws Exception {
		return dirMove(source, destination, false);
	}

	/**
	 * etrieve from remote agent the java.io.tmpdir system property
	 * 
	 * @return
	 * @throws Exception
	 */
	public String getRemoteTemporaryFolder() throws Exception {
		String key = "java.io.tmpdir";
		return agent.retrieveSystemProperty(key);
	}

	/**
	 * Retrieve from remote agent the user.dir system property
	 * 
	 * @return
	 * @throws Exception
	 */
	public String getRemoteUserDir() throws Exception {
		String key = "user.dir";
		return agent.retrieveSystemProperty(key);
	}

	public String getRemoteAutoItExecuter() throws Exception {
		return agent.revealAutoIt3Location();
	}

	public boolean isAutoItScriptAlive() throws Exception {
		return agent.isAutoItActive();
	}

	public void terminateRemainAutoItScripts() throws Exception {
		agent.killAutoItProcess();
	}

	public void terminateRemoteProcess(String imageName) throws Exception {
		agent.killProcess(imageName);
	}

	public boolean isProcessActive(String imageName) throws Exception {
		return agent.isProcessStillActive(imageName);
	}

	public int shutdownComputer(String switches) throws Exception {
		return agent.shutdownComputer(switches);
	}

	/**
	 * Reads a value from the registry.<br>
	 * <b><u>Example:</u><br>
	 * $var =
	 * RegRead("HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion",
	 * "ProgramFilesDir")<br>
	 * MsgBox(4096, "Program files are in:", $var)</b><br>
	 * 
	 * @param keyname
	 *            - The registry key to read.
	 * @param valuename
	 *            - The value to read.
	 * @return <ul>
	 *         <li><b>Success:</b> Returns the requested registry value. @EXTENDED
	 *         is set to the type of the value $REG_... . These types are
	 *         defined in the "Constants.au3" include file.</li>
	 *         <li><b>Failure:</b> Returns "" and sets the @error flag:
	 *         <ul>
	 *         <li>1 if unable to open requested key</li>
	 *         <li>2 if unable to open requested main key</li>
	 *         <li>3 if unable to remote connect to the registry</li>
	 *         <li>-1 if unable to open requested value</li>
	 *         <li>-2 if value type not supported</li>
	 *         </ul>
	 *         </li>
	 *         </ul>
	 * @throws Exception
	 */
	public String regRead(String keyname, String valuename) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("RegRead", keyname, valuename));
		processResult("The value of: " + keyname + ", is: " + valuename, result);
		return (String) result.get(STDOUT);
	}

	/**
	 * Creates a key or value in the registry.<br>
	 * 
	 * @param keyname
	 *            - The registry key to write to. If no other parameters are
	 *            specified this key will simply be created.
	 * @param valuename
	 *            - [optional] The valuename to write to.
	 * @param type
	 *            - [optional] Type of key to write: "REG_SZ", "REG_MULTI_SZ",
	 *            "REG_EXPAND_SZ", "REG_DWORD", "REG_QWORD", or "REG_BINARY".
	 * @param value
	 *            - [optional] The value to write.
	 * @return
	 * @throws Exception
	 */
	public boolean regWrite(String keyname, String valuename, String type, String value) throws Exception {
		Map<?, ?> result = null;
		if (valuename == null || type == null || value == null) {
			result = runRemoteScript(commandCreate("RegWrite", true, keyname));
		} else {
			result = runRemoteScript(commandCreate("RegWrite", true, keyname, valuename, type, value));
		}
		return "1".equals(result.get(STDOUT));
	}

	/**
	 * Reads the name of a subkey according to it's instance.<br>
	 * 
	 * @param keyName
	 *            - The registry key to read.
	 * @param instance
	 *            - The 1-based key instance to retrieve
	 * @return Returns the requested subkey name
	 * @throws Exception
	 */
	public String regEnumKey(String keyName, int instance) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("RegEnumKey", false, quote(keyName), instance));
		if (result.get(STDERR) != null) {
			if (!result.get(STDERR).toString().isEmpty()) {
				throw new Exception(result.get(STDERR).toString());
			}
		}
		return result.get(STDOUT).toString();
	}

	/**
	 * Reads the name of a value according to it's instance.<br>
	 * 
	 * @param keyName
	 *            - The registry key to read.
	 * @param instance
	 *            - The 1-based key instance to retrieve
	 * @return Returns the requested subkey name
	 * @throws Exception
	 */
	public String regEnumVal(String keyName, int instance) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("RegEnumVal", false, quote(keyName), instance));
		if (result.get(STDERR) != null) {
			if (!result.get(STDERR).toString().isEmpty()) {
				throw new Exception(result.get(STDERR).toString());
			}
		}
		return result.get(STDOUT).toString();
	}

	/**
	 * Deletes a key or value from the registry.<br>
	 * 
	 * @param keyname
	 *            - The registry key to delete.
	 * @param valuename
	 *            - [optional] The valuename to delete.
	 * @return
	 * @throws Exception
	 */
	public int regDelete(String keyname, String valuename) throws Exception {
		Map<?, ?> result = null;
		if (valuename == null) {
			result = runRemoteScript(commandCreate("RegDelete", true, keyname));
		} else {
			result = runRemoteScript(commandCreate("RegDelete", true, keyname, valuename));
		}
		return Integer.parseInt(result.get(STDOUT).toString());
	}

	public String getFileVersion(String fullPath) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileGetVersion", fullPath));
		if (result.get(STDERR) != null) {
			if (!result.get(STDERR).toString().isEmpty()) {
				throw new Exception(result.get(STDERR).toString());
			}
		}
		return result.get(STDOUT).toString();
	}

	/**
	 * Retrieves the details of a mapped drive.<br>
	 * <code><b>DriveMapGet ( "device" )</b></code>
	 * 
	 * @param device
	 *            - The device (drive or printer) letter to query, e.g. "O:" or
	 *            "LPT1:"
	 * @return <ul>
	 *         <li><b>Success:</b> Returns details of the mapping, e.g.
	 *         \\server\share</li>
	 *         <li><b>Failure:</b> Returns a blank string "" and sets @error to
	 *         1.</li>
	 *         </ul>
	 * @throws Exception
	 */
	public String driveMapGet(String device) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DriveMapGet", device));
		return result.get(STDOUT).toString();
	}

	/**
	 * Disconnects a network drive.<br>
	 * <code><b>DriveMapDel ( "device" )</b></code><br>
	 * <b>Remarks : </b>If a connection has no drive letter mapped you may use
	 * the connection name to disconnect, e.g. \\server\share
	 * 
	 * @param device
	 *            - The device to disconnect, e.g. "O:" or "LPT1:".
	 * @return true if success.
	 * @throws Exception
	 */
	public boolean driveMapDel(String device) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("DriveMapDel", device));
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * Maps a network drive.<br>
	 * <code><b>DriveMapAdd ( "device", "remote share" [, flags [, "user" [, "password"]]] )</b></code>
	 * 
	 * @param device
	 *            - The device to map, for example "O:" or "LPT1:". If you pass
	 *            a blank string for this parameter a connection is made but not
	 *            mapped to a specific drive. If you specify "*" an unused drive
	 *            letter will be automatically selected.
	 * @param remoteShare
	 *            - The remote share to connect to in the form "\\server\share".
	 * @param flags
	 *            - [optional] A combination of the following:
	 *            <ul>
	 *            <li>0 = default</li>
	 *            <li>1 = Persistent mapping</li>
	 *            <li>8 = Show authentication dialog if required</li>
	 *            </ul>
	 * @param user
	 *            - [optional] The username to use to connect. In the form
	 *            "username" or "domain\\username"
	 * @param password
	 *            - [optional] The password to use to connect.
	 * @return true if success.
	 * @throws Exception
	 */
	public boolean driveMapAdd(String device, String remoteShare, int flags, String user, String password)
			throws Exception {
		String commandCreate = null;
		if (flags == 0 || flags == 1 || flags == 8 || flags == 9) {
			if (!StringUtils.isEmpty(user)) {
				if (!StringUtils.isEmpty(password)) {
					commandCreate = commandCreate("DriveMapAdd", device, remoteShare, flags, user, password);
				} else {
					commandCreate = commandCreate("DriveMapAdd", device, remoteShare, flags, user);
				}
			} else {
				commandCreate = commandCreate("DriveMapAdd", device, remoteShare, flags);
			}
		} else {
			commandCreate = commandCreate("DriveMapAdd", device, remoteShare);
		}
		Map<?, ?> result = runRemoteScript(commandCreate);
		return "1".equals(result.get(STDOUT).toString());
	}

	/**
	 * {@link #driveMapAdd(String, String, int, String, String)}
	 */
	public boolean driveMapAdd(String device, String remoteShare, int flags, String user) throws Exception {
		return driveMapAdd(device, remoteShare, flags, user, null);
	}

	/**
	 * {@link #driveMapAdd(String, String, int, String, String)}
	 */
	public boolean driveMapAdd(String device, String remoteShare, int flags) throws Exception {
		return driveMapAdd(device, remoteShare, flags, null);
	}

	/**
	 * {@link #driveMapAdd(String, String, int, String, String)}
	 */
	public boolean driveMapAdd(String device, String remoteShare) throws Exception {
		return driveMapAdd(device, remoteShare, -1);
	}

	/**
	 * Execute an AutoIt file with given timeout and parameters
	 * 
	 * @param fileName
	 *            Script file full path
	 * @param timeout
	 *            The timeout for the script execution in <B>seconds</B>
	 * @param params
	 *            The script parameters
	 * @return
	 * @throws Exception
	 */
	public String executeScriptFileWithParams(String fileName, int timeout, Object... params) throws Exception {
		Vector<Object> paramsVec = new Vector<Object>();
		if (params != null && params.length > 0) {
			paramsVec.addAll(Arrays.asList(params));
		}
		Map<String, Comparable<?>> result = agent.executeAutoitFile(fileName, workDir, autoItLocation, timeout * 1000,
				paramsVec);
		String stderr = (String) result.get(STDERR);
		if (!stderr.isEmpty()) {
			throw new TimeoutException();
		}
		return (String) result.get(STDOUT);
	}

	/**
	 * Returns the long path+name of the path+name passed.
	 * 
	 * @param file
	 *            - full path and file name to convert.
	 * @param flag
	 *            - [optional] if 1 file can have relative dir, e.g.
	 *            "..\file.txt"
	 * @return Returns the long path+name of the path+name passed.
	 * @throws Exception
	 *             and Returns the parameter and sets @error to 1.
	 */
	public String fileGetLongName(String file, int flag) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileGetLongName", file, flag));
		processResult("File Get Long Name ", result);
		return result.get(STDOUT).toString();
	}

	/**
	 * Returns the long path+name of the path+name passed.
	 * 
	 * @param file
	 *            - full path and file name to convert.
	 * @return Returns the long path+name of the path+name passed.
	 * @throws Exception
	 *             and Returns the parameter and sets @error to 1.
	 */
	public String fileGetLongName(String file) throws Exception {
		Map<?, ?> result = runRemoteScript(commandCreate("FileGetLongName", file));
		processResult("File Get Long Name ", result);
		return result.get(STDOUT).toString();
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		if (!isRunAgentDirectly()) {
			if (agent instanceof AutoItRemoteInvoker) {
				((AutoItRemoteInvoker) agent).host = host;
			}
		}
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		if (!isRunAgentDirectly()) {
			if (agent instanceof AutoItRemoteInvoker) {
				((AutoItRemoteInvoker) agent).port = port;
			}
		}
		this.port = port;
	}

	public String getWorkDir() {
		return workDir;
	}

	public void setWorkDir(String workDir) {
		this.workDir = workDir;
	}

	public void setAutoItLocation(String autoItLocation) {
		this.autoItLocation = autoItLocation;
	}

	public boolean isRunAgentDirectly() {
		return runAgentDirectly;
	}

	/**
	 * If set to true, no Ftp Server will be used and no Xml-Rpc mediator
	 */
	public void setRunAgentDirectly(boolean runAgentDirectly) {
		this.runAgentDirectly = runAgentDirectly;
	}

	public void setSilentMode(boolean silentMode) throws Exception {
		this.silentMode = silentMode;
		if (agent != null) {
			agent.setSilentMode(silentMode);
		}
	}

	public int getScriptTimeout() {
		return scriptTimeout;
	}

	public void setScriptTimeout(int scriptTimeout) {
		this.scriptTimeout = scriptTimeout;
	}

	public void restoreScriptTimoutToDefault() {
		this.scriptTimeout = DEFAULT_TIME_OUT;
	}

}
