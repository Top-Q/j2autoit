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

import junit.framework.SystemTestCase4;

import org.junit.Before;
import org.junit.Test;

import com.jsystem.j2autoit.AutoItClient;

/**
 * This is an example test that displays some of the abilities of the 
 * j2autoit driver.
 * In order to run the driver correctly please make sure that you have
 * activated the remote/local agent that will execute all commands sent 
 * from this test.
 * 
 * @author Michael Oziransky
 */
public class AutoItTest extends SystemTestCase4 {
	
	private AutoItClient autoit;
	
	@Before
	public void beforeTest() throws Exception {
		autoit = (AutoItClient)system.getSystemObject("autoit");
	}
	
	@Test
	public void runNotepad() throws Exception {				
		autoit.run("notepad.exe");
		autoit.winWait("Untitled - Notepad");
		autoit.send("Hello");
		autoit.winClose("Untitled - Notepad");
		System.out.println(autoit.winWaitActive("Notepad", "Do you want to save", 5));
		autoit.send("!n");		
	}
}
