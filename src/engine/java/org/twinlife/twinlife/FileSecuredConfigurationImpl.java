/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileSecuredConfigurationImpl implements ConfigurationService.SecuredConfiguration {
    private static final String LOG_TAG = "FileSecuredConfigurationImpl";
    @NonNull
    private final File mPath;
    private byte[] mData;

    FileSecuredConfigurationImpl(@NonNull File path) {
        mPath = path;
        if (mPath.exists()) {
            try (FileInputStream fs = new FileInputStream(mPath)) {
                int len = (int) mPath.length();
                mData = new byte[len];
                if (fs.read(mData) != len) {
                    Log.e(LOG_TAG, "Reading " + mPath + " was incomplete");
                }

            } catch (IOException ex) {
                Log.e(LOG_TAG, "Cannot load " + mPath.getPath() + ": " + ex.getMessage());

            }
        }
    }

    @Override
    public String getName() {

        return mPath.getName();
    }

    @Override
    public byte[] getData() {

        return mData;
    }

    @Override
    public void setData(byte[] raw) {

        mData = raw;
    }

    void save() {

        if (mData != null) {
            try {
                Files.write(mPath.toPath(), mData, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                Log.e(LOG_TAG, "Cannot save " + mPath.getPath() + ": " + ex.getMessage());

            }
        } else if (mPath.exists()) {
            mPath.delete();
        }
    }
}
