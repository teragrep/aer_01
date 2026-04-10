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

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.jmx.JmxReporter;
import com.teragrep.aer_01.config.*;
import com.teragrep.aer_01.config.source.EnvironmentSource;
import com.teragrep.aer_01.config.source.PropertySource;
import com.teragrep.aer_01.config.source.Sourceable;
import com.teragrep.aer_01.hostname.Hostname;
import com.teragrep.aer_01.plugin.MappedPluginFactories;
import com.teragrep.aer_01.plugin.WrappedPluginFactoryWithConfig;
import com.teragrep.aer_01.tls.AzureSSLContextSupplier;
import com.teragrep.akv_01.plugin.PluginFactoryConfig;
import com.teragrep.akv_01.plugin.PluginMap;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.ManagedRelpConnectionStub;
import com.teragrep.rlp_01.pool.Pool;
import com.teragrep.rlp_01.pool.UnboundPool;
import com.teragrep.aer_01.plugin.PluginConfiguration;
import io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet;
import io.prometheus.metrics.instrumentation.dropwizard.DropwizardExports;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-java-get-started-send?tabs=passwordless%2Croles-azure-portal

public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args) throws Exception {
        final MetricRegistry metricRegistry = new MetricRegistry();
        final Sourceable configSource = getConfigSource();
        final int MAX_BATCH_SIZE = new BatchConfig(configSource).maxBatchSize();
        final int prometheusPort = new MetricsConfig(configSource).prometheusPort();
        final String realHostname = new Hostname("localhost").hostname();

        final RelpConnectionConfig relpConnectionConfig = new RelpConnectionConfig(configSource);

        LOGGER.info("Initializing Syslog bridge with max batch size <[{}]>", MAX_BATCH_SIZE);

        final JmxReporter jmxReporter = JmxReporter.forRegistry(metricRegistry).build();
        final Slf4jReporter slf4jReporter = Slf4jReporter
                .forRegistry(metricRegistry)
                .outputTo(LoggerFactory.getLogger(EventBatchConsumer.class))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        final Server jettyServer = new Server(prometheusPort);

        startMetrics(jmxReporter, slf4jReporter, metricRegistry, jettyServer);

        LOGGER.info("Initializing plugin map...");
        final PluginMap pluginMap;
        try {
            pluginMap = new PluginMap(new PluginConfiguration(configSource).asJson());
        }
        catch (final IOException e) {
            throw new UncheckedIOException("Initializing PluginMap failed", e);
        }
        LOGGER.info("Plugin map initialized.");

        final Map<String, PluginFactoryConfig> pluginFactoryConfigs = pluginMap.asUnmodifiableMap();
        final String defaultPluginFactoryClassName = pluginMap.defaultPluginFactoryClassName();
        final String exceptionPluginFactoryClassName = pluginMap.exceptionPluginFactoryClassName();
        final MappedPluginFactories mappedPluginFactories = new MappedPluginFactories(
                pluginFactoryConfigs,
                defaultPluginFactoryClassName,
                exceptionPluginFactoryClassName,
                realHostname,
                new SyslogConfig(configSource)
        );

        final Map<String, WrappedPluginFactoryWithConfig> pluginFactories = mappedPluginFactories.asUnmodifiableMap();
        final WrappedPluginFactoryWithConfig defaultPluginFactory = mappedPluginFactories
                .defaultPluginFactoryWithConfig();
        final WrappedPluginFactoryWithConfig exceptionPluginFactory = mappedPluginFactories
                .exceptionPluginFactoryWithConfig();

        final Pool<IManagedRelpConnection> relpConnectionPool;
        if (configSource.source("relp.tls.mode", "none").equals("keyVault")) {
            LOGGER.info("RELP connection is set to use keyVault TLS mode");
            relpConnectionPool = new UnboundPool<>(
                    new ManagedRelpConnectionWithMetricsFactory(
                            relpConnectionConfig.asRelpConfig(),
                            "defaultOutput",
                            metricRegistry,
                            relpConnectionConfig.asSocketConfig(),
                            new AzureSSLContextSupplier()
                    ),
                    new ManagedRelpConnectionStub()
            );
        }
        else {
            LOGGER.info("RELP connection is set to use plain mode");
            relpConnectionPool = new UnboundPool<>(
                    new ManagedRelpConnectionWithMetricsFactory(
                            relpConnectionConfig.asRelpConfig(),
                            "defaultOutput",
                            metricRegistry,
                            relpConnectionConfig.asSocketConfig()
                    ),
                    new ManagedRelpConnectionStub()
            );
        }

        final DefaultOutput dOutput = new DefaultOutput(relpConnectionPool);

        LOGGER.info("Starting EventBatchConsumer...");
        try (
                final EventBatchConsumer PARTITION_PROCESSOR = new EventBatchConsumer(
                        new ParsedEventConsumer(
                                dOutput,
                                pluginFactories,
                                defaultPluginFactory,
                                exceptionPluginFactory,
                                metricRegistry
                        )
                )
        ) {
            final AzureConfig azureConfig = new AzureConfig(configSource);
            final ErrorContextConsumer ERROR_HANDLER = new ErrorContextConsumer();

            // create credentials using the ManagedIdentityCredentialBuilder
            LOGGER.info("Building credentials...");
            final TokenCredential credential = new ManagedIdentityCredentialBuilder()
                    .clientId(azureConfig.userManagedIdentityClientId())
                    .build();

            // Create a blob container client that you use later to build an event processor client to receive and process events
            LOGGER.info("Building BlobContainerClient...");
            final BlobContainerAsyncClient blobContainerAsyncClient = new BlobContainerClientBuilder()
                    .credential(credential)
                    .endpoint(azureConfig.blobStorageEndpoint())
                    .containerName(azureConfig.blobStorageContainerName())
                    .buildAsyncClient();

            // Create an event processor client to receive and process events and errors.
            LOGGER.info("Building EventProcessorClient...");
            final EventProcessorClient eventProcessorClient = new EventProcessorClientBuilder()
                    .fullyQualifiedNamespace(azureConfig.namespaceName())
                    .eventHubName(azureConfig.eventHubName())
                    .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                    .processEventBatch(PARTITION_PROCESSOR, MAX_BATCH_SIZE)
                    .processError(ERROR_HANDLER)
                    .checkpointStore(new BlobCheckpointStore(blobContainerAsyncClient))
                    .credential(credential)
                    .buildEventProcessorClient();

            LOGGER.info("Starting EventProcessorClient...");
            eventProcessorClient.start();

            LOGGER.info("Main thread sleeping");
            Thread.sleep(Long.MAX_VALUE);

            LOGGER.info("Main thread has been awakened. Stopping syslog bridge...");
            eventProcessorClient.stop();

            jettyServer.stop();
            slf4jReporter.stop();
            jmxReporter.stop();
            LOGGER.info("Syslog bridge stopped.");
        }
    }

    private static void startMetrics(
            JmxReporter jmxReporter,
            Slf4jReporter slf4jReporter,
            MetricRegistry metricRegistry,
            Server jettyServer
    ) throws Exception {
        LOGGER.info("Starting metrics for syslog bridge...");
        jmxReporter.start();
        slf4jReporter.start(1, TimeUnit.MINUTES);

        // prometheus-exporter
        PrometheusRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry));

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        jettyServer.setHandler(context);

        final PrometheusMetricsServlet metricsServlet = new PrometheusMetricsServlet();
        final ServletHolder servletHolder = new ServletHolder(metricsServlet);
        context.addServlet(servletHolder, "/metrics");

        jettyServer.start();
        LOGGER.info("Metrics started for syslog bridge.");
    }

    private static Sourceable getConfigSource() {
        LOGGER.info("Getting config source...");
        final String type = System.getProperty("config.source", "properties");

        final Sourceable rv;
        if ("properties".equals(type)) {
            LOGGER.info("Config source set to properties.");
            rv = new PropertySource();
        }
        else if ("environment".equals(type)) {
            LOGGER.info("Config source set to environment.");
            rv = new EnvironmentSource();
        }
        else {
            throw new IllegalArgumentException("config.source not within supported types: [properties, environment]");
        }

        return rv;
    }
}
