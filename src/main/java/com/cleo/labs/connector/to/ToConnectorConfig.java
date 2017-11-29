package com.cleo.labs.connector.to;

import java.util.stream.Stream;

import com.cleo.connector.api.property.ConnectorPropertyException;
import com.google.common.base.Strings;

public class ToConnectorConfig {
    private ToConnectorClient client;
    private ToConnectorSchema schema;

    public ToConnectorConfig(ToConnectorClient client, ToConnectorSchema schema) {
        this.client = client;
        this.schema = schema;
    }

    /**
     * Gets the list of To folders.  This string property is interpreted
     * as a comma-separated list of folder names.  Resource folders are
     * stored internally using \ as the separator, so after splitting
     * the list on , the folder names are cleaned up by converting any
     * / to \, by also removing any leading or trailing / or \.
     * @return a list of cleaned up folder names, possibly empty, but never {@code null}
     * @throws ConnectorPropertyException
     */
    public String[] getToFolders() throws ConnectorPropertyException {
        String raw = schema.toFolders.getValue(client);
        if (Strings.isNullOrEmpty(raw)) {
            return new String[0];
        }
        return Stream.of(raw.split("\\s*,\\s*"))
                .map((s) -> s.replaceAll("[/\\\\]+", "\\\\"))               // combine all \ and / into \
                .map((s) -> s.replaceFirst("^\\\\?(.*?)\\\\?$", "$1"))   // remove leading and trailing \
                .toArray(String[]::new);
    }

    /**
     * Gets the Error Destination property.
     * @return the Error Destination
     * @throws ConnectorPropertyException
     */
    public String getErrorDestination() throws ConnectorPropertyException {
        return schema.errorDestination.getValue(client);
    }

    /**
     * Gets the Force Unique property.
     * @return the Force Unique property
     * @throws ConnectorPropertyException
     */
    public boolean getForceUnique() throws ConnectorPropertyException {
        return schema.forceUnique.getValue(client);
    }
}
