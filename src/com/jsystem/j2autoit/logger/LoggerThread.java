/**
 * 
 */
package com.jsystem.j2autoit.logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Kobi Gana
 *
 */
public class LoggerThread extends Thread {
	private StringBuffer buffer = new StringBuffer("");
	private int fileIndex;
	private File logsDirectory = null;
	private File currentLogFile = null;
	private boolean alive = true;
	private TimeUnit TimeUnitMINUTES = TimeUnit.MINUTES;
	private int logMode = 0;
	
	public LoggerThread() {
		this("logs");
	}
	
	public LoggerThread(String logsDirectory) {
		this.logsDirectory = new File(logsDirectory);
		this.logsDirectory.mkdir();
		fileIndex = 0;
		generateLogFileName();
	}
	
	public enum LogLevel {
		NONE,
		INFO,
		WARNING,
		ERROR,
		EXCEPTION
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			while (alive) {
				TimeUnitMINUTES.sleep(1L);
				flushLog();
			}
		} catch (Exception e) {
			flushLog();
		}
	}
	
	public synchronized String getCurrentLogs() {
		return buffer.toString();
	}
	
	public synchronized void messageLog(String message) {
		appendFormatedText(LogLevel.NONE, message);
	}
	
	public synchronized void infoLog(String info) {
		appendFormatedText(LogLevel.INFO, info);
	}
	
	public synchronized void warningLog(String warning) {
		appendFormatedText(LogLevel.WARNING, warning);
	}
	
	public synchronized void errorLog(String error) {
		appendFormatedText(LogLevel.ERROR, error);
	}
	
	public synchronized void throwableLog(String error, Throwable throwable) {
		appendFormatedText(LogLevel.EXCEPTION, error);
		appedntThrowable(throwable, false);
	}
	
	public synchronized void message(String message) {
		printConsole(System.out, message);
		appendFormatedText(LogLevel.NONE, message);
	}
	
	public synchronized void info(String info) {
		printConsole(System.out, info);
		appendFormatedText(LogLevel.INFO, info);
	}
	
	public synchronized void warning(String warning) {
		printConsole(System.out, warning);
		appendFormatedText(LogLevel.WARNING, warning);
	}
	
	public synchronized void error(String error) {
		printConsole(System.err, error);
		appendFormatedText(LogLevel.ERROR, error);
	}
	
	public synchronized void throwable(String error, Throwable throwable) {
		printConsole(System.err, error);
		appendFormatedText(LogLevel.EXCEPTION, error);
		appedntThrowable(throwable, true);
	}
	
	public void flushLog() {
		if (buffer.length() > 0) {
			if (currentLogFile.length() >= 500 * 1024) {
				generateLogFileName();
			}
			FileOutputStream fileOutputStream = null;
			try {
				String stringToWrite = "";
				synchronized (this) {
					if ((stringToWrite = buffer.toString().trim()).isEmpty()) {
						return;
					}
					buffer = null;
					buffer = new StringBuffer("");
				}
				fileOutputStream = new FileOutputStream(currentLogFile, true);
				fileOutputStream.write(stringToWrite.getBytes("UTF-8"));
			} catch (Exception exception) {
			} finally {
				if (fileOutputStream != null) {
					try {
						fileOutputStream.close();
					} catch (Exception exception2) {
					}
				}
			}
		}
	}
	
	public synchronized void closeLog() {
		alive = false;
		flushLog();
	}

	public void setLogMode(int logMode) {
		this.logMode = logMode;
	}
	
	private void printConsole(PrintStream printStream, String text) {
		if (logMode == 0 || logMode == 2) {
			printStream.print(text);
		}
	}
	
	private void appendFormatedText(LogLevel level, String text) {
		if (logMode == 0 || logMode == 1) {
			buffer.append(getCurrentDate());
			if (LogLevel.NONE != level) {
				buffer.append(" - ").append(level);
			}
			buffer.append(" :\n").append(text);
		}
	}
	
	private void appedntThrowable(Throwable throwable, boolean printStack) {
		if (printStack && (logMode == 0 || logMode == 2)) {
			throwable.printStackTrace();
		}
		if (logMode == 0 || logMode == 1) {
			StringWriter stringWriter = new StringWriter();
			throwable.printStackTrace(new PrintWriter(stringWriter));
			buffer.append(stringWriter.getBuffer());
		}
	}
	
	private String getCurrentDate() {
		return (new Date(System.currentTimeMillis())).toString();
	}
	
	private void generateLogFileName() {
		while ((currentLogFile = new File(logsDirectory, "log." + (fileIndex++) + ".txt")).exists()) {
		}
	}
	
}
