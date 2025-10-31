/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Pair;

import org.twinlife.twinlife.util.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assertion point declaration used to report technical issues in the application.
 * It is associated with a number returned by `getIdentifier()` and a list of restricted
 * and well controlled values can be reported together with the failure.  The list of
 * values is voluntarily restricted to:
 * - UUID, Integer, TwincodeInbound, TwincodeOutbound, RepositoryObject, ErrorCode
 * - specific parameters: peerConnectionId, invocationId, schemaId, twincodes,
 *   schema version, length, error code, operation ids
 * Strings must never be sent with an assertion failure because we cannot be sure
 * they do not contain user data.
 */
public interface AssertPoint {
    // Enum to identify a possible assertion parameter that is reported with a failed assertion.
    enum Parameter {
        PEER_CONNECTION_ID,
        INVOCATION_ID,
        SCHEMA_ID,
        RESOURCE_ID,
        TWINCODE_ID,
        ENVIRONMENT_ID,
        SCHEMA_VERSION,
        LENGTH,
        OPERATION_ID,
        SERVICE_ID,
        MARKER
    }

    // List of values that will be sent with the assertion point.
    // Note: only allowed objects of allowed types are taken into account
    // If the list contains a forbidden value (such as a String), a null
    // value will be sent to the server and this is intentional to make
    // sure we don't leak information.
    class Values {
        public final List<Object> list = new ArrayList<>();

        public Values put(Class value) {
            list.add(value);
            return this;
        }

        public Values put(long value) {
            list.add(value);
            return this;
        }

        public Values putOperationId(int operationId) {
            list.add(new Pair<>(Parameter.OPERATION_ID, operationId));
            return this;
        }

        public Values putLength(long value) {
            list.add(new Pair<>(Parameter.LENGTH, value));
            return this;
        }

        public Values putMarker(int value) {
            list.add(new Pair<>(Parameter.MARKER, value));
            return this;
        }

        public Values putVersion(int major, int minor) {
            list.add(new Version(major, minor));
            return this;
        }

        public Values putSchemaVersion(int value) {
            list.add(new Pair<>(Parameter.SCHEMA_VERSION, value));
            return this;
        }

        public Values putSchemaId(UUID value) {
            list.add(new Pair<>(Parameter.SCHEMA_ID, value));
            return this;
        }

        public Values putPeerConnectionId(UUID value) {
            list.add(new Pair<>(Parameter.PEER_CONNECTION_ID, value));
            return this;
        }

        public Values putEnvironmentId(UUID value) {
            list.add(new Pair<>(Parameter.ENVIRONMENT_ID, value));
            return this;
        }

        public Values putInvocationId(UUID value) {
            list.add(new Pair<>(Parameter.INVOCATION_ID, value));
            return this;
        }

        public Values putResourceId(UUID value) {
            list.add(new Pair<>(Parameter.RESOURCE_ID, value));
            return this;
        }

        public Values putTwincodeId(UUID value) {
            list.add(new Pair<>(Parameter.TWINCODE_ID, value));
            return this;
        }

        public Values put(Enum value) {
            list.add(value);
            return this;
        }

        public Values put(TwincodeOutbound twincodeOutbound) {
            list.add(twincodeOutbound);
            return this;
        }

        public Values put(TwincodeInbound twincodeInbound) {
            list.add(twincodeInbound);
            return this;
        }

        public Values put(TwincodeInvocation invocation) {
            list.add(invocation);
            return this;
        }

        public Values put(RepositoryObject repositoryObject) {
            list.add(repositoryObject);
            return this;
        }
    }

    static Values create(Class clazz) {
        return new Values().put(clazz);
    }

    static Values create(Enum value) {
        return new Values().put(value);
    }

    static Values createLength(long value) {
        return new Values().putLength(value);
    }

    static Values createMarker(int value) {
        return new Values().putMarker(value);
    }

    static Values createPeerConnectionId(UUID value) {
        return new Values().putPeerConnectionId(value);
    }

    static Values createEnvironmentId(UUID value) {
        return new Values().putEnvironmentId(value);
    }

    static Values create(RepositoryObject value) {
        return new Values().put(value);
    }

    static Values create(TwincodeOutbound value) {
        return new Values().put(value);
    }

    static Values create(TwincodeInvocation value) {
        return new Values().put(value);
    }

    int getIdentifier();

    default boolean stackTrace() {

        return false;
    }
}