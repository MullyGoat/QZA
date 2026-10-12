package com.qza.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.ByteArrayOutputStream;
import java.util.function.Predicate;

final class WindowsMedia {
    static final int CLOSED = 0;
    static final int PLAYING = 4;

    private static final int RO_INIT_MULTITHREADED = 1;
    private static final int ASYNC_STARTED = 0;
    private static final int ASYNC_COMPLETED = 1;
    private static final long ASYNC_TIMEOUT_MS = 3000L;
    private static final int MAX_THUMBNAIL_BYTES = 8 * 1024 * 1024;
    private static final int READ_CHUNK = 256 * 1024;

    private static final Memory IID_MANAGER_STATICS = guid("2050c4ee-11a0-57de-aed7-c97c70338245");
    private static final Memory IID_ASYNC_INFO = guid("00000036-0000-0000-c000-000000000046");
    private static final Memory IID_RANDOM_ACCESS_STREAM = guid("905a0fe1-bc53-11df-8c49-001e4fc686da");
    private static final Memory IID_INPUT_STREAM = guid("905a0fe2-bc53-11df-8c49-001e4fc686da");
    private static final Memory IID_BUFFER_FACTORY = guid("71af914d-c10f-484b-bc50-14bc623b3a27");
    private static final Memory IID_BUFFER_BYTE_ACCESS = guid("905a0fef-bc53-11df-8c49-001e4fc686da");

    private static final int RELEASE = 2;
    private static final int QUERY_INTERFACE = 0;

    private static final int STATICS_REQUEST_ASYNC = 6;
    private static final int MANAGER_GET_CURRENT_SESSION = 6;
    private static final int MANAGER_GET_SESSIONS = 7;
    private static final int SESSION_APP_ID = 6;
    private static final int SESSION_MEDIA_PROPERTIES_ASYNC = 7;
    private static final int SESSION_TIMELINE = 8;
    private static final int SESSION_PLAYBACK_INFO = 9;
    private static final int PROPS_TITLE = 6;
    private static final int PROPS_ARTIST = 9;
    private static final int PROPS_ALBUM = 10;
    private static final int PROPS_THUMBNAIL = 15;
    private static final int TIMELINE_START = 6;
    private static final int TIMELINE_END = 7;
    private static final int TIMELINE_POSITION = 10;
    private static final int TIMELINE_UPDATED = 11;
    private static final int PLAYBACK_STATUS = 7;
    private static final int VECTOR_GET_AT = 6;
    private static final int VECTOR_SIZE = 7;
    private static final int ASYNC_INFO_STATUS = 7;
    private static final int ASYNC_INFO_CANCEL = 9;
    private static final int ASYNC_OPERATION_RESULTS = 8;
    private static final int ASYNC_PROGRESS_RESULTS = 10;
    private static final int STREAM_REFERENCE_OPEN = 6;
    private static final int STREAM_SIZE = 6;
    private static final int INPUT_STREAM_READ = 6;
    private static final int BUFFER_FACTORY_CREATE = 6;
    private static final int BUFFER_LENGTH = 7;
    private static final int BYTE_ACCESS_BUFFER = 3;

    private static Function windowsCreateString;
    private static Function windowsDeleteString;
    private static Function windowsGetStringRawBuffer;
    private static Function roGetActivationFactory;
    private static Pointer manager;
    private static Pointer bufferFactory;

    record Track(String app, String title, String artist, String album, int status,
                 long startTicks, long endTicks, long positionTicks, long updatedTicks) {
        String key() {
            return app + "\n" + title + "\n" + artist + "\n" + album;
        }
    }

    private WindowsMedia() {
    }

    static boolean start() {
        if (!Platform.isWindows()) {
            return false;
        }
        NativeLibrary combase = NativeLibrary.getInstance("combase");
        Function roInitialize = combase.getFunction("RoInitialize");
        windowsCreateString = combase.getFunction("WindowsCreateString");
        windowsDeleteString = combase.getFunction("WindowsDeleteString");
        windowsGetStringRawBuffer = combase.getFunction("WindowsGetStringRawBuffer");
        roGetActivationFactory = combase.getFunction("RoGetActivationFactory");
        roInitialize.invokeInt(new Object[]{RO_INIT_MULTITHREADED});

        Pointer statics = factory("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager",
                IID_MANAGER_STATICS);
        if (statics == null) {
            return false;
        }
        try {
            manager = await(out(statics, STATICS_REQUEST_ASYNC), ASYNC_OPERATION_RESULTS);
        } finally {
            release(statics);
        }
        bufferFactory = factory("Windows.Storage.Streams.Buffer", IID_BUFFER_FACTORY);
        return manager != null;
    }

    static Snapshot read(Predicate<Track> wantThumbnail) {
        Pointer session = pick();
        if (session == null) {
            return null;
        }
        try {
            String app = string(session, SESSION_APP_ID);
            int status = CLOSED;
            Pointer playback = out(session, SESSION_PLAYBACK_INFO);
            if (playback != null) {
                status = integer(playback, PLAYBACK_STATUS, CLOSED);
                release(playback);
            }

            long start = 0;
            long end = 0;
            long position = 0;
            long updated = 0;
            Pointer timeline = out(session, SESSION_TIMELINE);
            if (timeline != null) {
                start = int64(timeline, TIMELINE_START);
                end = int64(timeline, TIMELINE_END);
                position = int64(timeline, TIMELINE_POSITION);
                updated = int64(timeline, TIMELINE_UPDATED);
                release(timeline);
            }

            Pointer props = await(out(session, SESSION_MEDIA_PROPERTIES_ASYNC), ASYNC_OPERATION_RESULTS);
            if (props == null) {
                return null;
            }
            try {
                Track track = new Track(app, string(props, PROPS_TITLE), string(props, PROPS_ARTIST),
                        string(props, PROPS_ALBUM), status, start, end, position, updated);
                byte[] thumbnail = wantThumbnail.test(track) ? thumbnail(props) : null;
                return new Snapshot(track, thumbnail);
            } finally {
                release(props);
            }
        } finally {
            release(session);
        }
    }

    record Snapshot(Track track, byte[] thumbnail) {
    }

    private static Pointer pick() {
        Pointer sessions = out(manager, MANAGER_GET_SESSIONS);
        if (sessions != null) {
            try {
                int size = integer(sessions, VECTOR_SIZE, 0);
                for (int i = 0; i < size; i++) {
                    PointerByReference item = new PointerByReference();
                    if (invoke(sessions, VECTOR_GET_AT, i, item) < 0 || item.getValue() == null) {
                        continue;
                    }
                    Pointer session = item.getValue();
                    Pointer playback = out(session, SESSION_PLAYBACK_INFO);
                    int status = CLOSED;
                    if (playback != null) {
                        status = integer(playback, PLAYBACK_STATUS, CLOSED);
                        release(playback);
                    }
                    if (status == PLAYING) {
                        return session;
                    }
                    release(session);
                }
            } finally {
                release(sessions);
            }
        }
        return out(manager, MANAGER_GET_CURRENT_SESSION);
    }

    private static byte[] thumbnail(Pointer props) {
        Pointer reference = out(props, PROPS_THUMBNAIL);
        if (reference == null || bufferFactory == null) {
            release(reference);
            return null;
        }
        Pointer stream = await(out(reference, STREAM_REFERENCE_OPEN), ASYNC_OPERATION_RESULTS);
        release(reference);
        if (stream == null) {
            return null;
        }
        Pointer random = query(stream, IID_RANDOM_ACCESS_STREAM);
        Pointer input = query(stream, IID_INPUT_STREAM);
        release(stream);
        try {
            if (random == null || input == null) {
                return null;
            }
            long size = int64(random, STREAM_SIZE);
            if (size <= 0 || size > MAX_THUMBNAIL_BYTES) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) size);
            while (bytes.size() < size) {
                int want = (int) Math.min(READ_CHUNK, size - bytes.size());
                byte[] chunk = readChunk(input, want);
                if (chunk == null || chunk.length == 0) {
                    break;
                }
                bytes.write(chunk, 0, chunk.length);
            }
            return bytes.size() > 0 ? bytes.toByteArray() : null;
        } finally {
            release(random);
            release(input);
        }
    }

    private static byte[] readChunk(Pointer input, int want) {
        PointerByReference created = new PointerByReference();
        if (invoke(bufferFactory, BUFFER_FACTORY_CREATE, want, created) < 0 || created.getValue() == null) {
            return null;
        }
        Pointer buffer = created.getValue();
        try {
            PointerByReference op = new PointerByReference();
            if (invoke(input, INPUT_STREAM_READ, buffer, want, 0, op) < 0) {
                return null;
            }
            Pointer result = await(op.getValue(), ASYNC_PROGRESS_RESULTS);
            if (result == null) {
                return null;
            }
            try {
                int length = integer(result, BUFFER_LENGTH, 0);
                if (length <= 0) {
                    return new byte[0];
                }
                Pointer access = query(result, IID_BUFFER_BYTE_ACCESS);
                if (access == null) {
                    return null;
                }
                try {
                    PointerByReference data = new PointerByReference();
                    if (invoke(access, BYTE_ACCESS_BUFFER, data) < 0 || data.getValue() == null) {
                        return null;
                    }
                    return data.getValue().getByteArray(0, Math.min(length, want));
                } finally {
                    release(access);
                }
            } finally {
                release(result);
            }
        } finally {
            release(buffer);
        }
    }

    private static Pointer factory(String className, Memory iid) {
        Pointer name = hstring(className);
        if (name == null) {
            return null;
        }
        try {
            PointerByReference factory = new PointerByReference();
            int hr = roGetActivationFactory.invokeInt(new Object[]{name, iid, factory});
            return hr < 0 ? null : factory.getValue();
        } finally {
            windowsDeleteString.invokeInt(new Object[]{name});
        }
    }

    private static Pointer await(Pointer operation, int resultsSlot) {
        if (operation == null) {
            return null;
        }
        Pointer info = query(operation, IID_ASYNC_INFO);
        if (info == null) {
            release(operation);
            return null;
        }
        try {
            long deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MS;
            IntByReference status = new IntByReference();
            while (true) {
                if (invoke(info, ASYNC_INFO_STATUS, status) < 0) {
                    return null;
                }
                int state = status.getValue();
                if (state == ASYNC_COMPLETED) {
                    break;
                }
                if (state != ASYNC_STARTED) {
                    return null;
                }
                if (System.currentTimeMillis() > deadline) {
                    invoke(info, ASYNC_INFO_CANCEL);
                    return null;
                }
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    invoke(info, ASYNC_INFO_CANCEL);
                    return null;
                }
            }
            PointerByReference result = new PointerByReference();
            return invoke(operation, resultsSlot, result) < 0 ? null : result.getValue();
        } finally {
            release(info);
            release(operation);
        }
    }

    private static Pointer out(Pointer self, int slot) {
        if (self == null) {
            return null;
        }
        PointerByReference value = new PointerByReference();
        return invoke(self, slot, value) < 0 ? null : value.getValue();
    }

    private static int integer(Pointer self, int slot, int fallback) {
        IntByReference value = new IntByReference();
        return invoke(self, slot, value) < 0 ? fallback : value.getValue();
    }

    private static long int64(Pointer self, int slot) {
        LongByReference value = new LongByReference();
        return invoke(self, slot, value) < 0 ? 0L : value.getValue();
    }

    private static String string(Pointer self, int slot) {
        PointerByReference value = new PointerByReference();
        if (invoke(self, slot, value) < 0 || value.getValue() == null) {
            return "";
        }
        Pointer handle = value.getValue();
        try {
            IntByReference length = new IntByReference();
            Pointer raw = windowsGetStringRawBuffer.invokePointer(new Object[]{handle, length});
            if (raw == null || length.getValue() <= 0) {
                return "";
            }
            return new String(raw.getCharArray(0, length.getValue()));
        } finally {
            windowsDeleteString.invokeInt(new Object[]{handle});
        }
    }

    private static Pointer hstring(String text) {
        PointerByReference handle = new PointerByReference();
        int hr = windowsCreateString.invokeInt(new Object[]{new WString(text), text.length(), handle});
        return hr < 0 ? null : handle.getValue();
    }

    private static Pointer query(Pointer self, Memory iid) {
        if (self == null) {
            return null;
        }
        PointerByReference value = new PointerByReference();
        return invoke(self, QUERY_INTERFACE, iid, value) < 0 ? null : value.getValue();
    }

    private static void release(Pointer self) {
        if (self != null) {
            invoke(self, RELEASE);
        }
    }

    private static int invoke(Pointer self, int slot, Object... args) {
        Pointer table = self.getPointer(0);
        Function method = Function.getFunction(table.getPointer((long) slot * Native.POINTER_SIZE));
        Object[] all = new Object[args.length + 1];
        all[0] = self;
        System.arraycopy(args, 0, all, 1, args.length);
        return method.invokeInt(all);
    }

    private static Memory guid(String text) {
        String hex = text.replace("-", "");
        Memory memory = new Memory(16);
        memory.setInt(0, (int) Long.parseLong(hex.substring(0, 8), 16));
        memory.setShort(4, (short) Integer.parseInt(hex.substring(8, 12), 16));
        memory.setShort(6, (short) Integer.parseInt(hex.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) {
            memory.setByte(8 + i, (byte) Integer.parseInt(hex.substring(16 + (i * 2), 18 + (i * 2)), 16));
        }
        return memory;
    }
}
