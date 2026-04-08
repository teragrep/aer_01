/*
 * Teragrep syslog bridge for Microsoft Azure EventHub
 * Copyright (C) 2023-2025 Suomen Kanuuna Oy
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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_01.plugin.ParsedEventWithException;
import com.teragrep.aer_01.plugin.WrappedPluginFactoryWithConfig;
import com.teragrep.aer_01.records.EventRecords;
import com.teragrep.akv_01.event.ParsedEvent;
import com.teragrep.akv_01.plugin.Plugin;
import com.teragrep.akv_01.plugin.PluginException;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.SDParam;
import com.teragrep.rlo_14.SyslogMessage;
import com.teragrep.rlp_01.RelpBatch;
import jakarta.json.JsonException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;

public final class ParsedEventConsumer implements AutoCloseable, Consumer<List<ParsedEvent>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParsedEventConsumer.class);
    private final Output output;
    private final Map<String, WrappedPluginFactoryWithConfig> pluginFactories;
    private final MetricRegistry metricRegistry;
    private final WrappedPluginFactoryWithConfig defaultPluginFactory;
    private final WrappedPluginFactoryWithConfig exceptionPluginFactory;

    ParsedEventConsumer(
            final Output output,
            final Map<String, WrappedPluginFactoryWithConfig> pluginFactories,
            final WrappedPluginFactoryWithConfig defaultPluginFactory,
            final WrappedPluginFactoryWithConfig exceptionPluginFactory,
            final MetricRegistry metricRegistry
    ) {
        this.metricRegistry = metricRegistry;
        this.pluginFactories = pluginFactories;
        this.exceptionPluginFactory = exceptionPluginFactory;
        this.defaultPluginFactory = defaultPluginFactory;
        this.output = output;
    }

    @Override
    public void close() {
        output.close();
    }

    @Override
    public void accept(final List<ParsedEvent> parsedEvents) {
        LOGGER.info("Received <[{}]> ParsedEvents", parsedEvents.size());
        final List<SyslogMessage> syslogMessagesToSend = new ArrayList<>();
        for (final ParsedEvent initialEvent : parsedEvents) {
            for (final ParsedEvent parsedEvent : new EventRecords(initialEvent).records()) {
                WrappedPluginFactoryWithConfig pluginFactoryWithConfig;
                if (parsedEvent.isJsonStructure()) {
                    try {
                        final String resourceId = parsedEvent.resourceId();
                        pluginFactoryWithConfig = pluginFactories.getOrDefault(resourceId, defaultPluginFactory);
                    }
                    catch (final JsonException ignored) {
                        // no resourceId in json
                        pluginFactoryWithConfig = defaultPluginFactory;
                    }
                }
                else {
                    // non-json event
                    pluginFactoryWithConfig = defaultPluginFactory;
                }

                final Plugin plugin = pluginFactoryWithConfig
                        .pluginFactory()
                        .plugin(pluginFactoryWithConfig.pluginFactoryConfig().configPath());

                List<SyslogMessage> syslogMessages;
                try {
                    syslogMessages = plugin.syslogMessage(parsedEvent);
                }
                catch (final PluginException e) {
                    try {
                        syslogMessages = exceptionPluginFactory
                                .pluginFactory()
                                .plugin(exceptionPluginFactory.pluginFactoryConfig().configPath())
                                .syslogMessage(new ParsedEventWithException(parsedEvent, e));
                    }
                    catch (final PluginException e2) {
                        throw new IllegalStateException("Exception plugin failed!", e2);
                    }
                }

                syslogMessagesToSend.addAll(syslogMessages);
            }
        }

        sendToOutput(syslogMessagesToSend);
    }

    private void sendToOutput(final List<SyslogMessage> syslogMessages) {
        LOGGER.debug("Creating a RelpBatch from SyslogMessages...");
        final RelpBatch batch = new RelpBatch();

        for (final SyslogMessage syslogMessage : syslogMessages) {
            final List<SDElement> partitionElements = syslogMessage
                    .getSDElements()
                    .stream()
                    .filter(sdElement -> sdElement.getSdID().equals("aer_01_partition@48577"))
                    .collect(Collectors.toList());
            if (partitionElements.isEmpty()) {
                throw new IllegalStateException("SDElement aer_01_partition@48577 not found");
            }

            final List<SDParam> partitionParams = partitionElements
                    .get(0)
                    .getSdParams()
                    .stream()
                    .filter(sdParam -> sdParam.getParamName().equals("partition_id"))
                    .collect(Collectors.toList());
            if (partitionParams.isEmpty()) {
                throw new IllegalStateException("SDParam partition_id not found in SDElement aer_01_partition@48577");
            }

            final long timestampSecs = Instant.parse(syslogMessage.getTimestamp()).toEpochMilli() / 1000L;

            metricRegistry
                    .gauge(name(ParsedEventConsumer.class, "latency-seconds", partitionParams.get(0).getParamValue()), () -> (Gauge<Long>) () -> Instant.now().getEpochSecond() - timestampSecs);

            batch.insert(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
        }

        LOGGER.debug("Sending RelpBatch to Output");
        output.accept(batch);
    }
}
