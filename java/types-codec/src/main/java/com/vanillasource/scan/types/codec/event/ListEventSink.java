package com.vanillasource.scan.types.codec.event;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;

import java.util.List;

public final class ListEventSink implements EventSink {
   private final List<Event> events;

   public ListEventSink(List<Event> events) {
      this.events = events;
   }

   @Override
   public int writableEvents() {
      return Integer.MAX_VALUE;
   }

   @Override
   public void write(Event event) {
      events.add(event);
   }
}
