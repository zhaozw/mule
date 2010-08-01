/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.source;

import org.mule.DefaultMuleEvent;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.construct.FlowConstructAware;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.LifecycleException;
import org.mule.api.lifecycle.Startable;
import org.mule.api.lifecycle.Stoppable;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.source.CompositeMessageSource;
import org.mule.api.source.MessageSource;
import org.mule.config.i18n.CoreMessages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implementation of {@link CompositeMessageSource} that propagates both injection of {@link FlowConstruct}
 * and lifecycle to nested {@link MessageSource}s.
 * <p>
 * <li>This message source cannot be started without a listener set.
 * <li>If sources are added when this composie is started they will be started as well.
 * <li>If a {@link MessageSource} is started in isolation when composite is stopped then messages will be
 * lost.
 * <li>Message will only be received from endpoints if the connector is also started.
 */
public class StartableCompositeMessageSource
    implements CompositeMessageSource, Startable, Stoppable, FlowConstructAware
{
    protected static final Log log = LogFactory.getLog(StartableCompositeMessageSource.class);

    protected MessageProcessor listener;
    protected AtomicBoolean started = new AtomicBoolean(false);
    protected final List<MessageSource> sources = Collections.synchronizedList(new ArrayList<MessageSource>());
    protected AtomicBoolean starting = new AtomicBoolean(false);
    protected FlowConstruct flowConstruct;
    private final MessageProcessor internalListener = new InternalMessageProcessor();

    public void addSource(MessageSource source) throws MuleException
    {
        synchronized (sources)
        {
            sources.add(source);
        }
        source.setListener(internalListener);
        if (started.get())
        {
            if (source instanceof FlowConstructAware)
            {
                ((FlowConstructAware) source).setFlowConstruct(flowConstruct);
            }
            if (source instanceof Startable)
            {
                ((Startable) source).start();
            }
        }
    }

    public void removeSource(MessageSource source) throws MuleException
    {
        if (started.get() && (source instanceof Stoppable))
        {
            ((Stoppable) source).stop();
        }
        synchronized (sources)
        {
            sources.remove(source);
        }
    }
    
    public void setMessageSources(List<MessageSource> sources) throws MuleException
    {
        this.sources.clear();
        for (MessageSource messageSource : sources)
        {
            addSource(messageSource);
        }
    }

    public void start() throws MuleException
    {
        if (listener == null)
        {
            throw new LifecycleException(CoreMessages.objectIsNull("listener"), this);
        }

        synchronized (sources)
        {
            starting.set(true);
            for (MessageSource source : sources)
            {
                if (source instanceof FlowConstructAware)
                {
                    ((FlowConstructAware) source).setFlowConstruct(flowConstruct);
                }
                if (source instanceof Startable)
                {
                    ((Startable) source).start();
                }
            }

            started.set(true);
            starting.set(false);
        }
    }

    public void stop() throws MuleException
    {
        synchronized (sources)
        {
            for (MessageSource source : sources)
            {
                if (source instanceof Stoppable)
                {
                    ((Stoppable) source).stop();
                }
            }

            started.set(false);
        }
    }

    public void setListener(MessageProcessor listener)
    {
        this.listener = listener;
    }

    public void setFlowConstruct(FlowConstruct pattern)
    {
        this.flowConstruct = pattern;

    }

    @Override
    public String toString()
    {
        return String.format("%s [listener=%s, sources=%s, started=%s]", getClass().getSimpleName(),
            listener, sources, started);
    }

    private class InternalMessageProcessor implements MessageProcessor
    {
        public InternalMessageProcessor()
        {
            super();
        }

        public MuleEvent process(MuleEvent event) throws MuleException
        {
            if (started.get() || starting.get())
            {
                // If the next message processor is an outbound router then create outbound event
                if (listener instanceof OutboundEndpoint)
                {
                    event = new DefaultMuleEvent(event.getMessage(), (OutboundEndpoint) listener, event.getSession());
                }
                return listener.process(event);
            }
            else
            {
                log.warn(String.format(
                    "A message was receieved from MessageSource, but message source is stopped. Message will be discarded.%n"
                                    + "  Message: %s%n" + "  MessageSource:%s", event, this));
                return null;
            }

        }
    }
}
