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

/**
 * A simple logging facade that is intended simply to capture the style of logging as used by Jetty.
 */
public interface Logger
{
    /**
     * Defines the minimum set of levels. Some implementations might need to translate the levels if no corresponding level exist on the underlying logging
     * framework. {@link java.util.logging.Logger} for example doesn't have a DEBUG level, so DEBUG will be translated to FINE. The mapping from {@link Logger}
     * to other logging frameworks should be the same as used in slf4j.
     */
    public enum Level
    {
        OFF, FATAL, ERROR, WARN, INFO, DEBUG, ALL
    }

    /**
     * @return the name of this logger
     */
    public String getName();

    /**
     * Formats and logs at warn level.
     *
     * @param msg
     *            the formatting string
     * @param args
     *            the optional arguments
     */
    public void warn(String msg, Object... args);

    /**
     * Logs the given Throwable information at warn level
     *
     * @param thrown
     *            the Throwable to log
     */
    public void warn(Throwable thrown);

    /**
     * Logs the given message at warn level, with Throwable information.
     *
     * @param msg
     *            the message to log
     * @param thrown
     *            the Throwable to log
     */
    public void warn(String msg, Throwable thrown);

    /**
     * Formats and logs at info level.
     *
     * @param msg
     *            the formatting string
     * @param args
     *            the optional arguments
     */
    public void info(String msg, Object... args);

    /**
     * Logs the given Throwable information at info level
     *
     * @param thrown
     *            the Throwable to log
     */
    public void info(Throwable thrown);

    /**
     * Logs the given message at info level, with Throwable information.
     *
     * @param msg
     *            the message to log
     * @param thrown
     *            the Throwable to log
     */
    public void info(String msg, Throwable thrown);

    /**
     * @return whether the debug level is enabled
     */
    public boolean isDebugEnabled();

    /**
     * Mutator used to turn debug on programmatically.
     *
     * @param enabled
     *            whether to enable the debug level
     */
    public void setDebugEnabled(boolean enabled);

    /**
     * Get the level of this logger
     *
     * @return the level of this logger
     */
    public String getLevel();

    /**
     * Set the level of this logger.
     *
     * @param level
     *            the level to set
     */
    public void setLevel(Level level);

    /**
     * Formats and logs at debug level.
     *
     * @param msg
     *            the formatting string
     * @param args
     *            the optional arguments
     */
    public void debug(String msg, Object... args);

    /**
     * Logs the given Throwable information at debug level
     *
     * @param thrown
     *            the Throwable to log
     */
    public void debug(Throwable thrown);

    /**
     * Logs the given message at debug level, with Throwable information.
     *
     * @param msg
     *            the message to log
     * @param thrown
     *            the Throwable to log
     */
    public void debug(String msg, Throwable thrown);

    /**
     * @param name
     *            the name of the logger
     * @return a logger with the given name
     */
    public Logger getLogger(String name);

    /**
     * Ignore an exception.
     * <p>
     * This should be used rather than an empty catch block.
     */
    public void ignore(Throwable ignored);
}
