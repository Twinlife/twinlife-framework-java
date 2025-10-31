/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

public class ErrorStats {
    public final long dnsErrorCount;
    public final long tcpErrorCount;
    public final long tlsErrorCount;
    public final long txnErrorCount;
    public final long proxyErrorCount;
    public final long tlsVerifyErrorCount;
    public final long tlsHostErrorCount;
    public final long createCounter;
    public final long connectCounter;

    public ErrorStats(long dnsErrorCount, long tcpErrorCount, long tlsErrorCount, long txnErrorCount,
                      long proxyErrorCount, long tlsVerifyErrorCount, long tlsHostErrorCount, long createCounter,
                      long connectCounter) {
        this.dnsErrorCount = dnsErrorCount;
        this.tcpErrorCount = tcpErrorCount;
        this.tlsErrorCount = tlsErrorCount;
        this.txnErrorCount = txnErrorCount;
        this.proxyErrorCount = proxyErrorCount;
        this.tlsVerifyErrorCount = tlsVerifyErrorCount;
        this.tlsHostErrorCount = tlsHostErrorCount;
        this.createCounter = createCounter;
        this.connectCounter = connectCounter;
    }
}
