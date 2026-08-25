// Minimal ZIP for code packages: store-only writer + reader.
// Reader inflates deflate entries via the browser's native DecompressionStream, so no
// third-party dependency is needed and it runs entirely client-side (GitHub Pages safe).

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}

const enc = s => new TextEncoder().encode(s);
const dec = b => new TextDecoder().decode(b);

// files: { name: textString } -> Uint8Array (ZIP, stored/no compression)
export function zipStore(files) {
  const chunks = [];
  let offset = 0;
  const push = b => { chunks.push(b); offset += b.length; };
  const u16 = v => new Uint8Array([v & 255, (v >> 8) & 255]);
  const u32 = v => new Uint8Array([v & 255, (v >> 8) & 255, (v >> 16) & 255, (v >>> 24) & 255]);

  const entries = [];
  for (const name of Object.keys(files)) {
    const data = enc(files[name]);
    const nameB = enc(name);
    const crc = crc32(data);
    const localOff = offset;
    push(u32(0x04034b50));
    push(u16(20)); push(u16(0)); push(u16(0));   // version, flags, method(0=store)
    push(u16(0));  push(u16(0));                  // mod time, mod date
    push(u32(crc)); push(u32(data.length)); push(u32(data.length));
    push(u16(nameB.length)); push(u16(0));        // name len, extra len
    push(nameB);
    push(data);
    entries.push({ nameB, crc, size: data.length, localOff });
  }

  const cdStart = offset;
  for (const e of entries) {
    push(u32(0x02014b50));
    push(u16(20)); push(u16(20)); push(u16(0)); push(u16(0)); // ver made/needed, flags, method
    push(u16(0)); push(u16(0));                                // time, date
    push(u32(e.crc)); push(u32(e.size)); push(u32(e.size));
    push(u16(e.nameB.length)); push(u16(0)); push(u16(0));     // name, extra, comment
    push(u16(0)); push(u16(0)); push(u32(0));                  // disk, int attr, ext attr
    push(u32(e.localOff));
    push(e.nameB);
  }
  const cdSize = offset - cdStart;
  push(u32(0x06054b50));
  push(u16(0)); push(u16(0));
  push(u16(entries.length)); push(u16(entries.length));
  push(u32(cdSize)); push(u32(cdStart)); push(u16(0));

  const out = new Uint8Array(offset);
  let p = 0;
  for (const c of chunks) { out.set(c, p); p += c.length; }
  return out;
}

// bytes: Uint8Array -> { name: textString }  (walks local file headers; store + deflate)
export async function unzip(bytes) {
  const files = {};
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let i = 0;
  while (i + 4 <= bytes.length && dv.getUint32(i, true) === 0x04034b50) {
    const method   = dv.getUint16(i + 8, true);
    const compSize = dv.getUint32(i + 18, true);
    const nameLen  = dv.getUint16(i + 26, true);
    const extraLen = dv.getUint16(i + 28, true);
    const nameStart = i + 30;
    const name = dec(bytes.subarray(nameStart, nameStart + nameLen));
    const dataStart = nameStart + nameLen + extraLen;
    const comp = bytes.subarray(dataStart, dataStart + compSize);
    let data;
    if (method === 0) data = comp;
    else if (method === 8) data = await inflateRaw(comp);
    else throw new Error('unsupported zip method ' + method);
    if (!name.endsWith('/')) files[name] = dec(data);
    i = dataStart + compSize;
  }
  return files;
}

async function inflateRaw(comp) {
  const stream = new Blob([comp]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
