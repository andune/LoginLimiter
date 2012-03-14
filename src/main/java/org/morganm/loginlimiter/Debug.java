/**
 * 
 */
package org.morganm.loginlimiter;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * @author morganm
 *
 */
public class Debug {
	// class version: 6
	private static Debug instance = null;
	
	private Logger pluginLog;
	private String logPrefix;
	private Logger debugLog;
	private boolean useConsoleLogger = true;
	private boolean debug = false;
	private Level oldConsoleLevel = null;
	private Level oldLogLevel = null;
	
	public void init(Logger log, String logPrefix, boolean isDebug) {
		this.pluginLog = log;
		this.logPrefix = logPrefix;
		this.debugLog = log;
		this.useConsoleLogger = true;
		setDebug(isDebug);
	}
	public void init(Logger log, String logPrefix, String logFileName, boolean isDebug) {
		this.pluginLog = log;
		this.logPrefix = logPrefix;
		this.useConsoleLogger = false;
		
		setDebugFile(log.getName()+".debug", logFileName);
		setDebug(isDebug);
	}
	
	private void setDebugFile(String loggerName, String fileName) {
		try {
			debugLog = Logger.getLogger(loggerName);
			
			// remove any existing handlers, this avoids multiple handlers
			// being added on plugin reload
			Handler[] handlers = debugLog.getHandlers();
			for(int i=0; i < handlers.length; i++) {
				handlers[i].close();
				debugLog.removeHandler(handlers[i]);
			}
			
			FileHandler handler = new FileHandler(fileName, true);
			DebugFormatter formatter = new DebugFormatter();
			handler.setFormatter(formatter);
			
			debugLog.addHandler(handler);
		}
		catch(IOException e) {
			String warning = "Error in setDebugFile: "+e.getMessage();
			if( pluginLog != null )
				pluginLog.warning(logPrefix+warning);
			else
				System.out.println(warning);
		}
	}
	
//	private void enableFileLogger() {
//		log.getName();
//	}
	
	private void setConsoleLevel(Level level) {
		if( level == null )
			return;
		
		Handler handler = getConsoleHandler(pluginLog);
		if( handler != null ) {
			oldConsoleLevel = handler.getLevel();
			handler.setLevel(level);
		}
	}
	private Handler getConsoleHandler(Logger log) {
		Handler[] handlers = log.getHandlers();
		for(int i=0; i < handlers.length; i++)
			if( handlers[i] instanceof ConsoleHandler )
				return handlers[i];

		Logger parent = log.getParent();
		if( parent != null )
			return getConsoleHandler(parent);
		else
			return null;
	}
	
	public void setDebug(boolean isDebug) {
		setDebug(isDebug, Level.FINE);
	}
	public void setDebug(boolean isDebug, Level level) {
		// do nothing if we haven't been initialized with a valid logger yet
		if( pluginLog == null )
			return;
		// do nothing if flag hasn't changed
		if( this.debug == isDebug )
			return;
		
		this.debug = isDebug;
		
		if( isDebug ) {
			oldLogLevel = debugLog.getLevel();
			debugLog.setLevel(level);
			pluginLog.info(logPrefix + "DEBUGGING ENABLED");
			if( useConsoleLogger ) {
				setConsoleLevel(level);
				pluginLog.info(logPrefix + "(CONSOLE DEBUG LOGGING ENABLED)");
			}
		}
		else {
			debugLog.setLevel(oldLogLevel);
			pluginLog.info(logPrefix + "DEBUGGING DISABLED");
			
			if( useConsoleLogger ) {
				setConsoleLevel(oldConsoleLevel);
				pluginLog.info(logPrefix + "(CONSOLE DEBUG LOGGING DISABLED)");
			}
		}
	}
	public boolean isDebug() { return debug; }
	public boolean isDevDebug() { return debug && debugLog.isLoggable(Level.FINEST); }
	
	public static Debug getInstance() {
		if( instance == null ) {
			synchronized(Debug.class) {
				if( instance == null )
					instance = new Debug();
			}
		}
		
		return instance;
	}
	
	/** This method takes varargs String elements and if debugging is true, it will concat
	 * them together and print out a debug message. This saves on debug overhead processing
	 * of the form:
	 * 
	 * debug("Some string"+someValue+", some other string "+someOtherValue);
	 * 
	 * since in that form, the StringBuilder() addition must be incurred for every debug
	 * call, even if debugging is off. With this method, the above statement becomes:
	 * 
	 * debug("Some string", someValue, ", some other string ", someOtherValue);
	 * 
	 * and the StringBuilder() penalty is only incurred if debugging is actually enabled.
	 * This reduces the penalty of debugs (when debugging is off) to simply that of
	 * a function call and a single if check. Not quite as good as C #ifdef preprocessing
	 * but it's about as close as we can get under Java.
	 * 
	 * If you look at the below method and find yourself thinking "that seems less
	 * efficient than just letting java do the addition", then you don't know much about
	 * Java strings. Java converts string addition (within the same literal expression)
	 * internally into StringBuilder() addition using exactly the same method calls as
	 * below.
	 * 
	 * @param msg
	 * @param args
	 */
	public void debug(Object...args) {
		if( debug ) {
			StringBuilder sb = new StringBuilder(logPrefix);
			for(int i=0; i<args.length;i++) {
				sb.append(args[i]);
			}
			
			debugLog.fine(sb.toString());
		}
	}
	
	public void devDebug(Object...args) {
		if( debug && debugLog.isLoggable(Level.FINEST) ) {
			StringBuilder sb = new StringBuilder(logPrefix);
			for(int i=0; i<args.length;i++) {
				sb.append(args[i]);
			}

			debugLog.finest(sb.toString());
		}
	}
	
	private class DebugFormatter extends Formatter {
		private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		private final String newLine = System.getProperty("line.separator");
		
		@Override
		public String format(LogRecord record) {
			StringBuilder sb = new StringBuilder(200);
			sb.append("[");
			sb.append(dateFormat.format(new Date(record.getMillis())));
			sb.append("] ");
			sb.append(record.getMessage());
			sb.append(newLine);
			return sb.toString();
		}
		
	}
}
