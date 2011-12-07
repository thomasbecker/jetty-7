// ========================================================================
// Copyright (c) 2004-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses.
// ========================================================================

package org.eclipse.jetty.util.log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.Loader;

/**
 * Logging. This class provides a static logging interface. If an instance of the org.slf4j.Logger class is found on the classpath, the static log methods are
 * directed to a slf4j logger for "org.eclipse.log". Otherwise the logs are directed to stderr.
 * <p>
 * The "org.eclipse.jetty.util.log.class" system property can be used to select a specific logging implementation.
 * <p>
 * If the system property org.eclipse.jetty.util.log.IGNORED is set, then ignored exceptions are logged in detail.
 *
 * @see StdErrLog
 * @see Slf4jLog
 */
public class Log
{
    public final static String EXCEPTION = "EXCEPTION ";
    public final static String IGNORED = "IGNORED ";

    /**
     * Logging Configuration Properties
     */
    protected static Properties __props;
    /**
     * The {@link Logger} implementation class name
     */
    public static String __logClass;
    /**
     * Legacy flag indicating if {@link Log#ignore(Throwable)} methods produce any output in the {@link Logger}s
     */
    public static boolean __ignored;

    /**
     * Map of configured loggers. Needs to be package private to be able to reset it in unit tests.
     */
    static ConcurrentMap<String, Logger> _loggers;

    private static Logger LOG;

    private static boolean __initialized;

    private static Set<LoggerListener> _loggerListeners;

    static
    {
        _loggers = new ConcurrentHashMap<String, Logger>();
        _loggerListeners = new HashSet<LoggerListener>();

        /*
         * Instantiate a default configuration properties (empty)
         */
        __props = new Properties();

        AccessController.doPrivileged(new PrivilegedAction<Object>()
        {
            public Object run()
            {
                /*
                 * First see if the jetty-logging.properties object exists in the classpath. This is an optional feature used by embedded mode use, and test
                 * cases to allow for early configuration of the Log class in situations where access to the System.properties are either too late or just
                 * impossible.
                 */
                URL testProps = Log.class.getClassLoader().getResource("jetty-logging.properties");
                if (testProps != null)
                {
                    InputStream in = null;
                    try
                    {
                        in = testProps.openStream();
                        __props.load(in);
                    }
                    catch (IOException e)
                    {
                        System.err.println("Unable to load " + testProps);
                        e.printStackTrace(System.err);
                    }
                    finally
                    {
                        IO.close(in);
                    }
                }

                /*
                 * Now load the System.properties as-is into the __props, these values will override any key conflicts in __props.
                 */
                @SuppressWarnings("unchecked")
                Enumeration<String> systemKeyEnum = (Enumeration<String>)System.getProperties().propertyNames();
                while (systemKeyEnum.hasMoreElements())
                {
                    String key = systemKeyEnum.nextElement();
                    __props.setProperty(key,System.getProperty(key));
                }

                /*
                 * Now use the configuration properties to configure the Log statics
                 */
                __logClass = __props.getProperty("org.eclipse.jetty.util.log.class","org.eclipse.jetty.util.log.Slf4jLog");
                __ignored = Boolean.parseBoolean(__props.getProperty("org.eclipse.jetty.util.log.IGNORED","false"));
                return null;
            }
        });
    }

    public static boolean initialized()
    {
        if (LOG != null)
        {
            return true;
        }

        synchronized (Log.class)
        {
            if (__initialized)
            {
                return LOG != null;
            }
            __initialized = true;
        }

        try
        {
            Class<?> log_class = Loader.loadClass(Log.class,__logClass);
            if (LOG == null || !LOG.getClass().equals(log_class))
            {
                LOG = (Logger)log_class.newInstance();
                LOG.debug("Logging to {} via {}",LOG,log_class.getName());
            }
        }
        catch (Throwable e)
        {
            // Unable to load specified Logger implementation, default to standard logging.
            initStandardLogging(e);
        }

        return LOG != null;
    }

    private static void initStandardLogging(Throwable e)
    {
        Class<?> log_class;
        if (e != null && __ignored)
        {
            e.printStackTrace();
        }

        if (LOG == null)
        {
            log_class = StdErrLog.class;
            LOG = new StdErrLog();
            LOG.debug("Logging to {} via {}",LOG,log_class.getName());
        }
    }

    public static void setLog(Logger log)
    {
        Log.LOG = log;
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static Logger getLog()
    {
        initialized();
        return LOG;
    }

    /**
     * Get the root logger.
     *
     * @return the root logger
     */
    public static Logger getRootLogger()
    {
        initialized();
        return LOG;
    }

    static boolean isIgnored()
    {
        return __ignored;
    }

    /**
     * Set Log to parent Logger.
     * <p>
     * If there is a different Log class available from a parent classloader, call {@link #getLogger(String)} on it and construct a {@link LoggerLog} instance
     * as this Log's Logger, so that logging is delegated to the parent Log.
     * <p>
     * This should be used if a webapp is using Log, but wishes the logging to be directed to the containers log.
     * <p>
     * If there is not parent Log, then this call is equivalent to
     *
     * <pre>
     * Log.setLog(Log.getLogger(name));
     * </pre>
     *
     * @param name
     *            Logger name
     */
    public static void setLogToParent(String name)
    {
        ClassLoader loader = Log.class.getClassLoader();
        if (loader.getParent() != null)
        {
            try
            {
                Class<?> uberlog = loader.getParent().loadClass("org.eclipse.jetty.util.log.Log");
                Method getLogger = uberlog.getMethod("getLogger",new Class[]
                { String.class });
                Object logger = getLogger.invoke(null,name);
                setLog(new LoggerLog(logger));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            setLog(getLogger(name));
        }
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void debug(Throwable th)
    {
        if (!isDebugEnabled())
            return;
        LOG.debug(EXCEPTION,th);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void debug(String msg)
    {
        if (!initialized())
            return;
        LOG.debug(msg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void debug(String msg, Object arg)
    {
        if (!initialized())
            return;
        LOG.debug(msg,arg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void debug(String msg, Object arg0, Object arg1)
    {
        if (!initialized())
            return;
        LOG.debug(msg,arg0,arg1);
    }

    /**
     * Ignore an exception unless trace is enabled. This works around the problem that log4j does not support the trace level.
     *
     * @param thrown
     *            the Throwable to ignore
     */
    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void ignore(Throwable thrown)
    {
        if (!initialized())
            return;
        LOG.ignore(thrown);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void info(String msg)
    {
        if (!initialized())
            return;
        LOG.info(msg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void info(String msg, Object arg)
    {
        if (!initialized())
            return;
        LOG.info(msg,arg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void info(String msg, Object arg0, Object arg1)
    {
        if (!initialized())
            return;
        LOG.info(msg,arg0,arg1);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static boolean isDebugEnabled()
    {
        if (!initialized())
            return false;
        return LOG.isDebugEnabled();
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void warn(String msg)
    {
        if (!initialized())
            return;
        LOG.warn(msg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void warn(String msg, Object arg)
    {
        if (!initialized())
            return;
        LOG.warn(msg,arg);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void warn(String msg, Object arg0, Object arg1)
    {
        if (!initialized())
            return;
        LOG.warn(msg,arg0,arg1);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void warn(String msg, Throwable th)
    {
        if (!initialized())
            return;
        LOG.warn(msg,th);
    }

    /**
     * @deprecated anonymous logging is deprecated, use a named {@link Logger} obtained from {@link #getLogger(String)}
     */
    @Deprecated
    public static void warn(Throwable th)
    {
        if (!initialized())
            return;
        LOG.warn(EXCEPTION,th);
    }

    /**
     * Obtain a named Logger based on the fully qualified class name.
     *
     * @param clazz
     *            the class to base the Logger name off of
     * @return the Logger with the given name
     */
    public static Logger getLogger(Class<?> clazz)
    {
        return getLogger(clazz.getName());
    }

    /**
     * Obtain a named Logger or the default Logger if null is passed.
     *
     * @param name
     *            the Logger name
     * @return the Logger with the given name
     */
    public static Logger getLogger(String name)
    {
        if (!initialized())
            return null;

        if (name == null)
        {
            return LOG;
        }
        else
        {
            Logger logger = _loggers.get(name);
            if (logger == null)
            {
                logger = createNewLogger(name);
            }
            return logger;
        }
    }

    private static Logger createNewLogger(String name)
    {
        Logger logger = LOG.getLogger(name);
        _loggers.putIfAbsent(name,logger);
        callLoggerListeners(logger);
        return logger;
    }

    private static void callLoggerListeners(Logger logger)
    {
        for (LoggerListener listener : _loggerListeners)
        {
            listener.addLogger(logger);
        }
    }

    /**
     * Add a listener which is called whenever new loggers are configured
     * @param listener
     *                the listener to add
     */
    public static void addLoggerListener(LoggerListener listener){
        listener.initialize(_loggers);
        _loggerListeners.add(listener);
    }
}
