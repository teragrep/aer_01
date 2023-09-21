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

import com.azure.identity.AzureAuthorityHosts;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.teragrep.aer_01.config.AzureConfig;

import java.io.IOException;

// https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-java-get-started-send?tabs=passwordless%2Croles-azure-portal

public final class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        try (final EventContextConsumer PARTITION_PROCESSOR = new EventContextConsumer()) {
            AzureConfig azureConfig = new AzureConfig();
            final ErrorContextConsumer ERROR_HANDLER = new ErrorContextConsumer();

// create a token using the default Azure credential
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                    .authorityHost(AzureAuthorityHosts.AZURE_PUBLIC_CLOUD)
                    .build();

// Create a blob container client that you use later to build an event processor client to receive and process events
            BlobContainerAsyncClient blobContainerAsyncClient = new BlobContainerClientBuilder()
                    .credential(credential)
                    .endpoint(azureConfig.blobStorageEndpoint)
                    .containerName(azureConfig.blobStorageContainerName)
                    .buildAsyncClient();

// Create an event processor client to receive and process events and errors.
            EventProcessorClient eventProcessorClient = new EventProcessorClientBuilder()
                    .fullyQualifiedNamespace(azureConfig.namespaceName)
                    .eventHubName(azureConfig.namespaceName)
                    .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                    .processEvent(PARTITION_PROCESSOR)
                    .processError(ERROR_HANDLER)
                    .checkpointStore(new BlobCheckpointStore(blobContainerAsyncClient))
                    .credential(credential)
                    .buildEventProcessorClient();

            eventProcessorClient.start();

            Thread.sleep(Long.MAX_VALUE);

            eventProcessorClient.stop();
        }
    }
}
