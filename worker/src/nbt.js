
const END = 0;
const BYTE = 1;
const SHORT = 2;
const INT = 3;
const LONG = 4;
const FLOAT = 5;
const DOUBLE = 6;
const BYTE_ARRAY = 7;
const STRING = 8;
const LIST = 9;
const COMPOUND = 10;
const INT_ARRAY = 11;
const LONG_ARRAY = 12;

const DECODER = new TextDecoder();

const MAX_BYTES = 8 * 1024 * 1024;
const MAX_TAGS = 400_000;

export async function decodeNbt(base64) {
    const packed = unbase64(base64);
    const bytes = await gunzip(packed);
    if (bytes.length > MAX_BYTES) {
        throw new Error('NBT is too large to read');
    }

    const reader = {
        bytes: bytes,
        view: new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength),
        at: 0,
        tags: 0,
    };

    if (byteAt(reader) !== COMPOUND) {
        throw new Error('NBT does not start with a compound');
    }
    readString(reader);
    return readCompound(reader);
}

function unbase64(text) {
    const binary = atob(text);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        out[i] = binary.charCodeAt(i);
    }
    return out;
}

async function gunzip(bytes) {
    const stream = new Blob([bytes]).stream()
        .pipeThrough(new DecompressionStream('gzip'));
    return new Uint8Array(await new Response(stream).arrayBuffer());
}

function readCompound(reader) {
    const out = {};
    for (;;) {
        const type = byteAt(reader);
        if (type === END) {
            return out;
        }
        const name = readString(reader);
        out[name] = readPayload(reader, type);
    }
}

function readPayload(reader, type) {
    if (++reader.tags > MAX_TAGS) {
        throw new Error('NBT has too many tags');
    }

    switch (type) {
        case BYTE:
            return reader.view.getInt8(take(reader, 1));
        case SHORT:
            return reader.view.getInt16(take(reader, 2));
        case INT:
            return reader.view.getInt32(take(reader, 4));
        case LONG:
            return reader.view.getBigInt64(take(reader, 8));
        case FLOAT:
            return reader.view.getFloat32(take(reader, 4));
        case DOUBLE:
            return reader.view.getFloat64(take(reader, 8));
        case BYTE_ARRAY:
            return readBytes(reader, readLength(reader));
        case STRING:
            return readString(reader);
        case LIST:
            return readList(reader);
        case COMPOUND:
            return readCompound(reader);
        case INT_ARRAY:
            return readNumbers(reader, 4);
        case LONG_ARRAY:
            return readNumbers(reader, 8);
        default:
            throw new Error(`Unknown NBT tag ${type}`);
    }
}

function readList(reader) {
    const type = byteAt(reader);
    const length = readLength(reader);
    const out = new Array(length);
    for (let i = 0; i < length; i++) {

        out[i] = type === END ? null : readPayload(reader, type);
    }
    return out;
}

function readNumbers(reader, width) {
    const length = readLength(reader);
    const out = new Array(length);
    for (let i = 0; i < length; i++) {
        out[i] = width === 4
            ? reader.view.getInt32(take(reader, 4))
            : reader.view.getBigInt64(take(reader, 8));
    }
    return out;
}

function readString(reader) {
    const length = reader.view.getUint16(take(reader, 2));
    return DECODER.decode(readBytes(reader, length));
}

function readBytes(reader, length) {
    const start = take(reader, length);
    return reader.bytes.subarray(start, start + length);
}

function byteAt(reader) {
    return reader.view.getUint8(take(reader, 1));
}

function readLength(reader) {
    const length = reader.view.getInt32(take(reader, 4));
    if (length < 0 || length > reader.bytes.length - reader.at) {
        throw new Error('NBT length is out of range');
    }
    return length;
}

function take(reader, count) {
    const start = reader.at;
    if (count < 0 || start + count > reader.bytes.length) {
        throw new Error('NBT ran past the end');
    }
    reader.at = start + count;
    return start;
}
