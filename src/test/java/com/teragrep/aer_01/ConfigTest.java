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

import com.teragrep.aer_01.config.AzureConfig;
import com.teragrep.aer_01.config.RelpConfig;
import com.teragrep.aer_01.config.SyslogConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigTest {
    @Test
    public void testConfigFromEnv() {
        AzureConfig azureConfig = new AzureConfig(); // AZURE_NAMESPACE comes from maven
        Assertions.assertEquals("azure_namespace_from_env", azureConfig.getNamespaceName(), "Expected to get config from environment variable");
    }
    @Test
    public void testConfigFromProperty() {
        String expected = "testing.hostname.example.com";
        System.setProperty("syslog.hostname", expected);
        SyslogConfig syslogConfig = new SyslogConfig();
        Assertions.assertEquals(expected, syslogConfig.getHostname(), "Expected to get config from property");
    }
    @Test
    public void testConfigFallback() {
        RelpConfig relpConfig = new RelpConfig();
        Assertions.assertEquals(601, relpConfig.getRelpPort(), "Expected to get fallback value");
    }
}
