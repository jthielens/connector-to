# README #

This connector is intended to be used to PUT files to one or more outboxes
identified by connection (remote host/mailbox) name, user (omnihost) name,
or folder (in which case all connections, users, and folders contained in
the folder will receive the file in their outbox).

## TL;DR ##

The POM for this project creates a ZIP archive intended to be expanded from
the Harmony/VLTrader installation directory (`$CLEOHOME` below).

```
git clone git@github.com:jthielens/connector-to.git
mvn clean package
cp target/to-5.4.1.0-SNAPSHOT-distribution.zip $CLEOHOME
cd $CLEOHOME
unzip -o to-5.4.1.0-SNAPSHOT-distribution.zip
./Harmonyd stop
./Harmonyd start
```

When Harmony/VLTrader restarts, you will see a new `Template` in the host tree
under `Connections` > `Generic` > `Generic TO`.  Select `Clone and Activate`
and a new `TO` connection (host) will appear on the `Active` tab.

Change the default `<send>` action to read `PUT -DEL test.bin` (instead of `PUT -DEL *`)
and run the action.
