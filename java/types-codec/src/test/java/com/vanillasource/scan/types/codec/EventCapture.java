package com.vanillasource.scan.types.codec;

import java.util.ArrayList;
import java.util.List;

final class EventCapture implements DecodingEventHandler {
    final List<DecodingEvent> events = new ArrayList<>();

    @Override
    public void onEvent(DecodingEvent event) {
        events.add(event);
    }
}
