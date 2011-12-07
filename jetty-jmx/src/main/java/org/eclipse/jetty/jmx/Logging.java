// ========================================================================
// Copyright (c) 2009-2009 Mort Bay Consulting Pty. Ltd.
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


package org.eclipse.jetty.jmx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.Logger.Level;
import org.eclipse.jetty.util.log.LoggerListener;




/* ------------------------------------------------------------ */
/**
 */
public class Logging implements LoggingMBean,LoggerListener
{

    private static Set<Logger> _loggers = new HashSet<Logger>();

    public Logging()
    {
        Log.addLoggerListener(this);
    }

    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#getLoggers()
     */
    public Map<String, String> getLoggers()
    {
        Map<String, String> loggers = new HashMap<String, String>();
        for (Logger logger : _loggers)
        {
            loggers.put(logger.getName(),logger.getLevel());
        }
        return loggers;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.util.log.LoggerListener#initialize(Map)
     */
    public void initialize(Map<String, Logger> loggers)
    {
        for (String logger : loggers.keySet())
        {
            _loggers.add(loggers.get(logger));
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.util.log.LoggerListener#addLogger(Logger)
     */
    public void addLogger(Logger logger)
    {
        _loggers.add(logger);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#getLevel(String)
     */
    public String getLevel(String loggerName)
    {
        return Log.getLogger(loggerName).getLevel();
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#setLevel(String, String)
     */
    public void setLevel(String loggerName, String level)
    {
        Log.getLogger(loggerName).setLevel(Level.valueOf(level.toUpperCase()));
    }

}
