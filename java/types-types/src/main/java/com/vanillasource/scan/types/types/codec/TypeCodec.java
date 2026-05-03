package com.vanillasource.scan.types.types.codec;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.FloatingPoint;
import com.vanillasource.scan.types.codec.type.Set;
import com.vanillasource.scan.types.codec.type.SignedInteger;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.codec.type.VariableLengthInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Drives a runtime {@link Type} through the {@link TypeMetaType#META_TYPE}
 * encoder and decoder. Each of the ten meta-type union cases corresponds to
 * one of the {@code com.vanillasource.scan.types.codec.type.*} classes; the
 * codec walks the runtime instance to produce the matching event stream and,
 * on decode, dispatches on the constructor index to rebuild the runtime
 * instance.
 */
public final class TypeCodec {
    private TypeCodec() {
    }

    public static byte[] serialize(Type type) {
        List<Event> events = new ArrayList<>();
        new EventWriter(events).writeType(type);

        ListEventSource src = new ListEventSource(events);
        BufferedBitSink sink = new BufferedBitSink();
        ValueEncoder enc = TypeMetaType.META_TYPE.createEncoder();
        if (!enc.generate(src, sink)) {
            throw new IllegalStateException("META encoder did not complete with full event input");
        }
        sink.closeBits();
        if (src.availableEvents() != 0) {
            throw new IllegalStateException("META encoder left " + src.availableEvents() + " unconsumed events");
        }
        return sink.toBytes();
    }

    public static Type deserialize(byte[] bytes) {
        FedBitSource src = new FedBitSource();
        if (bytes.length > 0) {
            src.write(bytes, 0, bytes.length);
        }
        List<Event> events = new ArrayList<>();
        ListEventSink sink = new ListEventSink(events);
        ValueDecoder dec = TypeMetaType.META_TYPE.createDecoder();
        if (!dec.parse(src, sink)) {
            throw new IllegalArgumentException("incomplete Type encoding");
        }
        EventReader r = new EventReader(events);
        Type type = r.readType();
        if (r.hasMore()) {
            throw new IllegalStateException("trailing events after Type");
        }
        return type;
    }

    private static final class EventWriter {
        private final List<Event> events;

        EventWriter(List<Event> events) {
            this.events = events;
        }

        void writeType(Type t) {
            if (t instanceof Unit) {
                events.add(new Event.Constructor(0));
            } else if (t instanceof UnsignedInteger ui) {
                events.add(new Event.Constructor(1));
                field(0, () -> writeIntegerScalar(ui.byteSize()));
            } else if (t instanceof SignedInteger si) {
                events.add(new Event.Constructor(2));
                field(0, () -> writeIntegerScalar(si.byteSize()));
            } else if (t instanceof VariableLengthInteger vli) {
                events.add(new Event.Constructor(3));
                field(0, () -> writeIntegerScalar(vli.maxBytes()));
            } else if (t instanceof FloatingPoint fp) {
                events.add(new Event.Constructor(4));
                field(0, () -> writeIntegerScalar(fp.byteSize()));
            } else if (t instanceof Struct s) {
                events.add(new Event.Constructor(5));
                field(0, () -> writeArray(s.fieldTypes(), this::writeType));
            } else if (t instanceof Union u) {
                events.add(new Event.Constructor(6));
                field(0, () -> writeArray(u.constructorFields(),
                        ctor -> writeArray(ctor, this::writeType)));
            } else if (t instanceof Array a) {
                events.add(new Event.Constructor(7));
                field(0, () -> writeIntegerScalar(a.min()));
                field(1, () -> writeIntegerScalar(a.max()));
                field(2, () -> writeType(a.itemType()));
            } else if (t instanceof Set s) {
                events.add(new Event.Constructor(8));
                field(0, () -> writeIntegerScalar(s.memberCount()));
            } else if (t instanceof Stream st) {
                events.add(new Event.Constructor(9));
                field(0, () -> writeType(st.itemType()));
            } else {
                throw new IllegalArgumentException("Cannot serialize type: " + t.getClass().getName());
            }
        }

        private void writeIntegerScalar(long value) {
            events.add(new Event.IntegerScalar(value, value == 0 ? 0 : 1));
        }

        private <T> void writeArray(List<T> items, Consumer<T> writeItem) {
            events.add(new Event.StartContainer(ContainerKind.ARRAY, items.size()));
            for (T item : items) {
                events.add(new Event.StartItem());
                writeItem.accept(item);
                events.add(new Event.EndItem());
            }
            events.add(new Event.EndContainer(ContainerKind.ARRAY));
        }

        private void field(int index, Runnable body) {
            events.add(new Event.StartField(index));
            body.run();
            events.add(new Event.EndField(index));
        }
    }

    private static final class EventReader {
        private final List<Event> events;
        private int pos = 0;

        EventReader(List<Event> events) {
            this.events = events;
        }

        boolean hasMore() {
            return pos < events.size();
        }

        Type readType() {
            int ctor = ((Event.Constructor) next()).index();
            switch (ctor) {
                case 0:
                    return new Unit();
                case 1:
                    return new UnsignedInteger(intInField(0));
                case 2:
                    return new SignedInteger(intInField(0));
                case 3:
                    return new VariableLengthInteger(intInField(0));
                case 4:
                    return new FloatingPoint(intInField(0));
                case 5:
                    return new Struct(inField(0, () -> readArray(this::readType)));
                case 6:
                    return new Union(inField(0, () -> readArray(() -> readArray(this::readType))));
                case 7: {
                    int min = intInField(0);
                    int max = intInField(1);
                    Type itemType = inField(2, this::readType);
                    return new Array(min, max, itemType);
                }
                case 8:
                    return new Set(intInField(0));
                case 9:
                    return new Stream(inField(0, this::readType));
                default:
                    throw new IllegalStateException("invalid Type constructor: " + ctor);
            }
        }

        private int intInField(int index) {
            return inField(index, () -> {
                long v = ((Event.IntegerScalar) next()).value();
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("value out of int range: " + v);
                }
                return (int) v;
            });
        }

        private <T> List<T> readArray(Supplier<T> readItem) {
            long count = ((Event.StartContainer) next()).count();
            List<T> out = new ArrayList<>(Math.toIntExact(count));
            for (long i = 0; i < count; i++) {
                expect(Event.StartItem.class);
                out.add(readItem.get());
                expect(Event.EndItem.class);
            }
            expect(Event.EndContainer.class);
            return out;
        }

        private <T> T inField(int index, Supplier<T> body) {
            Event.StartField sf = (Event.StartField) next();
            if (sf.index() != index) {
                throw new IllegalStateException("expected StartField(" + index + ") got StartField(" + sf.index() + ")");
            }
            T value = body.get();
            Event.EndField ef = (Event.EndField) next();
            if (ef.index() != index) {
                throw new IllegalStateException("expected EndField(" + index + ") got EndField(" + ef.index() + ")");
            }
            return value;
        }

        private Event next() {
            return events.get(pos++);
        }

        private void expect(Class<? extends Event> cls) {
            Event e = next();
            if (!cls.isInstance(e)) {
                throw new IllegalStateException("expected " + cls.getSimpleName() + " got " + e);
            }
        }
    }

    private static final class ListEventSource implements EventSource {
        private final List<Event> events;
        private int pos = 0;

        ListEventSource(List<Event> events) {
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

    private static final class ListEventSink implements EventSink {
        private final List<Event> events;

        ListEventSink(List<Event> events) {
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
}
