package com.vanillasource.scan.types.codec.event;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;

import java.util.List;

public final class ListEventSource implements EventSource {
   private final List<Event> events;
   private int pos = 0;

   public ListEventSource(List<Event> events) {
      this.events = events;
   }

   @Override
   public int availableEvents() {
      return events.size() - pos;
   }

   @Override
   public Event read() {
      return events.get(pos++);
   }
}
