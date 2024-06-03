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
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.teragrep.aer_01.config.MetricsConfig;
import com.teragrep.aer_01.config.source.PropertySource;
import com.teragrep.aer_01.config.source.Sourceable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.codahale.metrics.MetricRegistry.name;

public class EventContextConsumerTest {

    @Test
    public void testLatencyMetric() throws Exception {
        final EventContextFactory eventContextFactory = new CheckpointlessEventContextFactory();
        final Sourceable configSource = new PropertySource();
        final int prometheusPort = new MetricsConfig(configSource).prometheusPort;
        final MetricRegistry metricRegistry = new MetricRegistry();

        try (EventContextConsumer eventContextConsumer = new EventContextConsumer(configSource, new Output.FakeOutput(), metricRegistry, prometheusPort)) {
            EventContext eventContext;
            final double records = 10;
            for (int i = 0; i < records; i++) {
                eventContext = eventContextFactory.create();
                eventContextConsumer.accept(eventContext);
            }

            long latency = Instant.now().getEpochSecond();

            // 5 records for each partition
            Gauge<Long> gauge1 = metricRegistry.gauge(name(EventContextConsumer.class, "latency-seconds", "1"));
            Gauge<Long> gauge2 = metricRegistry.gauge(name(EventContextConsumer.class, "latency-seconds", "2"));

            // hard to test the exact correct latency
            Assertions.assertTrue(gauge1.getValue() >= latency);
            Assertions.assertTrue(gauge2.getValue() >= latency);
        }
    }

    @Test
    public void testDepthBytesMetric() throws Exception {
        final EventContextFactory eventContextFactory = new CheckpointlessEventContextFactory();
        final Sourceable configSource = new PropertySource();
        final int prometheusPort = new MetricsConfig(configSource).prometheusPort;
        final MetricRegistry metricRegistry = new MetricRegistry();

        try (EventContextConsumer eventContextConsumer = new EventContextConsumer(configSource, new Output.FakeOutput(), metricRegistry, prometheusPort)) {
            // FIXME: code duplication when initializing without null
            EventContext eventContext = eventContextFactory.create();
            eventContextConsumer.accept(eventContext);

            long depth1 = 0L;
            final double records = 10;
            for (int i = 1; i < records; i++) { // records - 1 loops
                eventContext = eventContextFactory.create();
                eventContextConsumer.accept(eventContext);

                if (i == 4) {
                    depth1 = eventContext.getLastEnqueuedEventProperties().getOffset() - eventContext.getEventData().getOffset();
                }
            }

            long depth2 = eventContext.getLastEnqueuedEventProperties().getOffset() - eventContext.getEventData().getOffset();
            Gauge<Long> gauge1 = metricRegistry.gauge(name(EventContextConsumer.class, "depth-bytes", "1"));
            Gauge<Long> gauge2 = metricRegistry.gauge(name(EventContextConsumer.class, "depth-bytes", "2"));

            Assertions.assertEquals(depth1, 99L); // offsets are defined in the factory
            Assertions.assertEquals(depth2, 99L);
            Assertions.assertEquals(depth1, gauge1.getValue());
            Assertions.assertEquals(depth2, gauge2.getValue());
        }
    }

    @Test
    public void testEstimatedDataDepthMetric() throws Exception {
        final EventContextFactory eventContextFactory = new CheckpointlessEventContextFactory();
        final Sourceable configSource = new PropertySource();
        final int prometheusPort = new MetricsConfig(configSource).prometheusPort;
        final MetricRegistry metricRegistry = new MetricRegistry();

        try (EventContextConsumer eventContextConsumer = new EventContextConsumer(configSource, new Output.FakeOutput(), metricRegistry, prometheusPort)) {
            final double records = 10;
            long length = 0L;
            for (int i = 0; i < records; i++) {
                EventContext eventContext = eventContextFactory.create();
                length = length + eventContext.getEventData().getBody().length;
                eventContextConsumer.accept(eventContext);
            }

            Gauge<Long> gauge = metricRegistry.gauge(MetricRegistry.name(EventContextConsumer.class, "estimated-data-depth"));
            Double estimatedDepth = (length / records) / records;

            Assertions.assertEquals(estimatedDepth, gauge.getValue());
        }
    }
}
