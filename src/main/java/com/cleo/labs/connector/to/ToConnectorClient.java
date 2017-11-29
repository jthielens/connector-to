package com.cleo.labs.connector.to;

import static com.cleo.connector.api.command.ConnectorCommandName.ATTR;
import static com.cleo.connector.api.command.ConnectorCommandName.PUT;
import static com.cleo.connector.api.command.ConnectorCommandOption.Delete;
import static com.cleo.connector.api.command.ConnectorCommandOption.Unique;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.ConnectorException;
import com.cleo.connector.api.annotations.Command;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandUtil;
import com.cleo.connector.api.command.PutCommand;
import com.cleo.connector.api.interfaces.IConnectorOutgoing;
import com.cleo.lexicom.beans.LexBean;
import com.cleo.lexicom.beans.LexFile;
import com.cleo.lexicom.beans.LexHostBean;
import com.cleo.lexicom.beans.LexMailboxBean;
import com.cleo.lexicom.beans.LocalUserHost;
import com.cleo.lexicom.beans.LocalUserMailbox;
import com.cleo.lexicom.beans.MacroReplacement;
import com.cleo.lexicom.streams.LexFileOutputStream;
import com.cleo.util.MacroUtil;
import com.google.common.base.Strings;

public class ToConnectorClient extends ConnectorClient {
    private ToConnectorConfig config;

    /**
     * Constructs a new {@code ToConnectorClient} for the schema
     * @param schema the {@code ToConnectorSchema}
     */
    public ToConnectorClient(ToConnectorSchema schema) {
        this.config = new ToConnectorConfig(this, schema);
    }

    /**
     * Figures out the best intent of the user for the destination filename to use:
     * <ul><li>if a destination path is provided, use it (e.g. PUT source destination or
     *         through a URI, LCOPY source router:host/destination).</li>
     *     <li>if the destination path matches the host alias (e.g. LCOPY source router:host),
     *         prefer the source filename</li>
     *     <li>if the destination is not useful and the source is not empty, use it</li>
     * @param put the {@link PutCommand}
     * @return a String to use as the filename
     */
    private String bestFilename(PutCommand put) {
        String destination = put.getDestination().getPath();
        if (Strings.isNullOrEmpty(destination) || destination.equals(getHost().getAlias())) {
            String source = put.getSource().getPath();
            if (!Strings.isNullOrEmpty(source)) {
                destination = source;
            }
        }
        return destination;
    }
    private static final Pattern HEX_ASCII = Pattern.compile("%([2-6][0-9a-fA-f]|7[0-9a-eA-E])");

    private static String decode_ascii(String s) {
        if (s == null) {
            return s;
        }
        Matcher m = HEX_ASCII.matcher(s.replace('+', ' '));
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            char replacement = (char) Byte.parseByte(m.group(1), 16);
            String append;
            if (replacement == '\\' || replacement == '$') {
                append = new String(new char[] { '\\', replacement });
            } else {
                append = new String(new char[] { replacement });
            }
            m.appendReplacement(sb, append);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Only seem to need one of these -- guess the methods should be static?
     */
    private static final MacroReplacement macro = new MacroReplacement();

    /**
     * This code is copied from {@link MacroReplacement}'s
     * {@code findMacroValue} method, with the added step of decoding %nn values
     * between %20 and %7E, which are otherwise erroneously interpreted as macro
     * delimiters.
     * 
     * @param mailbox the LocalUserMailbox whose outbox is needed
     * @return the outbox, fully macro replaced
     * @throws Exception
     */
    private static String getUserOutbox(LocalUserMailbox mailbox) throws Exception {
        LocalUserHost group  = (LocalUserHost) mailbox.getParent();
        String        home   = LexBean.checkDirectory(mailbox.buildHome(false), "User Home");
        String        outbox = new com.cleo.lexicom.beans.LexFile(home, mailbox.getOutbox()).getPath();
        outbox = decode_ascii(outbox);
        return macro.replaceMacrosInString(group, mailbox, outbox, MacroUtil.HOST_DIR_HOST);
    }

    /**
     * This code is copied from {@link MacroReplacement}'s
     * {@code findMacroValue} method, with the added step of decoding %nn values
     * between %20 and %7E, which are otherwise erroneously interpreted as macro
     * delimiters.
     * 
     * @param mailbox the LexMailboxBean whose outbox is needed
     * @return the outbox, fully macro replaced
     * @throws Exception
     */
    private static String getOutbox(LexMailboxBean mailbox) throws Exception {
        String outbox;
        if (mailbox instanceof LocalUserMailbox) {
            outbox = getUserOutbox((LocalUserMailbox) mailbox);
        } else {
            LexHostBean host = (LexHostBean) mailbox.getParent();
            outbox = host.getOutbox();
            outbox = decode_ascii(outbox);
            outbox = macro.replaceMacrosInString(host, mailbox, outbox, MacroUtil.HOST_DIR_HOST);
            if (new Boolean(host.properties.getProperty(LexHostBean.AddMailboxAliasDirectoryToOutbox)).booleanValue()) {
                outbox = LexBean.getAbsolute(new com.cleo.lexicom.beans.LexFile(outbox, mailbox.getAlias()).getPath());
            }
        }
        return outbox;
    }

    /**
     * Returns {@code true} if a given string starts with any of a list
     * of prefixes.
     * @param string the string to match
     * @param prefixes a possibly empty but not {@code null} array of prefixes
     * @return {@code true} if the string starts with any of the prefixes
     * @see {@link String#startsWith(String)}
     */
    private static boolean startsWithAny(String string, String[] prefixes) {
        string = Strings.nullToEmpty(string);
        for (String prefix : prefixes) {
            if (string.startsWith(prefix)) {
                return string.length() == prefix.length()
                        || string.charAt(prefix.length()) == '\\';
            }
        }
        return false;
    }

    @Command(name = PUT, options = { Unique, Delete })
    public ConnectorCommandResult put(PutCommand put) throws ConnectorException, IOException {
        String destination = put.getDestination().getPath();
        IConnectorOutgoing source = put.getSource();
        String filename = bestFilename(put);

        logger.debug(String.format("PUT local '%s' to remote '%s' (matching filename '%s')",
                source.getPath(), destination, filename));

        boolean unique = config.getForceUnique() ||
                ConnectorCommandUtil.isOptionOn(put.getOptions(), Unique);

        String[] folders = config.getToFolders();

        OutputStream[] outputs =
                Stream.of(Optional.ofNullable(LexHostBean.allHosts).orElse(new Object[0])) // filter all hosts
                .filter((h) -> h instanceof LexHostBean)                                   // ...that are LexHostBeans
                .map(LexHostBean.class::cast)
                .filter((h) -> startsWithAny(h.getFolder(), folders))                      // ...that are in a requested folder
                .map(LexHostBean::getMailbox)                                              // ...and get their mailboxes
                .flatMap((hb) -> Stream.of(hb))
                .map(LexMailboxBean.class::cast)
                .map((mbx) -> {                                                            // ...and convert to their outboxes
                    try {
                        return getOutbox(mbx);
                    } catch (Exception e) {
                        logger.logWarning(String.format("exception routing to '%s': '%s'", mbx.toPath(true), e.getMessage()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map((outbox) -> {                                                         // ...and convert to LexFileOutputStreams
                    logger.debug(String.format("Routing '%s' to outbox '%s'", filename, outbox));
                    try {
                        LexFile file = new LexFile(outbox, filename);
                        if (unique && file.exists()) {
                            String ext = FilenameUtils.getExtension(filename).replaceFirst("^(?=[^\\.])","."); // prefix with "." unless empty or already "."
                            String base = filename.substring(0, filename.length()-ext.length());
                            int counter = 1;
                            String candidate;
                            do {
                                candidate = base+"."+counter+ext;
                                file = new LexFile(outbox, candidate);
                                counter++;
                            } while (file.exists());
                            logger.debug(String.format("Selected unique filename '%s' for outbox '%s'", outbox, candidate));
                        }
                        return new LexFileOutputStream(file);
                    } catch (Exception e) {
                        logger.logWarning(String.format("Destination '%s' skipped due to error: %s", outbox, e.getMessage()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toArray(OutputStream[]::new);                                            // ...and return as an array

        // If we didn't get any matching outputs, use the error destination, if defined
        if (outputs.length == 0)  {
            String errorDestination = config.getErrorDestination();
            if (!Strings.isNullOrEmpty(errorDestination)) {
                try {
                    outputs = new OutputStream[] {new LexFileOutputStream(new LexFile(errorDestination))};
                } catch (Exception e) {
                    logger.logWarning(String.format("Error Destination '%s' ignored due to error: %s", errorDestination, e.getMessage()));
                    // well, we tried
                }
            }
        }

        // If there is still no output, fail the PUT
        // Otherwise, do the transfer and success!
        if (outputs.length == 0) {
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Error,
                    String.format("No matching routes found for '%s'.", filename));
        } else {
            ParallelOutputStream out = new ParallelOutputStream(outputs);
            transfer(source.getStream(), out, false);
            return new ConnectorCommandResult(ConnectorCommandResult.Status.Success);
        }
    }

    /**
     * Get the file attribute view associated with a file path
     * 
     * @param path the file path
     * @return the file attributes
     * @throws com.cleo.connector.api.ConnectorException
     * @throws java.io.IOException
     */
    @Command(name = ATTR)
    public BasicFileAttributeView getAttributes(String path) throws ConnectorException, IOException {
        logger.debug(String.format("ATTR '%s'", path));
        throw new ConnectorException(String.format("'%s' does not exist or is not accessible", path),
                ConnectorException.Category.fileNonExistentOrNoAccess);
    }
}
