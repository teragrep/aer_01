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
package com.teragrep.aer_01.config;

import com.teragrep.aer_01.config.source.Sourceable;

public final class AzureConfig {

    private final String namespaceName;
    private final String eventHubName;
    private final String blobStorageEndpoint;
    private final String blobStorageContainerName;
    private final String userManagedIdentityClientId;

    public AzureConfig(final Sourceable configSource) {
        this(
                configSource.source("azure.namespace", "<NAMESPACE NAME>.servicebus.windows.net"),
                configSource.source("azure.eventhub", "<EVENT HUB NAME>"),
                configSource.source("azure.blobstorage.endpoint", "https://<STORAGE ACCOUNT NAME>.blob.core.windows.net"), configSource.source("azure.blobstorage.container", "<CONTAINER NAME>"), configSource.source("azure.identity.usermanaged", "<USER MANAGED IDENTITY ID>")
        );
    }

    public AzureConfig(
            final String namespaceName,
            final String eventHubName,
            final String blobStorageEndpoint,
            final String blobStorageContainerName,
            final String userManagedIdentityClientId
    ) {
        this.namespaceName = namespaceName;
        this.eventHubName = eventHubName;
        this.blobStorageEndpoint = blobStorageEndpoint;
        this.blobStorageContainerName = blobStorageContainerName;
        this.userManagedIdentityClientId = userManagedIdentityClientId;
    }

    public String namespaceName() {
        return namespaceName;
    }

    public String eventHubName() {
        return eventHubName;
    }

    public String blobStorageEndpoint() {
        return blobStorageEndpoint;
    }

    public String blobStorageContainerName() {
        return blobStorageContainerName;
    }

    public String userManagedIdentityClientId() {
        return userManagedIdentityClientId;
    }
}
