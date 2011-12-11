/**
 * 
 */
package org.morganm.loginlimiter;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author morganm
 *
 */
public class Debug {
	private static Debug instance = null;
	
	private Logger log;
	private String logPrefix;
	private boolean debug = false;
	private Level oldConsoleLevel = null;
	private Level oldLogLevel = null;
	
	public void init(Logger log, String logPrefix, boolean isDebug) {
		this.log = log;
		this.logPrefix = logPrefix; 
		setDebug(isDebug);
	}
	
	private void enableFileLogger() {
		log.getName();
	}
	
	private void setConsoleLevel(Level level) {
		if( level == null )
			return;
		
		Handler handler = getConsoleHandler(log);
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
		if( log == null )
			return;
		// do nothing if flag hasn't changed
		if( this.debug == isDebug )
			return;
		
		this.debug = isDebug;
		
		if( isDebug ) {
			oldLogLevel = log.getLevel();
			log.setLevel(level);
			setConsoleLevel(level);
			debug("DEBUGGING ENABLED");
		}
		else {
			debug("DEBUGGING DISABLED");
			setConsoleLevel(oldConsoleLevel);
			log.setLevel(oldLogLevel);
		}
	}
	public boolean isDebug() { return debug; }
	
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
	 * since in that form, the StringBuffer() addition must be incurred for every debug
	 * call, even if debugging is off. With this method, the above statement becomes:
	 * 
	 * debug("Some string", someValue, ", some other string ", someOtherValue);
	 * 
	 * and the StringBuffer() penalty is only incurred if debugging is actually enabled.
	 * This reduces the penalty of debugs (when debugging is off) to simply that of
	 * a function call and a single if check. Not quite as good as C #ifdef preprocessing
	 * but it's about as close as we can get under Java.
	 * 
	 * If you look at the below method and find yourself thinking "that seems less
	 * efficient than just letting java do the addition", then you don't know much about
	 * Java strings. Java converts string addition (within the same literal expression)
	 * internally into StringBuffer() addition using exactly the same method calls as
	 * below.
	 * 
	 * @param msg
	 * @param args
	 */
	public void debug(Object...args) {
		if( debug ) {
			StringBuffer sb = new StringBuffer(logPrefix);
			for(int i=0; i<args.length;i++) {
				sb.append(args[i]);
			}
			
			log.fine(sb.toString());
		}
	}
	
	public void devDebug(Object...args) {
		if( debug && log.isLoggable(Level.FINEST) ) {
			StringBuffer sb = new StringBuffer(logPrefix);
			for(int i=0; i<args.length;i++) {
				sb.append(args[i]);
			}

			log.finest(sb.toString());
		}
	}
}
