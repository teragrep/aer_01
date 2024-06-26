/*
 * Teragrep Azure Eventhub Reader
 * Copyright (C) 2023  Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://github.com/teragrep/teragrep/blob/main/LICENSE>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.aer_01;

import com.azure.messaging.eventhubs.models.EventContext;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_01.config.RelpConfig;
import com.teragrep.aer_01.config.SyslogConfig;
import com.teragrep.aer_01.config.source.Sourceable;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

final class EventContextConsumer implements AutoCloseable, Consumer<EventContext> {

    private final Output output;
    private final String realHostName;
    private final SyslogConfig syslogConfig;
    EventContextConsumer(Sourceable configSource) {
        RelpConfig relpConfig = new RelpConfig(configSource);

        this.output = new Output(
                "defaultOutput",
                relpConfig.destinationAddress,
                relpConfig.destinationPort,
                relpConfig.connectionTimeout,
                relpConfig.readTimeout,
                relpConfig.writeTimeout,
                relpConfig.reconnectInterval,
                new MetricRegistry()
        );

        this.realHostName = getRealHostName();
        this.syslogConfig = new SyslogConfig(configSource);
    }

    private String getRealHostName() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "localhost";
        }
        return hostname;
    }

    @Override
    public void accept(EventContext eventContext) {

        String eventUuid = eventContext.getEventData().getMessageId();

        // FIXME proper handling of non-provided uuids
        if (eventUuid == null) {
            eventUuid = "aer_01="+UUID.randomUUID();
        }

        SDElement sdId = new SDElement("event_id@48577")
                .addSDParam("hostname", realHostName)
                .addSDParam("uuid", eventUuid)
                .addSDParam("unixtime", Instant.now().toString())
                .addSDParam("id_source", "source");
        /*
        // TODO add this too as SDElement
        SDElement sdCorId = new SDElement("id@123").addSDParam("corId", eventContext.getEventData().getCorrelationId());

        // TODO add azure stuff
        eventContext.getPartitionContext().getFullyQualifiedNamespace();
        eventContext.getPartitionContext().getEventHubName();
        eventContext.getPartitionContext().getPartitionId();
        eventContext.getPartitionContext().getConsumerGroup();

        // TODO metrics about these vs last retrieved, these are tracked per partition!:
        eventContext.getLastEnqueuedEventProperties().getEnqueuedTime();
        eventContext.getLastEnqueuedEventProperties().getSequenceNumber();
        eventContext.getLastEnqueuedEventProperties().getOffset();
        eventContext.getLastEnqueuedEventProperties().getRetrievalTime(); // null if not retrieved

        // TODO compare these to above
        eventContext.getEventData().getOffset();
        eventContext.getEventData().getEnqueuedTime();
        eventContext.getEventData().getPartitionKey();
        eventContext.getEventData().getProperties();
        */
        SyslogMessage syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withTimestamp(eventContext.getEventData().getEnqueuedTime())
                .withHostname(syslogConfig.hostname)
                .withAppName(syslogConfig.appName)
                .withSDElement(sdId)
                //.withSDElement(sdCorId)
                .withMsgId(eventContext.getEventData().getSequenceNumber().toString())
                .withMsg(eventContext.getEventData().getBodyAsString());

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
        // Every 10 events received, it will update the checkpoint stored in Azure Blob Storage.
        if (eventContext.getEventData().getSequenceNumber() % 10 == 0) {
            eventContext.updateCheckpoint();
        }
    }

    @Override
    public void close() {
        output.close();
    }
}
