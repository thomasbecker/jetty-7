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

package org.eclipse.jetty.util.log;

import java.util.Map;

import org.eclipse.jetty.util.component.Container.Listener;

/* ------------------------------------------------------------ */
/**
 * Listener to track {@link Logger} instances.
 */
public interface LoggerListener
{
    /**
     * Initializes this listener.
     *
     * @param loggers
     *            loggers to be added initially
     */
    public void initialize(Map<String, Logger> loggers);

    /**
     * Adds a logger to this {@link Listener} instance. Should be called whenever a new Logger is being configured.
     */
    public void addLogger(Logger logger);
}
