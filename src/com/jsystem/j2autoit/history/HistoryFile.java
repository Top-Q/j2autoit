/**
 * 
 */
package com.jsystem.j2autoit.history;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 *
 */
public class HistoryFile {
	private static HistoryFileThread historyFileThread = null;
	
	public static void init() {
		if (historyFileThread == null) {
			synchronized (HistoryFile.class) {
				if (historyFileThread == null) {
					historyFileThread = new HistoryFileThread();
					historyFileThread.start();
				}
			}
		}
	}
	
	public static void close() {
		if (historyFileThread != null) {
			synchronized (HistoryFile.class) {
				if (historyFileThread != null) {
					historyFileThread.shutdownNow();
					historyFileThread.forceDeleteAll();
					historyFileThread = null;
				}
			}
		}
	}
	
	public static void shutdownNow() {
		if (historyFileThread != null) {
			historyFileThread.shutdownNow();
		}
	}
	
	public static void setSleepTime(TimeUnit sleepTimeUnit, long sleepTimeOut) {
		if (historyFileThread != null) {
			historyFileThread.setSleepTime(sleepTimeUnit, sleepTimeOut);
		}
	}
	
	public static void addFile(File fileName) {
		addFile(fileName.getAbsolutePath());
	}
	
	public static void addFile(String fileName) {
		if (historyFileThread != null) {
			historyFileThread.addFile(fileName);
		}
	}
	
	public static void setHistory_Size(int size) {
		if (historyFileThread != null) {
			historyFileThread.setHistory_Size(size);
		}
	}
	
	public static int getHistory_Size() {
		return historyFileThread == null ? 1000 : historyFileThread.getHistory_Size();
	}
	
	public static void forceDeleteAll() {
		if (historyFileThread != null) {
			historyFileThread.forceDeleteAll();
		}
	}
	
	public static boolean containEntries() {
		return historyFileThread == null ? false : historyFileThread.containEntries();
	}
	
}
