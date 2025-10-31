/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Version;

import java.util.UUID;

/**
 * Assertion event request IQ.
 * This IQ reports technical information when an assertion represented by an AssertPoint failed.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"debcf418-2d3d-4477-97e1-8f7b4507ce8a",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AssertionIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"applicationId", "type":"uuid"},
 *     {"name":"applicationVersionMajor", "type":"int"},
 *     {"name":"applicationVersionMinor", "type":"int"},
 *     {"name":"applicationVersionPatch", "type":"int"},
 *     {"name":"assertion", "type":"int"},
 *     {"name":"timestamp", "type":"long"},
 *     {"name":"valueCount", "type":"int"},[
 *       {"name":"type", ["int", "long", "uuid", "class", ]}
 *       {"name":"value", "type": ["long", "uuid", "class"]}
 *     ]
 *  ]
 * }
 * </pre>
 */
class AssertionIQ extends BinaryPacketIQ {
    private static final String LOG_TAG = "AssertionIQ";

    private static class AssertionIQSerializer extends BinaryPacketIQSerializer {

        AssertionIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AssertionIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final AssertionIQ assertionIQ = (AssertionIQ) object;

            encoder.writeUUID(assertionIQ.applicationId);
            encoder.writeInt(assertionIQ.applicationVersion.major);
            encoder.writeInt(assertionIQ.applicationVersion.minor);
            encoder.writeInt(assertionIQ.applicationVersion.patch);
            encoder.writeInt(assertionIQ.assertPoint.getIdentifier());
            encoder.writeLong(assertionIQ.timestamp);
            if (assertionIQ.values == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(assertionIQ.values.list.size());
                for (Object value : assertionIQ.values.list) {
                    int kind = 0;
                    if (value instanceof Pair) {
                        Pair<Object, Object> p = (Pair<Object, Object>) value;
                        if (p.first instanceof AssertPoint.Parameter) {
                            switch ((AssertPoint.Parameter)p.first) {
                                case PEER_CONNECTION_ID:
                                    kind = (1 << 8);
                                    break;
                                case INVOCATION_ID:
                                    kind = (2 << 8);
                                    break;
                                case SCHEMA_ID:
                                    kind = (3 << 8);
                                    break;
                                case RESOURCE_ID:
                                    kind = (4 << 8);
                                    break;
                                case TWINCODE_ID:
                                    kind = (5 << 8);
                                    break;
                                case SCHEMA_VERSION:
                                    kind = (6 << 8);
                                    break;
                                case LENGTH:
                                    kind = (7 << 8);
                                    break;
                                case OPERATION_ID:
                                    kind = (8 << 8);
                                    break;
                                case ENVIRONMENT_ID:
                                    kind = (12 << 8);
                                    break;
                                case SERVICE_ID:
                                    kind = (15 << 8);
                                    break;
                                case MARKER:
                                    kind = (16 << 8);
                                    break;
                            }
                        }
                        value = p.second;
                    }
                    if (value instanceof Integer) {
                        encoder.writeEnum(1 | kind);
                        encoder.writeInt((Integer) value);
                    } else if (value instanceof Long) {
                        encoder.writeEnum(2 | kind);
                        encoder.writeLong((Long) value);
                    } else if (value instanceof UUID) {
                        encoder.writeEnum(3 | kind);
                        encoder.writeUUID((UUID) value);
                    } else if (value instanceof Class) {
                        // If we have a Class, we can report the name (even if it is obfuscated)
                        encoder.writeEnum(4 | kind);
                        encoder.writeString(((Class) value).getName());
                    } else if (value instanceof TwincodeOutbound) {
                        encoder.writeEnum(5 | kind);
                        encodeTwincode(encoder, (TwincodeOutbound) value);
                    } else if (value instanceof TwincodeInbound) {
                        encoder.writeEnum(6 | kind);
                        encodeTwincodeInbound(encoder, (TwincodeInbound) value);
                    } else if (value instanceof TwincodeInvocation) {
                        TwincodeInvocation invocation = (TwincodeInvocation) value;
                        encoder.writeEnum(7 | kind);
                        encoder.writeUUID(invocation.invocationId);
                    } else if (value instanceof RepositoryObject) {
                        encoder.writeEnum(8 | kind);
                        encodeSubject(encoder, (RepositoryObject)value);
                    } else if (value instanceof Version) {
                        Version v = (Version) value;
                        encoder.writeEnum(9 | kind);
                        encoder.writeInt(v.major);
                        encoder.writeInt(v.minor);
                        encoder.writeInt(v.patch);
                    } else if (value instanceof BaseService.ErrorCode) {
                        // For the ErrorCode, send the normalized error code and the enum ordinal.
                        // The normalized value will be identical between iOS and Android but
                        // some error codes are mapped to the code 7 (LibraryError).
                        encoder.writeEnum(10 | (9 << 8));
                        encoder.writeInt(BaseService.ErrorCode.fromErrorCode((BaseService.ErrorCode) value));
                        encoder.writeInt(((BaseService.ErrorCode) value).ordinal());
                    } else if (value instanceof Enum) {
                        encoder.writeEnum(1 | kind);
                        encoder.writeInt(((Enum) value).ordinal());
                    } else {
                        // Don't send any other types
                        encoder.writeEnum(0 | kind);
                        if (Logger.DEBUG) {
                            Log.e(LOG_TAG, "assertion value " + value + " has a wrong type: " + value.getClass());
                        }
                    }
                }
            }

            // We accept to send string in the stack trace because they are only referencing classes, files and method names.
            if (assertionIQ.exception == null) {
                encoder.writeEnum(0);
            } else {
                // Check if we must only report the stack trace and omit the exception class name.
                if (assertionIQ.stackTrace) {
                    encoder.writeEnum(1);
                } else {
                    encoder.writeEnum(2);
                    encoder.writeString(assertionIQ.exception.getClass().getName());
                }

                final StackTraceElement[] stackTraceElements = assertionIQ.exception.getStackTrace();
                int length = stackTraceElements.length;
                if (length > 32) {
                    length = 32;
                }
                encoder.writeInt(length);
                for (int i = 0; i < length; i++) {
                    StackTraceElement st = stackTraceElements[i];
                    encoder.writeOptionalString(st.getClassName());
                    encoder.writeOptionalString(st.getFileName());
                    encoder.writeOptionalString(st.getMethodName());
                    encoder.writeInt(st.getLineNumber());
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }

        private void encodeTwincodeInbound(@NonNull Encoder encoder, @Nullable TwincodeInbound twincodeInbound) throws SerializerException {

            // Send the twincode IN and associated OUT (allows to verify consistency between them).
            if (twincodeInbound == null) {
                encoder.writeZero();
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(twincodeInbound.getId());
                encoder.writeUUID(twincodeInbound.getTwincodeOutbound().getId());
            }
        }

        private void encodeTwincode(@NonNull Encoder encoder, @Nullable TwincodeOutbound twincodeOutbound) throws SerializerException {

            // Send the twincode OUT with flags (allows to verify flags consistency).
            if (twincodeOutbound == null) {
                encoder.writeZero();
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(twincodeOutbound.getId());
                if (twincodeOutbound instanceof TwincodeOutboundImpl) {
                    encoder.writeInt(((TwincodeOutboundImpl) twincodeOutbound).getFlags());
                } else {
                    encoder.writeInt(0);
                }
            }
        }

        private void encodeSubject(@NonNull Encoder encoder, @Nullable RepositoryObject subject) throws SerializerException {

            // Send the subject schema ID which tells what kind of object it is (Profil, Contact, Group, ...)
            // Send the IN, OUT and PEER OUT twincodes.
            if (subject == null) {
                encoder.writeZero();
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(subject.getDatabaseId().getSchemaId());
                encodeTwincodeInbound(encoder, subject.getTwincodeInbound());
                encodeTwincode(encoder, subject.getTwincodeOutbound());
                encodeTwincode(encoder, subject.getPeerTwincodeOutbound());
            }
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new AssertionIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID applicationId;
    @NonNull
    final Version applicationVersion;
    @NonNull
    final AssertPoint assertPoint;
    @Nullable
    final AssertPoint.Values values;
    final boolean stackTrace;
    @Nullable
    final Throwable exception;
    final long timestamp;

    AssertionIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                @NonNull UUID applicationId, @NonNull Version applicationVersion,
                @NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values,
                boolean stackTrace, @Nullable Throwable exception) {

        super(serializer, requestId);

        this.applicationId = applicationId;
        this.applicationVersion = applicationVersion;
        this.assertPoint = assertPoint;
        this.values = values;
        this.stackTrace = stackTrace;
        this.exception = exception;
        this.timestamp = System.currentTimeMillis();
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(assertPoint);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AssertionIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
