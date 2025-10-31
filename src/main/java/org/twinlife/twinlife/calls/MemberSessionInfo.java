/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

/**
 * Description of a member and its optional P2P session id.
 */
class MemberSessionInfo {

    @NonNull
    final String memberId;
    @Nullable
    final UUID p2pSessionId;

    MemberSessionInfo(@NonNull String memberId, @Nullable UUID p2pSessionId) {
        this.memberId = memberId;
        this.p2pSessionId = p2pSessionId;
    }

    static void serialize(@NonNull Encoder encoder,
                          @Nullable MemberSessionInfo[] members) throws SerializerException {

        if (members == null) {
            encoder.writeInt(0);
        } else {
            encoder.writeInt(members.length);
            for (final MemberSessionInfo sessionInfo : members) {
                if (sessionInfo == null) {
                    throw new SerializerException();
                }
                encoder.writeString(sessionInfo.memberId);
                encoder.writeOptionalUUID(sessionInfo.p2pSessionId);
            }
        }
    }

    @Nullable
    static MemberSessionInfo[] deserialize(@NonNull Decoder decoder) throws SerializerException {

        final int count = decoder.readInt();
        if (count == 0) {
            return null;
        }

        final MemberSessionInfo[] members = new MemberSessionInfo[count];
        for (int i = 0; i < count; i++) {
            final String memberId = decoder.readString();
            final UUID p2pSessionId = decoder.readOptionalUUID();

            members[i] = new MemberSessionInfo(memberId, p2pSessionId);
        }
        return members;
    }

}
