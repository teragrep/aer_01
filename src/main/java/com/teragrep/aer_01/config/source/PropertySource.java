package com.teragrep.aer_01.config.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public final class PropertySource implements Souceable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertySource.class);
    private final Properties properties;

    PropertySource() {
        this.properties = System.getProperties();
    }
    @Override
    public String source(String name, String defaultValue) {
        LOGGER.debug("sourcing property name <[{}]>", name);
        String rv = properties.getProperty(name, defaultValue);
        LOGGER.debug("sourced value <[{}]> for property name <[{}]>", rv, name);
        return rv;
    }
}
