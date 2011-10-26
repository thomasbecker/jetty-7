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

package org.eclipse.jetty.io.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.print.attribute.standard.MediaSize.Engineering;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Buffers;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Timeout.Task;

/* ------------------------------------------------------------ */
/**
 */
public class SslConnection extends AbstractConnection implements AsyncConnection
{
    static final Logger LOG=Log.getLogger("org.eclipse.jetty.io.nio.ssl");
    
    private static final NIOBuffer __ZERO_BUFFER=new IndirectNIOBuffer(0);
    
    private final ThreadLocal<NIOBuffer> __inBuffer = new ThreadLocal<NIOBuffer>();
    private final ThreadLocal<NIOBuffer> __outBuffer = new ThreadLocal<NIOBuffer>();
    private final SSLEngine _engine;
    private final SSLSession _session;
    private AsyncConnection _connection;
    private final SslEndPoint _sslEndPoint = new SslEndPoint();
    private int _allocations;
    private NIOBuffer _inbound;
    private NIOBuffer _unwrapBuf;
    private NIOBuffer _outbound;
    private AsyncEndPoint _aEndp;

    
    public SslConnection(SSLEngine engine,EndPoint endp)
    {
        this(engine,endp,System.currentTimeMillis());
    }
    
    public SslConnection(SSLEngine engine,EndPoint endp, long timeStamp)
    {
        super(endp,timeStamp);
        _engine=engine;
        _session=_engine.getSession();
        _aEndp=(AsyncEndPoint)endp;
    }
    
    public synchronized void setConnection(AsyncConnection connection)
    {
        _connection=connection;
    }
    
    public synchronized AsyncConnection getConnection()
    {
        return _connection;
    }

    private void allocateBuffers()
    {
        synchronized (this)
        {
            if (_allocations++==0)
            {
                if (_inbound==null)
                {
                    _inbound = __inBuffer.get();
                    if (_inbound==null)
                        _inbound=new IndirectNIOBuffer(_session.getPacketBufferSize()*2);
                }

                if (_outbound==null)
                {
                    _outbound = __outBuffer.get();
                    if (_outbound==null)
                        _outbound=new IndirectNIOBuffer(_session.getPacketBufferSize()*2);
                }
            }
        }
    }
    
    private void releaseBuffers()
    {
        synchronized (this)
        {
            if (--_allocations==0)
            {
                if (_inbound!=null && _inbound.length()==0)
                {
                    __inBuffer.set(_inbound);
                    _inbound=null;
                }

                if (_outbound!=null && _outbound.length()==0)
                {
                    __outBuffer.set(_outbound);
                    _outbound=null;
                }
                
                if (_unwrapBuf!=null && _unwrapBuf.length()==0)
                    _unwrapBuf=null;
            }
        }
    }
    
    public Connection handle() throws IOException
    {
        try
        {
            allocateBuffers();
            
            boolean progress=true;

            while (progress)
            {
                progress=false;
                
                // If we are handshook let the delegate connection 
                if (_engine.getHandshakeStatus()!=HandshakeStatus.NOT_HANDSHAKING)
                    progress|=process(null,null);
                else
                {
                    // handle the delegate connection
                    AsyncConnection next = (AsyncConnection)_connection.handle();
                    if (next!=_connection && next==null)
                    {
                        _connection=next;
                        progress=true;
                    }
                }
                
                
                LOG.debug("{} handle progress=",_session,progress);
            }
        }
        finally
        {
            releaseBuffers();
        }
        
        return this;
    }

    public boolean isIdle()
    {
        return false;
    }

    public boolean isSuspended()
    {
        return false;
    }

    public void onClose()
    {
        
    }

    public void onInputShutdown() throws IOException
    {
        
    }

    /* ------------------------------------------------------------ */
    private synchronized boolean process(Buffer toFill, Buffer toFlush) throws IOException
    {
        if (toFill==null)
        {
            if (_unwrapBuf==null)
                _unwrapBuf=new IndirectNIOBuffer(_session.getApplicationBufferSize());
            toFill=_unwrapBuf;
        }
        else if (_unwrapBuf!=null && _unwrapBuf.hasContent())
        {
            _unwrapBuf.skip(toFill.put(_unwrapBuf));
            return true;
        }
        if (toFlush==null)
            toFlush=__ZERO_BUFFER;

        HandshakeStatus initialStatus = _engine.getHandshakeStatus();
        boolean progress=true;
        boolean some_progress=false;
        try
        {
            allocateBuffers();

            while (progress)
            {
                progress=false;
                int filled=0,flushed=0;
                
                // Read any available data
                if (_inbound.space()>0 && (filled=_endp.fill(_inbound))>0)
                    progress = true;
                
                // flush any output data
                if (_outbound.hasContent() && (flushed=_endp.flush(_outbound))>0)
                    progress = true;

                LOG.debug("{} filled={} flushed={}",_session,filled,flushed);
                
                // handle the current hand share status
                LOG.debug("{} status {}",_session,_engine.getHandshakeStatus());
                switch(_engine.getHandshakeStatus())
                {
                    case FINISHED:
                        throw new IllegalStateException();

                    case NOT_HANDSHAKING:
                    {
                        // Try wrapping some application data
                        if (toFlush.hasContent() && _outbound.space()>0 && wrap(toFlush))
                            progress=true;

                        // Try unwrapping some application data
                        if (toFill.space()>0 && _inbound.hasContent() && unwrap(toFill))
                            progress=true;
                    }   
                    break;

                    case NEED_TASK:
                    {
                        // A task needs to be run, so run it!
                        Runnable task;
                        while ((task=_engine.getDelegatedTask())!=null)
                        {
                            progress=true;
                            task.run();
                        }

                        // Detect SUN JVM Bug!!!
                        if(initialStatus==HandshakeStatus.NOT_HANDSHAKING &&
                                _engine.getHandshakeStatus()==HandshakeStatus.NEED_UNWRAP /* && sent==0 */ )
                        {
                            // This should be NEED_WRAP
                            // The fix simply detects the signature of the bug and then close the connection (fail-fast) so that ff3 will delegate to using SSL instead of TLS.
                            // This is a jvm bug on java1.6 where the SSLEngine expects more data from the initial handshake when the client(ff3-tls) already had given it.
                            // See http://jira.codehaus.org/browse/JETTY-567 for more details
                            LOG.warn("{} JETTY-567",_session);
                            _endp.close();
                            return false;
                        }
                    }
                    break;

                    case NEED_WRAP:
                    {
                        // The SSL needs to send some handshake data to the other side
                        if (wrap(toFlush))
                            progress=true;
                    }
                    break;

                    case NEED_UNWRAP:
                    {
                        // The SSL needs to receive some handshake data from the other side
                        if (unwrap(toFill))
                            progress=true;
                    }
                    break;
                }

                // pass on ishut/oshut state
                if (!_inbound.hasContent() && _endp.isInputShutdown())
                    _engine.closeInbound();
                if (!_outbound.hasContent() && _engine.isOutboundDone())
                    _endp.shutdownOutput();
                
                LOG.debug("{} process progress={}",_session,progress);
                some_progress|=progress;
            }
        }
        finally
        {
            releaseBuffers();
        }
        return some_progress;
    }
    
    private synchronized boolean wrap(final Buffer buffer) throws IOException
    {
        ByteBuffer bbuf=extractByteBuffer(buffer);
        final SSLEngineResult result;
        
        synchronized(bbuf)
        {
            _outbound.compact();
            ByteBuffer out_buffer=_outbound.getByteBuffer();
            synchronized(out_buffer)
            {
                try
                {
                    bbuf.position(buffer.getIndex());
                    bbuf.limit(buffer.putIndex());
                    out_buffer.position(_outbound.putIndex());
                    out_buffer.limit(out_buffer.capacity());
                    result=_engine.wrap(bbuf,out_buffer);
                    if (LOG.isDebugEnabled())
                        LOG.debug("{} wrap {} {} consumed={} produced={}",
                            _session,
                            result.getStatus(),
                            result.getHandshakeStatus(),
                            result.bytesConsumed(),
                            result.bytesProduced());

                    buffer.skip(result.bytesConsumed());
                    buffer.compact();
                    _outbound.setPutIndex(_outbound.putIndex()+result.bytesProduced());
                }
                catch(SSLException e)
                {
                    LOG.warn(_endp+":",e);
                    _endp.close();
                    throw e;
                }
                finally
                {
                    out_buffer.position(0);
                    out_buffer.limit(out_buffer.capacity());
                    bbuf.position(0);
                    bbuf.limit(bbuf.capacity());
                }
            }
        }

        switch(result.getStatus())
        {
            case BUFFER_UNDERFLOW:
                throw new IllegalStateException();

            case BUFFER_OVERFLOW:
                break;

            case OK:
                break;
                
            case CLOSED:
                _endp.close();
                break;

            default:
                LOG.warn("{} wrap default {}",_session,result);
            throw new IOException(result.toString());
        }
        
        return result.bytesConsumed()>0 || result.bytesProduced()>0;
    }
    
    private synchronized boolean unwrap(final Buffer buffer) throws IOException
    {
        if (!_inbound.hasContent())
            return false;
        
        buffer.compact();
        ByteBuffer bbuf=extractByteBuffer(buffer);
        final SSLEngineResult result;
        
        synchronized(bbuf)
        {
            ByteBuffer in_buffer=_inbound.getByteBuffer();
            synchronized(in_buffer)
            {
                try
                {
                    bbuf.position(buffer.putIndex());
                    bbuf.limit(buffer.capacity());
                    in_buffer.position(_inbound.getIndex());
                    in_buffer.limit(_inbound.putIndex());
                    
                    result=_engine.unwrap(in_buffer,bbuf);
                    if (LOG.isDebugEnabled())
                        LOG.debug("{} unwrap {} {} consumed={} produced={}",
                            _session,
                            result.getStatus(),
                            result.getHandshakeStatus(),
                            result.bytesConsumed(),
                            result.bytesProduced());
                    
                    _inbound.skip(result.bytesConsumed());
                    _inbound.compact();
                    buffer.setPutIndex(buffer.putIndex()+result.bytesProduced());
                }
                catch(SSLException e)
                {
                    LOG.warn(_endp+":",e);
                    _endp.close();  // TODO ?
                    throw e;
                }
                finally
                {
                    in_buffer.position(0);
                    in_buffer.limit(in_buffer.capacity());
                    bbuf.position(0);
                    bbuf.limit(bbuf.capacity());
                }
            }
        }

        switch(result.getStatus())
        {
            case BUFFER_UNDERFLOW:
                break;

            case BUFFER_OVERFLOW:
                LOG.debug("{} wrap {}",_session,result);
                break;

            case OK:
                break;
                
            case CLOSED:
                _endp.close();
                break;

            default:
                LOG.warn("{} wrap default {}",_session,result);
            throw new IOException(result.toString());
        }
        
        if (LOG.isDebugEnabled() && result.bytesProduced()>0)
            LOG.debug("{} unwrapped '{}'",_session,buffer);
        
        return result.bytesConsumed()>0 || result.bytesProduced()>0;
    }


    /* ------------------------------------------------------------ */
    private ByteBuffer extractByteBuffer(Buffer buffer)
    {
        if (buffer.buffer() instanceof NIOBuffer)
            return ((NIOBuffer)buffer.buffer()).getByteBuffer();
        return ByteBuffer.wrap(buffer.array());
    }
    
    /* ------------------------------------------------------------ */
    public AsyncEndPoint getSslEndPoint()
    {
        return _sslEndPoint;
    }
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public class SslEndPoint implements AsyncEndPoint
    {
        public SSLEngine getSslEngine()
        {
            return _engine;
        }

        public SslConnection getSslConnection()
        {
            return SslConnection.this;
        }
        
        public void shutdownOutput() throws IOException
        {
            _engine.closeOutbound();
        }

        public boolean isOutputShutdown()
        {
            return !isOpen();
        }

        public void shutdownInput() throws IOException
        {
            // We do not do a closeInput here, as that prevents any writes
        }

        public boolean isInputShutdown()
        {
            return !isOpen();
        }

        public void close() throws IOException
        {
            _endp.close();
        }

        public int fill(Buffer buffer) throws IOException
        {
            int size=buffer.length();
            process(buffer,null);
            int filled=buffer.length()-size;
            
            if (filled==0 && isInputShutdown())
                return -1;
            return filled;
        }

        public int flush(Buffer buffer) throws IOException
        {
            int size=buffer.length();
            process(null,buffer);
            int flushed=size-buffer.length();
            return flushed;
        }

        public int flush(Buffer header, Buffer buffer, Buffer trailer) throws IOException
        {
            if (header!=null && header.hasContent())
                return flush(header);
            if (buffer!=null && buffer.hasContent())
                return flush(buffer);
            if (trailer!=null && trailer.hasContent())
                return flush(trailer);
            return 0;
        }

        public boolean blockReadable(long millisecs) throws IOException
        {
            long now = System.currentTimeMillis();
            long end=millisecs>0?(now+millisecs):Long.MAX_VALUE;
            
            while (now<end)
            {
                process(null,null);
                synchronized (this)
                {
                    if (_unwrapBuf!=null && _unwrapBuf.hasContent())
                        break;
                    if (_inbound!=null && _inbound.hasContent())
                        break;
                }
                _endp.blockReadable(end-now);
                now = System.currentTimeMillis();
            }
            
            return now<end;
        }

        public boolean blockWritable(long millisecs) throws IOException
        {
            return _endp.blockWritable(millisecs);
        }

        public boolean isOpen()
        {
            return _endp.isOpen();
        }

        public Object getTransport()
        {
            return _endp;
        }

        public boolean isBufferingInput()
        {
            synchronized (this)
            {
                if (_unwrapBuf!=null && _unwrapBuf.hasContent())
                    return true;
                if (_inbound!=null && _inbound.hasContent())
                    return true;
            }
            return false;
        }

        public boolean isBufferingOutput()
        {
            synchronized (this)
            {
                return _outbound!=null && _outbound.hasContent();
            }
        }

        public void flush() throws IOException
        {
            process(null,null);
        }

        public void asyncDispatch()
        {
            _aEndp.asyncDispatch();
        }

        public void scheduleWrite()
        {
            _aEndp.scheduleWrite();
        }

        public void scheduleIdle()
        {
            _aEndp.scheduleIdle();
        }

        public void cancelIdle()
        {
            _aEndp.cancelIdle();
        }

        public void scheduleTimeout(Task task, long timeoutMs)
        {
            _aEndp.scheduleTimeout(task,timeoutMs);
        }

        public void cancelTimeout(Task task)
        {
            _aEndp.cancelTimeout(task);
        }
        
        public boolean isWritable()
        {
            return _aEndp.isWritable();
        }

        public boolean hasProgressed()
        {
            return _aEndp.hasProgressed();
        }

        public String getLocalAddr()
        {
            return _aEndp.getLocalAddr();
        }

        public String getLocalHost()
        {
            return _aEndp.getLocalHost();
        }

        public int getLocalPort()
        {
            return _aEndp.getLocalPort();
        }

        public String getRemoteAddr()
        {
            return _aEndp.getRemoteAddr();
        }

        public String getRemoteHost()
        {
            return _aEndp.getRemoteHost();
        }

        public int getRemotePort()
        {
            return _aEndp.getRemotePort();
        }

        public boolean isBlocking()
        {
            return false;
        }

        public boolean isBufferred()
        {
            return true;
        }

        public int getMaxIdleTime()
        {
            return _aEndp.getMaxIdleTime();
        }

        public void setMaxIdleTime(int timeMs) throws IOException
        {
            _aEndp.setMaxIdleTime(timeMs);
        }

        
    }
    
    
}