package com.teragrep.aer_01.config.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class EnvironmentSource implements Souceable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentSource.class);

    private final Map<String, String> envValues = System.getenv();

    @Override
    public String source(String name, String defaultValue) {
        String variable = name.toUpperCase().replace(".", "_");
        LOGGER.debug("sourcing name <[{}]> as environment variable <[{}]>", name, variable);
        String rv =  envValues.getOrDefault(variable, defaultValue);
        LOGGER.debug("sourced value <[{}]> for variable <[{}]>", rv, variable);
        return rv;
    }

    @Override
    public String toString() {
        return "EnvironmentSource{" +
                "envValues=" + envValues +
                '}';
    }
}
