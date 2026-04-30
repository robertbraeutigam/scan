package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.bit.FedBitSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Drives a {@link TypeDefinition} AST through the {@link Meta#META_TYPE}
 * runtime type's encoder and decoder. Iteration 2 plumbing — flattens the AST
 * into the {@code types-codec} {@link Event} vocabulary on the way out and
 * rebuilds the AST records from the event stream on the way in. UTF-8 is used
 * for all name strings.
 */
final class MetaCodec {
    private MetaCodec() {
    }

    static byte[] serialize(TypeDefinition def) {
        List<Event> events = new ArrayList<>();
        new EventWriter(events).writeTypeDefinition(def);

        ListEventSource src = new ListEventSource(events);
        BufferedBitSink sink = new BufferedBitSink();
        ValueEncoder enc = Meta.META_TYPE.createEncoder();
        if (!enc.generate(src, sink)) {
            throw new IllegalStateException("META encoder did not complete with full event input");
        }
        sink.closeBits();
        if (src.availableEvents() != 0) {
            throw new IllegalStateException("META encoder left " + src.availableEvents() + " unconsumed events");
        }
        return sink.toBytes();
    }

    static TypeDefinition deserialize(byte[] bytes) {
        FedBitSource src = new FedBitSource();
        if (bytes.length > 0) {
            src.write(bytes, 0, bytes.length);
        }
        List<Event> events = new ArrayList<>();
        ListEventSink sink = new ListEventSink(events);
        ValueDecoder dec = Meta.META_TYPE.createDecoder();
        if (!dec.parse(src, sink)) {
            throw new IllegalArgumentException("incomplete TypeDefinition encoding");
        }
        EventReader r = new EventReader(events);
        TypeDefinition def = r.readTypeDefinition();
        if (r.hasMore()) {
            throw new IllegalStateException("trailing events after TypeDefinition");
        }
        return def;
    }

    private static final class EventWriter {
        private final List<Event> events;

        EventWriter(List<Event> events) {
            this.events = events;
        }

        void writeTypeDefinition(TypeDefinition def) {
            field(0, () -> writeString(def.name()));
            field(1, () -> writeArray(def.parameters(), this::writeParameter));
            field(2, () -> writeArray(def.constructors(), this::writeConstructor));
        }

        private void writeParameter(ParameterDefinition p) {
            field(0, () -> writeString(p.name()));
            field(1, () -> writeExpression(p.type()));
            field(2, () -> writeOptionExpression(p.defaultValue()));
        }

        private void writeConstructor(ConstructorDefinition c) {
            field(0, () -> writeString(c.name()));
            field(1, () -> writeArray(c.fields(), this::writeField));
        }

        private void writeField(FieldDefinition f) {
            field(0, () -> writeString(f.name()));
            field(1, () -> writeExpression(f.type()));
        }

        private void writeOptionExpression(Optional<Expression> opt) {
            if (opt.isEmpty()) {
                events.add(new Event.Constructor(0));
            } else {
                events.add(new Event.Constructor(1));
                field(0, () -> writeExpression(opt.get()));
            }
        }

        private void writeExpression(Expression e) {
            if (e instanceof Expression.Invocation inv) {
                events.add(new Event.Constructor(0));
                field(0, () -> writeString(inv.name()));
                field(1, () -> writeArray(inv.arguments(), this::writeExpression));
            } else if (e instanceof Expression.IntegerLiteral il) {
                events.add(new Event.Constructor(1));
                long v = il.value();
                field(0, () -> events.add(new Event.IntegerScalar(v, Long.signum(v))));
            } else if (e instanceof Expression.FloatLiteral fl) {
                events.add(new Event.Constructor(2));
                field(0, () -> events.add(new Event.FloatingPointScalar(fl.value())));
            } else if (e instanceof Expression.StringLiteral sl) {
                events.add(new Event.Constructor(3));
                field(0, () -> writeString(sl.value()));
            } else {
                throw new IllegalStateException("unknown Expression: " + e.getClass());
            }
        }

        private void writeString(String s) {
            byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
            events.add(new Event.StartContainer(ContainerKind.ARRAY, utf8.length));
            if (utf8.length > 0) {
                events.add(new Event.Chunk(utf8));
            }
            events.add(new Event.EndContainer(ContainerKind.ARRAY));
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

        TypeDefinition readTypeDefinition() {
            String name = inField(0, this::readString);
            List<ParameterDefinition> parameters = inField(1, () -> readArray(this::readParameter));
            List<ConstructorDefinition> constructors = inField(2, () -> readArray(this::readConstructor));
            return new TypeDefinition(name, parameters, constructors);
        }

        private ParameterDefinition readParameter() {
            String name = inField(0, this::readString);
            Expression type = inField(1, this::readExpression);
            Optional<Expression> def = inField(2, this::readOptionExpression);
            return new ParameterDefinition(name, type, def);
        }

        private ConstructorDefinition readConstructor() {
            String name = inField(0, this::readString);
            List<FieldDefinition> fields = inField(1, () -> readArray(this::readField));
            return new ConstructorDefinition(name, fields);
        }

        private FieldDefinition readField() {
            String name = inField(0, this::readString);
            Expression type = inField(1, this::readExpression);
            return new FieldDefinition(name, type);
        }

        private Optional<Expression> readOptionExpression() {
            int ctor = ((Event.Constructor) next()).index();
            if (ctor == 0) {
                return Optional.empty();
            }
            return Optional.of(inField(0, this::readExpression));
        }

        private Expression readExpression() {
            int ctor = ((Event.Constructor) next()).index();
            switch (ctor) {
                case 0: {
                    String name = inField(0, this::readString);
                    List<Expression> args = inField(1, () -> readArray(this::readExpression));
                    return new Expression.Invocation(name, args);
                }
                case 1: {
                    long v = inField(0, () -> ((Event.IntegerScalar) next()).value());
                    return new Expression.IntegerLiteral(v);
                }
                case 2: {
                    double v = inField(0, () -> ((Event.FloatingPointScalar) next()).value());
                    return new Expression.FloatLiteral(v);
                }
                case 3: {
                    String v = inField(0, this::readString);
                    return new Expression.StringLiteral(v);
                }
                default:
                    throw new IllegalStateException("invalid Expression constructor: " + ctor);
            }
        }

        private String readString() {
            long count = ((Event.StartContainer) next()).count();
            byte[] bytes = new byte[Math.toIntExact(count)];
            int written = 0;
            while (written < count) {
                Event.Chunk chunk = (Event.Chunk) next();
                System.arraycopy(chunk.bytes(), 0, bytes, written, chunk.bytes().length);
                written += chunk.bytes().length;
            }
            expect(Event.EndContainer.class);
            return new String(bytes, StandardCharsets.UTF_8);
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
