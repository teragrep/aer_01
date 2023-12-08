package com.teragrep.aer_01.config.source;

public final class ConfigSource implements Souceable {

    public final Souceable souceable;

    public ConfigSource() {
        this.souceable = getType();
    }

    private Souceable getType() {
        String type = System.getProperty("config.source", "properties");

        Souceable rv;
        if ("properties".equals(type)) {
            rv = new PropertySource();
        }
        else if ("environment".equals(type)) {
            rv = new EnvironmentSource();
        }
        else {
            throw new IllegalArgumentException("config.source not within supported types: [properties, environment]");
        }

        return rv;
    }

    @Override
    public String source(String name, String defaultValue) {
        return souceable.source(name, defaultValue);
    }
}
