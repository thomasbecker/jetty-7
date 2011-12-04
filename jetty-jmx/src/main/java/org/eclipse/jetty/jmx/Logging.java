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
import java.util.Map;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.LoggerListener;




/* ------------------------------------------------------------ */
/**
 */
public class Logging implements LoggingMBean,LoggerListener
{

    private static Map<String,Boolean> _loggers = new HashMap<String, Boolean>();

    public Logging()
    {
        Log.addLoggerListener(this);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#setDebugEnabled(java.lang.String, boolean)
     */
    public void setDebugEnabled(String loggerName, boolean enabled)
    {
        Logger logger = Log.getLogger(loggerName);
        logger.setDebugEnabled(enabled);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#getIsDebugEnabled(java.lang.String)
     */
    public boolean getIsDebugEnabled(String loggerName)
    {
        Logger logger = Log.getLogger(loggerName);
        return logger.isDebugEnabled();
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.jmx.LoggingMBean#getLoggers()
     */
    public Map<String, Boolean> getLoggers()
    {
        return _loggers;
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.util.log.LoggerListener#initialize(Map)
     */
    public void initialize(Map<String, Logger> loggers)
    {
        for (String logger : loggers.keySet())
        {
            _loggers.put(logger,loggers.get(logger).isDebugEnabled());
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.util.log.LoggerListener#addLogger(Logger)
     */
    public void addLogger(Logger logger)
    {
        _loggers.put(logger.getName(),logger.isDebugEnabled());
    }

}
