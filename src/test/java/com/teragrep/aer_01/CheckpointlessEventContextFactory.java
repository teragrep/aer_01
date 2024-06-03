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

import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.LastEnqueuedEventProperties;
import com.azure.messaging.eventhubs.models.PartitionContext;

import java.time.Instant;

final public class CheckpointlessEventContextFactory implements EventContextFactory {

    private int created;
    private Integer partitionId = 0;

    @Override
    public EventContext create() {
        if (created % 5 == 0) {
            partitionId++;
        }

        final PartitionContext partitionContext = new PartitionContext("namespace", "eventHubName",
                "consumerGroup", partitionId.toString());
        final EventData eventData = new EventDataFake();
        final CheckpointStore checkpointStore = new CheckpointStoreFake();
        final LastEnqueuedEventProperties lastEnqueuedEventProperties = new LastEnqueuedEventProperties(1L, 100L,
                Instant.ofEpochSecond(0), Instant.ofEpochSecond(0));

        created++;
        return new EventContext(partitionContext, eventData, checkpointStore, lastEnqueuedEventProperties);
    }
}
