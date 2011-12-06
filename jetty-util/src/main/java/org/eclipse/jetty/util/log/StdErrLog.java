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

import java.io.PrintStream;
import java.security.AccessControlException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jetty.util.DateCache;

/**
 * StdErr Logging. This implementation of the Logging facade sends all logs to StdErr with minimal formatting.
 * <p>
 * If the system property "org.eclipse.jetty.LEVEL" is set to one of the following (ALL, DEBUG, INFO, WARN), then set the eclipse jetty root level logger level
 * to that specified level. (Default level is INFO)
 * <p>
 * If the system property "org.eclipse.jetty.util.log.SOURCE" is set, then the source method/file of a log is logged. For named debuggers, the system property
 * name+".SOURCE" is checked. If it is not not set, then "org.eclipse.jetty.util.log.SOURCE" is used as the default.
 * <p>
 * If the system property "org.eclipse.jetty.util.log.LONG" is set, then the full, unabbreviated name of the logger is used for logging. For named debuggers,
 * the system property name+".LONG" is checked. If it is not not set, then "org.eclipse.jetty.util.log.LONG" is used as the default.
 */
public class StdErrLog implements Logger
{
    private static final String EOL = System.getProperty("line.separator");
    private static DateCache _dateCache;
    private static Properties __props = Log.__props;

    private final static boolean __source = Boolean.parseBoolean(Log.__props.getProperty("org.eclipse.jetty.util.log.SOURCE",
            Log.__props.getProperty("org.eclipse.jetty.util.log.stderr.SOURCE","false")));
    private final static boolean __long = Boolean.parseBoolean(Log.__props.getProperty("org.eclipse.jetty.util.log.stderr.LONG","false"));

    /**
     * Tracking for child loggers only.
     */
    private final static ConcurrentMap<String, StdErrLog> __loggers = new ConcurrentHashMap<String, StdErrLog>();

    static
    {
        String deprecatedProperties[] =
        { "DEBUG", "org.eclipse.jetty.util.log.DEBUG", "org.eclipse.jetty.util.log.stderr.DEBUG" };

        // Toss a message to users about deprecated system properties
        for (String deprecatedProp : deprecatedProperties)
        {
            if (System.getProperty(deprecatedProp) != null)
            {
                System.err.printf("System Property [%s] has been deprecated! (Use org.eclipse.jetty.LEVEL=DEBUG instead)%n",deprecatedProp);
            }
        }

        try
        {
            _dateCache = new DateCache("yyyy-MM-dd HH:mm:ss");
        }
        catch (Exception x)
        {
            x.printStackTrace(System.err);
        }
    }

    public static final int LEVEL_ALL = 0;
    public static final int LEVEL_DEBUG = 1;
    public static final int LEVEL_INFO = 2;
    public static final int LEVEL_WARN = 3;

    private int _level = LEVEL_INFO;
    // Level that this Logger was configured as (remembered in special case of .setDebugEnabled())
    private int _configuredLevel;
    private PrintStream _stderr = System.err;
    private boolean _source = __source;
    // Print the long form names, otherwise use abbreviated
    private boolean _printLongNames = __long;
    // The full log name, as provided by the system.
    private final String _name;
    // The abbreviated log name (used by default, unless _long is specified)
    private final String _abbrevname;
    private boolean _hideStacks = false;

    public StdErrLog()
    {
        this(null);
    }

    public StdErrLog(String name)
    {
        this(name,__props);
    }

    public StdErrLog(String name, Properties props)
    {
        __props = props;
        this._name = name == null?"":name;
        this._abbrevname = condensePackageString(this._name);
        this._level = getLoggingLevel(props,this._name);
        this._configuredLevel = this._level;

        try
        {
            _source = Boolean.parseBoolean(props.getProperty(_name + ".SOURCE",Boolean.toString(_source)));
        }
        catch (AccessControlException ace)
        {
            _source = __source;
        }
    }

    /**
     * Get the Logging Level for the provided log name. Using the FQCN first, then each package segment from longest to shortest.
     *
     * @param props
     *            the properties to check
     * @param name
     *            the name to get log for
     * @return the logging level
     */
    public static int getLoggingLevel(Properties props, final String name)
    {
        // Calculate the level this named logger should operate under.
        // Checking with FQCN first, then each package segment from longest to shortest.
        String nameSegment = name;

        while ((nameSegment != null) && (nameSegment.length() > 0))
        {
            String levelStr = props.getProperty(nameSegment + ".LEVEL");
            // System.err.printf("[StdErrLog.CONFIG] Checking for property [%s.LEVEL] = %s%n",nameSegment,levelStr);
            int level = getLevelId(nameSegment + ".LEVEL",levelStr);
            if (level != (-1))
            {
                return level;
            }

            // Trim and try again.
            int idx = nameSegment.lastIndexOf('.');
            if (idx >= 0)
            {
                nameSegment = nameSegment.substring(0,idx);
            }
            else
            {
                nameSegment = null;
            }
        }

        // Default Logging Level
        return getLevelId("log.LEVEL",props.getProperty("log.LEVEL","INFO"));
    }

    protected static int getLevelId(String levelSegment, String levelName)
    {
        if (levelName == null)
        {
            return -1;
        }
        String levelStr = levelName.trim();
        if ("ALL".equalsIgnoreCase(levelStr))
        {
            return LEVEL_ALL;
        }
        else if ("DEBUG".equalsIgnoreCase(levelStr))
        {
            return LEVEL_DEBUG;
        }
        else if ("INFO".equalsIgnoreCase(levelStr))
        {
            return LEVEL_INFO;
        }
        else if ("WARN".equalsIgnoreCase(levelStr))
        {
            return LEVEL_WARN;
        }

        System.err.println("Unknown StdErrLog level [" + levelSegment + "]=[" + levelStr + "], expecting only [ALL, DEBUG, INFO, WARN] as values.");
        return -1;
    }

    /**
     * Condenses a classname by stripping down the package name to just the first character of each package name segment.Configured
     * <p>
     *
     * <pre>
     * Examples:
     * "org.eclipse.jetty.test.FooTest"           = "oejt.FooTest"
     * "org.eclipse.jetty.server.logging.LogTest" = "orjsl.LogTest"
     * </pre>
     *
     * @param classname
     *            the fully qualified class name
     * @return the condensed name
     */
    protected static String condensePackageString(String classname)
    {
        String parts[] = classname.split("\\.");
        StringBuilder dense = new StringBuilder();
        for (int i = 0; i < (parts.length - 1); i++)
        {
            dense.append(parts[i].charAt(0));
        }
        if (dense.length() > 0)
        {
            dense.append('.');
        }
        dense.append(parts[parts.length - 1]);
        return dense.toString();
    }

    public String getName()
    {
        return _name;
    }

    public void setPrintLongNames(boolean printLongNames)
    {
        this._printLongNames = printLongNames;
    }

    public boolean isPrintLongNames()
    {
        return this._printLongNames;
    }

    public boolean isHideStacks()
    {
        return _hideStacks;
    }

    public void setHideStacks(boolean hideStacks)
    {
        _hideStacks = hideStacks;
    }

    /* ------------------------------------------------------------ */
    /**
     * Is the source of a log, logged
     *
     * @return true if the class, method, file and line number of a log is logged.
     */
    public boolean isSource()
    {
        return _source;
    }

    /* ------------------------------------------------------------ */
    /**
     * Set if a log source is logged.
     *
     * @param source
     *            true if the class, method, file and line number of a log is logged.
     */
    public void setSource(boolean source)
    {
        _source = source;
    }

    public void warn(String msg, Object... args)
    {
        if (_level <= LEVEL_WARN)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":WARN:",msg,args);
            _stderr.println(buffer);
        }
    }

    public void warn(Throwable thrown)
    {
        warn("",thrown);
    }

    public void warn(String msg, Throwable thrown)
    {
        if (_level <= LEVEL_WARN)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":WARN:",msg,thrown);
            _stderr.println(buffer);
        }
    }

    public void info(String msg, Object... args)
    {
        if (_level <= LEVEL_INFO)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":INFO:",msg,args);
            _stderr.println(buffer);
        }
    }

    public void info(Throwable thrown)
    {
        info("",thrown);
    }

    public void info(String msg, Throwable thrown)
    {
        if (_level <= LEVEL_INFO)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":INFO:",msg,thrown);
            _stderr.println(buffer);
        }
    }

    public boolean isDebugEnabled()
    {
        return (_level <= LEVEL_DEBUG);
    }

    /**
     * Legacy interface where a programmatic configuration of the logger level is done as a wholesale approach.
     */
    public void setDebugEnabled(boolean enabled)
    {
        if (enabled)
        {
            synchronized (__loggers)
            {
                this._level = LEVEL_DEBUG;

                // Boot stomp all cached log levels to DEBUG
                for (StdErrLog log : __loggers.values())
                {
                    log._level = LEVEL_DEBUG;
                }
            }
        }
        else
        {
            synchronized (__loggers)
            {
                this._level = this._configuredLevel;

                // restore all cached log configured levels
                for (StdErrLog log : __loggers.values())
                {
                    log._level = log._configuredLevel;
                }
            }
        }
    }

    public int getIntLevel()
    {
        return _level;
    }

    public String getLevel()
    {
        switch (_level)
        {
            case 0:
                return "ALL";
            case 1:
                return "DEBUG";
            case 2:
                return "INFO";
            case 3:
                return "WARN";
            default:
                throw new IllegalStateException("_level is set to an unknwon loglevel. Should never happen");
        }
    }

    public void setLevel(Level level)
    {
        switch (level)
        {
            case ALL:
                _level = 0;
                break;
            case DEBUG:
                _level = 1;
                break;
            case INFO:
                _level = 2;
                break;
            case ERROR:
                _level = 3;
                break;
            case FATAL:
                _level = 3;
                break;
            case WARN:
                _level = 3;
                break;
            case OFF:
                _level = 3;
                break;
        }
    }

    /**
     * Set the level for this logger.
     * <p>
     * Available values ({@link StdErrLog#LEVEL_ALL}, {@link StdErrLog#LEVEL_DEBUG}, {@link StdErrLog#LEVEL_INFO}, {@link StdErrLog#LEVEL_WARN})
     *
     * @param level
     *            the level to set the logger to
     */
    public void setLevel(int level)
    {
        this._level = level;
    }

    public void setStdErrStream(PrintStream stream)
    {
        this._stderr = stream;
    }

    public void debug(String msg, Object... args)
    {
        if (_level <= LEVEL_DEBUG)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":DBUG:",msg,args);
            _stderr.println(buffer);
        }
    }

    public void debug(Throwable thrown)
    {
        debug("",thrown);
    }

    public void debug(String msg, Throwable thrown)
    {
        if (_level <= LEVEL_DEBUG)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":DBUG:",msg,thrown);
            _stderr.println(buffer);
        }
    }

    private void format(StringBuilder buffer, String level, String msg, Object... args)
    {
        String d = _dateCache.now();
        int ms = _dateCache.lastMs();
        tag(buffer,d,ms,level);
        format(buffer,msg,args);
    }

    private void format(StringBuilder buffer, String level, String msg, Throwable thrown)
    {
        format(buffer,level,msg);
        if (isHideStacks())
        {
            format(buffer,String.valueOf(thrown));
        }
        else
        {
            format(buffer,thrown);
        }
    }

    private void tag(StringBuilder buffer, String d, int ms, String tag)
    {
        buffer.setLength(0);
        buffer.append(d);
        if (ms > 99)
        {
            buffer.append('.');
        }
        else if (ms > 9)
        {
            buffer.append(".0");
        }
        else
        {
            buffer.append(".00");
        }
        buffer.append(ms).append(tag);
        if (_printLongNames)
        {
            buffer.append(_name);
        }
        else
        {
            buffer.append(_abbrevname);
        }
        buffer.append(':');
        if (_source)
        {
            Throwable source = new Throwable();
            StackTraceElement[] frames = source.getStackTrace();
            for (int i = 0; i < frames.length; i++)
            {
                final StackTraceElement frame = frames[i];
                String clazz = frame.getClassName();
                if (clazz.equals(StdErrLog.class.getName()) || clazz.equals(Log.class.getName()))
                {
                    continue;
                }
                if (!_printLongNames && clazz.startsWith("org.eclipse.jetty."))
                {
                    buffer.append(condensePackageString(clazz));
                }
                else
                {
                    buffer.append(clazz);
                }
                buffer.append('#').append(frame.getMethodName());
                if (frame.getFileName() != null)
                {
                    buffer.append('(').append(frame.getFileName()).append(':').append(frame.getLineNumber()).append(')');
                }
                buffer.append(':');
                break;
            }
        }
    }

    private void format(StringBuilder builder, String msg, Object... args)
    {
        if (msg == null)
        {
            msg = "";
            for (int i = 0; i < args.length; i++)
            {
                msg += "{} ";
            }
        }
        String braces = "{}";
        int start = 0;
        for (Object arg : args)
        {
            int bracesIndex = msg.indexOf(braces,start);
            if (bracesIndex < 0)
            {
                escape(builder,msg.substring(start));
                builder.append(" ");
                builder.append(arg);
                start = msg.length();
            }
            else
            {
                escape(builder,msg.substring(start,bracesIndex));
                builder.append(String.valueOf(arg));
                start = bracesIndex + braces.length();
            }
        }
        escape(builder,msg.substring(start));
    }

    private void escape(StringBuilder builder, String string)
    {
        for (int i = 0; i < string.length(); ++i)
        {
            char c = string.charAt(i);
            if (Character.isISOControl(c))
            {
                if (c == '\n')
                {
                    builder.append('|');
                }
                else if (c == '\r')
                {
                    builder.append('<');
                }
                else
                {
                    builder.append('?');
                }
            }
            else
            {
                builder.append(c);
            }
        }
    }

    private void format(StringBuilder buffer, Throwable thrown)
    {
        if (thrown == null)
        {
            buffer.append("null");
        }
        else
        {
            buffer.append(EOL);
            format(buffer,thrown.toString());
            StackTraceElement[] elements = thrown.getStackTrace();
            for (int i = 0; elements != null && i < elements.length; i++)
            {
                buffer.append(EOL).append("\tat ");
                format(buffer,elements[i].toString());
            }

            Throwable cause = thrown.getCause();
            if (cause != null && cause != thrown)
            {
                buffer.append(EOL).append("Caused by: ");
                format(buffer,cause);
            }
        }
    }

    /**
     * A more robust form of name blank test. Will return true for null names, and names that have only whitespace
     *
     * @param name
     *            the name to test
     * @return true for null or blank name, false if any non-whitespace character is found.
     */
    private static boolean isBlank(String name)
    {
        if (name == null)
        {
            return true;
        }
        int size = name.length();
        char c;
        for (int i = 0; i < size; i++)
        {
            c = name.charAt(i);
            if (!Character.isWhitespace(c))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a Child Logger relative to this Logger.
     *
     * @param name
     *            the child name
     * @return the appropriate child logger (if name specified results in a new unique child)
     */
    public Logger getLogger(String name)
    {
        if (isBlank(name))
        {
            return this;
        }

        String fullname = name;
        if (!isBlank(_name))
        {
            fullname = _name + "." + name;
        }

        StdErrLog logger = __loggers.get(fullname);
        if (logger == null)
        {
            StdErrLog sel = new StdErrLog(fullname);
            // Preserve configuration for new loggers configuration
            sel.setPrintLongNames(_printLongNames);
            // Let Level come from configured Properties instead - sel.setLevel(_level);
            sel.setSource(_source);
            sel._stderr = this._stderr;
            logger = __loggers.putIfAbsent(fullname,sel);
            if (logger == null)
            {
                logger = sel;
            }
        }

        return logger;
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append("StdErrLog:");
        s.append(_name);
        s.append(":LEVEL=");
        switch (_level)
        {
            case LEVEL_ALL:
                s.append("ALL");
                break;
            case LEVEL_DEBUG:
                s.append("DEBUG");
                break;
            case LEVEL_INFO:
                s.append("INFO");
                break;
            case LEVEL_WARN:
                s.append("WARN");
                break;
            default:
                s.append("?");
                break;
        }
        return s.toString();
    }

    public static void setProperties(Properties props)
    {
        __props = props;
    }

    public void ignore(Throwable ignored)
    {
        if (_level <= LEVEL_ALL)
        {
            StringBuilder buffer = new StringBuilder(64);
            format(buffer,":IGNORED:","",ignored);
            _stderr.println(buffer);
        }
    }
}
