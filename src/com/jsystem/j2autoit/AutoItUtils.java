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
import java.io.FileOutputStream;

import jsystem.utils.exec.Command;
import jsystem.utils.exec.Execute;

public class AutoItUtils {
	public static CommandResult runScript(String script, String workDir, String autoItLocation,int timeout) throws Exception{
		return runScript(script, workDir, autoItLocation, timeout, false);
	}
	public static CommandResult runScript(String script, String workDir, String autoItLocation,int timeout, boolean silent) throws Exception{
		System.out.println("Executing command : " + script); 
		System.out.print("Result= "); 
		File sfile = File.createTempFile("autoit", ".au3"); //create autoit file 
		StringBuffer buf = new StringBuffer();
		buf.append("Local $var = ");
		buf.append(script);
		buf.append("\n");
		buf.append("Local $rc = @error\nConsoleWrite($var)\nExit($rc)\n");
		
		FileOutputStream out = new FileOutputStream(sfile);
		out.write(buf.toString().getBytes());
		out.close();
		
		Command cmd = new Command();
		cmd.setTimeout(timeout);
		cmd.setCmd(new String[]{autoItLocation,"/ErrorStdOut", sfile.getAbsolutePath()});
		cmd.setDir(new File(workDir));
		Execute.execute(cmd, true, !silent, true); 
		System.out.println(" ");
		System.out.println(" ");
		
		CommandResult result = new CommandResult();
		result.setScript(buf.toString());
		result.setStdout(cmd.getStdout().toString());
		result.setStderr(cmd.getStderr().toString());
		result.setReturnCode(cmd.getReturnCode());
		
		return result;
	}
	public static void main(String...args) throws Exception{
		String script = "WinWait(\"File Download\")\n" +
						"WinActivate(\"File Download\")\n" +
						"ControlClick(\"File Download\", \"\",\"[CLASS:Button; INSTANCE:2]\")\n" +
						"WinWait(\"Save As\")\n" +
						"WinActivate(\"Save As\")\n" + 
						"ControlSetText(\"Save As\", \"\", \"[CLASS:Edit; INSTANCE:1]\", \"c:\\ddd.bmp\")\n" +
						"ControlClick(\"Save As\", \"\",\"[CLASS:Button; INSTANCE:2]\")\n" +
						"WinWait(\"Download complete\")\n" +
						"WinActivate(\"Download complete\")\n" +
						"ControlClick(\"Download complete\", \"\",\"[CLASS:Button; INSTANCE:4]\")\n";
		AutoItUtils.runScript(script, "c:\\", "C:\\Program Files\\AutoIt3\\autoit3.exe", 120* 60 *1000);


	}
}
