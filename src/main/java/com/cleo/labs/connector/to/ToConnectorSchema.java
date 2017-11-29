package com.cleo.labs.connector.to;

import java.io.IOException;

import com.cleo.connector.api.ConnectorConfig;
import com.cleo.connector.api.annotations.Client;
import com.cleo.connector.api.annotations.Connector;
import com.cleo.connector.api.annotations.ExcludeType;
import com.cleo.connector.api.annotations.Info;
import com.cleo.connector.api.annotations.Property;
import com.cleo.connector.api.interfaces.IConnectorProperty;
import com.cleo.connector.api.property.CommonProperties;
import com.cleo.connector.api.property.CommonProperty;
import com.cleo.connector.api.property.PropertyBuilder;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

@Connector(scheme = "to", description = "Send To Outboxes",
           excludeType = { @ExcludeType(type = ExcludeType.SentReceivedBoxes),
                           @ExcludeType(type = ExcludeType.Exchange) })
@Client(ToConnectorClient.class)
public class ToConnectorSchema extends ConnectorConfig {
    @Property
    final IConnectorProperty<String> toFolders = new PropertyBuilder<>("ToFolders", "")
            .setRequired(false)
            .setAllowedInSetCommand(true)
            .setDescription("A list of folders, separated by commas, where files should be sent")
            .build();

    @Property
    final IConnectorProperty<String> errorDestination = new PropertyBuilder<>("ErrorDestination", "")
            .setDescription("An optional destination expression for files that do not match any routing rules.")
            .setRequired(false)
            .build();

    @Property
    final IConnectorProperty<Boolean> forceUnique = new PropertyBuilder<>("ForceUnique", false)
            .setDescription("Always create unique filenames as if PUT -UNI were used")
            .build();

    @Property
    final IConnectorProperty<Boolean> enableDebug = CommonProperties.of(CommonProperty.EnableDebug);

    @Info
    protected static String info() throws IOException {
        return Resources.toString(ToConnectorSchema.class.getResource("info.txt"), Charsets.UTF_8);
    }
}
