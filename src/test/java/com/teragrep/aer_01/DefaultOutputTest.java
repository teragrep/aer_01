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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.teragrep.aer_01.config.RelpConfig;
import com.teragrep.aer_01.config.source.PropertySource;
import com.teragrep.aer_01.fakes.ConnectionlessRelpConnectionFake;
import com.teragrep.aer_01.fakes.RelpConnectionFake;
import com.teragrep.aer_01.fakes.ThrowingRelpConnectionFake;
import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import com.teragrep.rlp_01.RelpConnection;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;

import static com.codahale.metrics.MetricRegistry.name;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DefaultOutputTest {

    private SyslogMessage syslogMessage;
    private DefaultOutput output;

    @BeforeAll
    public void setEnv() {
        syslogMessage = new SyslogMessage()
                .withSeverity(Severity.INFORMATIONAL)
                .withFacility(Facility.LOCAL0)
                .withMsgId("123")
                .withMsg("test");
    }

    @AfterEach
    public void tearDown() {
        output.close();
    }

    @Test
    public void testSendLatencyMetricIsCapped() { // Should only keep information on the last 10.000 messages
        final int measurementLimit = 10000;

        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        SlidingWindowReservoir sendReservoir = new SlidingWindowReservoir(measurementLimit);
        SlidingWindowReservoir connectReservoir = new SlidingWindowReservoir(measurementLimit);
        output = new DefaultOutput("defaultOutput", new RelpConfig(new PropertySource()), metricRegistry,
                new RelpConnectionFake(), sendReservoir, connectReservoir);

        for (int i = 0; i < measurementLimit + 100; i++) { // send more messages than the limit is
            output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));
        }

        Assertions.assertEquals(measurementLimit, sendReservoir.size()); // should have measurementLimit amount of records saved
        Assertions.assertEquals(1, connectReservoir.size()); // only connected once
    }

    @Test
    public void testConnectionLatencyMetricIsCapped() { // Should take information on how long it took to successfully connect
        System.setProperty("relp.connection.retry.interval", "1");

        final int measurementLimit = 100;
        final int reconnections = measurementLimit + 10;

        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        SlidingWindowReservoir sendReservoir = new SlidingWindowReservoir(measurementLimit);
        SlidingWindowReservoir connectReservoir = new SlidingWindowReservoir(measurementLimit);
        RelpConnection relpConnection = new ConnectionlessRelpConnectionFake(reconnections); // use a fake that forces reconnects
        output = new DefaultOutput("defaultOutput", new RelpConfig(new PropertySource()), metricRegistry,
                relpConnection, sendReservoir, connectReservoir);

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(1, sendReservoir.size()); // only sent 1 message
        Assertions.assertEquals(measurementLimit, connectReservoir.size()); // should have measurementLimit amount of records saved

        System.clearProperty("relp.connection.retry.interval");
    }

    @Test
    public void testConnectionLatencyMetricWithException() { // should not update value if an exception was thrown from server
        System.setProperty("relp.connection.retry.interval", "1");

        final int reconnections = 10;

        // set up DefaultOutput
        MetricRegistry metricRegistry = new MetricRegistry();
        RelpConnection relpConnection = new ThrowingRelpConnectionFake(reconnections); // use a fake that throws exceptions when connecting
        output = new DefaultOutput("defaultOutput", new RelpConfig(new PropertySource()), metricRegistry, relpConnection);

        output.accept(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));

        Timer sendTimer = metricRegistry.timer(name(DefaultOutput.class, "<[defaultOutput]>", "sendLatency"));
        Timer connectionTimer = metricRegistry.timer(name(DefaultOutput.class, "<[defaultOutput]>", "connectLatency"));

        Assertions.assertEquals(1, sendTimer.getCount()); // only sent 1 message
        Assertions.assertEquals(1, connectionTimer.getCount()); // only 1 connection attempt without throwing recorded

        System.clearProperty("relp.connection.retry.interval");
    }
}
