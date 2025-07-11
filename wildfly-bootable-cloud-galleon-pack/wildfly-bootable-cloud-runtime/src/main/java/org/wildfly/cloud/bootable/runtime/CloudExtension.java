/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.cloud.bootable.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import org.wildfly.core.jar.runtime.RuntimeExtension;

/**
 *
 * @author jdenise
 */
public class CloudExtension implements RuntimeExtension {

    private static final String OPENSHIFT_HOST_NAME_ENV = "HOSTNAME";
    private static final String JBOSS_NODE_NAME_PROPERTY = "jboss.node.name";
    private static final String JBOSS_TX_NODE_ID_PROPERTY = "jboss.tx.node.id";
    private static final Path JBOSS_CONTAINER_DIR = Paths.get("/opt/jboss/container");
    private static final Path JBOSS_CONTAINER_BOOTABLE_DIR = JBOSS_CONTAINER_DIR.resolve("wildfly-bootable-jar");
    private static final Path INSTALL_DIR_FILE = JBOSS_CONTAINER_BOOTABLE_DIR.resolve("install-dir");

    @Override
    public List<String> booting(List<String> args, Path installDir) throws Exception {
        String hostname = System.getenv(OPENSHIFT_HOST_NAME_ENV);
        doBoot(args, installDir, hostname);
        // Handle JGroup.
        String passwordEnv = System.getenv("JGROUPS_CLUSTER_PASSWORD");
        if(passwordEnv != null) {
            List<String> cmds = JGroupsUtil.getAuthProtocolCommands(installDir.resolve("standalone").
                    resolve("configuration").resolve("standalone.xml"));
            return cmds;
        }
        return Collections.emptyList();
    }

    void doBoot(List<String> args, Path installDir, String hostname) throws Exception {
        handleCloud(args, installDir, hostname);
        // Required by Operator
        if (Files.exists(JBOSS_CONTAINER_DIR)) {
            try {
                if (!Files.exists(JBOSS_CONTAINER_BOOTABLE_DIR)) {
                    Files.createDirectory(JBOSS_CONTAINER_BOOTABLE_DIR);
                }
                Files.write(INSTALL_DIR_FILE, installDir.toString().getBytes(), StandardOpenOption.CREATE);
            } catch (IOException ex) {
                System.err.println("Warning, can't generate " + INSTALL_DIR_FILE + ". " + ex);
            }
        }
    }

    private static void handleCloud(List<String> args, Path installDir, String hostname) throws Exception {

        String nodeName = null;
        for (String arg : args) {
            if (arg.startsWith("-D" + JBOSS_NODE_NAME_PROPERTY + "=")) {
                int eq = arg.indexOf("=");
                nodeName = arg.substring(eq + 1, arg.length());
            }
        }

        if (nodeName == null) {
            boolean setNodeName = true;
            nodeName = System.getProperty(JBOSS_NODE_NAME_PROPERTY);
            if (nodeName == null) {
                nodeName = hostname;
            } else {
                setNodeName = false;
            }
            if (nodeName != null) {
                String txId = trunkTxIdValue(nodeName);
                if (setNodeName) {
                    args.add("-D" + JBOSS_NODE_NAME_PROPERTY + "=" + nodeName);
                }
                args.add("-D" + JBOSS_TX_NODE_ID_PROPERTY + "=" + txId);
            }
        } else {
            String txId = trunkTxIdValue(nodeName);
            args.add("-D" + JBOSS_TX_NODE_ID_PROPERTY + "=" + txId);
        }
    }

    private static String trunkTxIdValue(String value) {
        if (value.length() > 23) {
            String originalValue = value;
            StringBuilder builder = new StringBuilder();
            char[] chars = value.toCharArray();
            for (int i = 1; i <= 23; i++) {
                char c = chars[value.length() - i];
                builder.insert(0, c);
            }
            value = builder.toString();
            System.out.println("The HOSTNAME env variable used to set "
                    + JBOSS_TX_NODE_ID_PROPERTY + " is longer than 23 bytes. "
                    + JBOSS_TX_NODE_ID_PROPERTY + " value was adjusted to 23 bytes long string "
                    + value + " from the original value " + originalValue);
        }
        return value;
    }
}
