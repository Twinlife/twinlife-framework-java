/*
 *  Copyright (c) 2015-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

/*
 * Based on org.apache.avro.util.Utf8.java
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.annotation.NonNull;

import java.nio.charset.Charset;

public class Utf8 {

    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static byte[] getBytes(String content) {

        if (content == null) {

            return null;
        }

        return content.getBytes(UTF8);
    }

    @NonNull
    public static String create(@NonNull byte[] data, int length) {

        return new String(data, 0, length, Utf8.UTF8);
    }
}
