/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RepositoryService extends BaseService<RepositoryService.ServiceObserver> {

    String VERSION = "3.1.0";
    String XML_SERIALIZER = "XML";

    class RepositoryServiceConfiguration extends BaseServiceConfiguration {

        public RepositoryServiceConfiguration() {

            super(BaseServiceId.REPOSITORY_SERVICE_ID, VERSION, false);
        }
    }

    enum AccessRights {
        PRIVATE, PUBLIC, EXCLUSIVE
    }

    /**
     * Statistics collected for a contact or group.
     */
    enum StatType {
        NB_MESSAGE_SENT,
        NB_FILE_SENT,
        NB_IMAGE_SENT,
        NB_VIDEO_SENT,
        NB_AUDIO_SENT,
        NB_GEOLOCATION_SENT,
        NB_TWINCODE_SENT,
        NB_MESSAGE_RECEIVED,
        NB_FILE_RECEIVED,
        NB_IMAGE_RECEIVED,
        NB_VIDEO_RECEIVED,
        NB_AUDIO_RECEIVED,
        NB_GEOLOCATION_RECEIVED,
        NB_TWINCODE_RECEIVED,
        NB_AUDIO_CALL_SENT,
        NB_VIDEO_CALL_SENT,
        NB_AUDIO_CALL_RECEIVED,
        NB_VIDEO_CALL_RECEIVED,
        NB_AUDIO_CALL_MISSED,
        NB_VIDEO_CALL_MISSED,
        AUDIO_CALL_SENT_DURATION,
        AUDIO_CALL_RECEIVED_DURATION,
        VIDEO_CALL_SENT_DURATION,
        VIDEO_CALL_RECEIVED_DURATION;

        private static final Set<StatType> INCOMING_EVENTS =
                Set.of(NB_GEOLOCATION_RECEIVED, NB_TWINCODE_RECEIVED, NB_MESSAGE_RECEIVED, NB_FILE_RECEIVED,
                        NB_IMAGE_RECEIVED, NB_VIDEO_RECEIVED, NB_AUDIO_RECEIVED, NB_AUDIO_CALL_RECEIVED,
                        NB_AUDIO_CALL_MISSED, NB_VIDEO_CALL_RECEIVED, NB_VIDEO_CALL_MISSED);

        public boolean isIncoming() {
            return INCOMING_EVENTS.contains(this);
        }
    }

    /**
     * Weight information to compute the contact/group usage score.
     */
    class Weight {
        public final double points;
        public final double scale;

        public Weight(double points, double scale) {
            this.points = points;
            this.scale = scale;
        }
    }

    /**
     * Statistics collected for a given contact/group.
     */
    class ObjectStatReport {
        @NonNull
        public final DatabaseIdentifier objectId;
        @NonNull
        public final long[] counters;

        public ObjectStatReport(@NonNull DatabaseIdentifier objectId, @NonNull long[] counters) {
            this.counters = counters;
            this.objectId = objectId;
        }
    }
    class StatReport {
        @NonNull
        public final List<ObjectStatReport> stats;
        public final long objectCount;
        public final long certifiedCount;
        public final long invitationCodeCount;

        public StatReport(@NonNull List<ObjectStatReport> stats, long objectCount, long certifiedCount, long invitationCodeCount) {
            this.stats = stats;
            this.objectCount = objectCount;
            this.certifiedCount = certifiedCount;
            this.invitationCodeCount = invitationCodeCount;
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        default void onUpdateObject(@NonNull RepositoryObject object) {}

        default void onDeleteObject(@NonNull UUID objectId) {}

        /**
         * Called by the repository service when it detects that an object is now invalid (isValid() returns false).
         *
         * Note: in most cases, an object becomes invalid when the inbound twincode (private identity) was not found.
         * This could occur when the deletion of Profile/Contact/Group object is stopped in the middle.
         *
         * @param object object that should be deleted.
         */
        default void onInvalidObject(@NonNull RepositoryObject object) {}
    }

    class FindResult {
        @NonNull
        public final ErrorCode errorCode;
        @Nullable
        public final RepositoryObject object;

        @NonNull
        public static FindResult error(@NonNull ErrorCode errorCode) {
            return new FindResult(errorCode, null);
        }

        @NonNull
        public static FindResult ok(@NonNull RepositoryObject object) {
            return new FindResult(ErrorCode.SUCCESS, object);
        }

        private FindResult(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
            this.errorCode = errorCode;
            this.object = object;
        }
    }

    interface Initializer {
        void initialize(@NonNull RepositoryObject object);
    }

    void createObject(@NonNull RepositoryObjectFactory<?> factory, AccessRights accessRights,
                      @NonNull Initializer initializer, @NonNull Consumer<RepositoryObject> complete);

    void getObject(@NonNull UUID objectId, @NonNull RepositoryObjectFactory<?> factory,
                   @NonNull Consumer<RepositoryObject> complete);

    void listObjects(@NonNull RepositoryObjectFactory<?> factory,
                     @Nullable Filter<RepositoryObject> filter,
                     @NonNull Consumer<List<RepositoryObject>> complete);

    @NonNull
    FindResult findObject(boolean withTwincodeInbound, @NonNull UUID key,
                          @NonNull RepositoryObjectFactory<?>[] factories);
    @Nullable
    RepositoryObject findObject(@NonNull UUID key);

    /**
     * Given the authenticate signature, find the subject that matches and verifies that signature.
     * If the signature is invalid, an error code is returned.  If the signature is valid but no public
     * key is found, an ITEM_NOT_FOUND error is returned.
     *
     * @param signature the signature to verify.
     * @param factories the list of factories for the creation of objects.
     * @return result of the search.
     */
    @NonNull
    FindResult findWithSignature(@NonNull String signature, @NonNull RepositoryObjectFactory<?>[] factories);

    void updateObject(@NonNull RepositoryObject object, @NonNull Consumer<RepositoryObject> complete);

    /**
     * Set the owner for every object of the factory.
     *
     * @param factory the factory that identifies objects.
     * @param newOwner the new owner.
     */
    void setOwner(@NonNull RepositoryObjectFactory<?> factory, @NonNull RepositoryObject newOwner);

    void deleteObject(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete);

    boolean hasObjects(@NonNull UUID schemaId);

    void incrementStat(@NonNull RepositoryObject object, StatType kind);

    void updateStat(@NonNull RepositoryObject object, StatType kind, long value);

    void updateStats(@NonNull RepositoryObjectFactory<?> factory, boolean updateScore,
                     @NonNull Consumer<List<RepositoryObject>> consumer);

    @Nullable
    StatReport reportStats(@NonNull UUID schemaId);

    void checkpointStats();

    void setWeightTable(@NonNull UUID schemaId, @NonNull Weight[] weights);
}
