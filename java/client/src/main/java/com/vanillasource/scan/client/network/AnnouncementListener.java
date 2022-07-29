/**
 * Copyright (C) 2022 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network;

import java.util.function.Consumer;

/**
 * Listen to announcements of new devices or to answers for a query.
 * Answers are public keys of the devices.
 */
public interface AnnouncementListener extends Consumer<byte[]> {
}
