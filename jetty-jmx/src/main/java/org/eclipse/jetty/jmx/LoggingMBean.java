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

import java.util.Map;

import org.eclipse.jetty.util.log.Logger;

/* ------------------------------------------------------------ */
/**
 * JMX MBean to read and configure loglevels of all existing {@link Logger} instances
 */
public interface LoggingMBean
{
    /* ------------------------------------------------------------ */
    /**
     * Enables/Disables debug for the {@link Logger} with the given name
     *
     * @param loggerName
     *            the logger to change
     * @param enabled
     *            true to enable, false to disable debug loglevel
     */
    public void setDebugEnabled(String loggerName, boolean enabled);

    /* ------------------------------------------------------------ */
    /**
     * Returns if debug is enabled for the given {@link Logger}
     *
     * @param loggerName
     *            the logger name to retrieve isDebugEnabled from
     */
    public boolean getIsDebugEnabled(String loggerName);

    /* ------------------------------------------------------------ */
    /**
     * Returns all Loggers in a {@link Map}. Keys represent loggernames, Values represent isDebugEnabled
     */
    public Map<String, Boolean> getLoggers();
}
