/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps a Java Client making http requests stay within the server's rate limits.
 *
 * Defaults to honoring the header fields from https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/. Note that GitHub
 * precedes the header names with "x-", a common practice for non-standard headers (the link is a draft, not formally accepted).
 *
 * <ul>
 *     <li>RateLimit-Limit</li>
 *     <li>RateLimit-Remaining</li>
 *     <li>RateLimit-Reset</li>
 * </ul>
 */
public class ClientRateLimitHelper {
    // public static final String LIMIT_HEADER = "X-RateLimit-Limit";
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    public static final String RESET_HEADER = "X-RateLimit-Reset";
    private static final int BACK_OFF = 10;

    private static final Logger LOG = LoggerFactory.getLogger(ClientRateLimitHelper.class);
    private final Duration maxWait;

    /**
     * Creates a helper, specifying how long to wait for a ratelimit reset.
     * @param maxWait how long to wait for a reset
     */
    public ClientRateLimitHelper(Duration maxWait) {
        this.maxWait = maxWait;
    }

    /**
     * Checks if rate limit headers are present. If they are present, and the remaining limit is less than {@code BACK_OFF}, the method
     * sleeps until the reset time is hit. Otherwise, does nothing.
     *
     * <p>If the wait for the reset time would be more than the <code>maxWait</code> parameter passed to the constructor, then it throws
     * a <code>CustomWebApplicationException</code>.</p>
     *
     * @param headers the headers of an HTTP response
     */
    public void checkRateLimit(Map<String, List<String>> headers) {
        getRemainingAndReset(headers).ifPresent(remainingAndReset -> {
            if (remainingAndReset.remaining < BACK_OFF) {
                LOG.info(headers.toString());
                final Instant now = Instant.now();
                LOG.info("Now is {}", now);
                final Duration durationToReset = Duration.between(now, remainingAndReset.resetTime);
                LOG.info("Duration is {}", durationToReset);
                if (durationToReset.compareTo(maxWait) > 0) {
                    throw new CustomWebApplicationException("Rate limits on an external service hit", HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
                if (!durationToReset.isNegative()) {
                    try {
                        LOG.info("Sleeping for {} seconds", durationToReset.toSeconds());
                        Thread.sleep(durationToReset.toMillis());
                    } catch (InterruptedException e) {
                        LOG.error("Interrupted exception while waiting for reset limit", e);
                    }
                }
            }
        });
    }

    private Optional<RemainingAndReset> getRemainingAndReset(Map<String, List<String>> headers) {
        final Map<String, List<String>> map = new CaseInsensitiveMap(headers);
        final List<String> remaining = map.get(REMAINING_HEADER);
        LOG.info("Remaining is {}", remaining);
        if (remaining != null && !remaining.isEmpty()) {
            final List<String> reset = map.get(RESET_HEADER);
            if (reset != null && !reset.isEmpty()) {
                final int remainingRequests = Integer.parseInt(remaining.get(0));
                final int resetTime = Integer.parseInt(reset.get(0));
                return Optional.of(new RemainingAndReset(remainingRequests, Instant.ofEpochSecond(resetTime + 1)));
            }
        }
        return Optional.empty();
    }

    record RemainingAndReset(int remaining, Instant resetTime) {};
}
