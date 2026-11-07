// Extracts ONE BIOBUZZ FLOWER (am-5855 "Flower Assembly <1>") from the official field STEP and writes
// web/assets/flower.glb in a flower-local frame, so the sim can instance it on all four walls.
//
// Adapted from scripts/convert-step.mjs in caryden/ftc-biobuzz-sim (MIT, Copyright (c) 2026 Carl Ryden and the
// students and mentors of NCSSM FTC teams 5064, 8569, and 22377). See NOTICE.
//
// Local frame (meters): origin on the tile at the center of the POLLEN stack, +Y up, -Z toward the
// perimeter wall the FLOWER is mounted on, +Z toward the field (the retrieval opening faces +Z and the sides).
// The CAD places Flower <1> on the rear (-Z) wall with its POLLEN stack at X=-0.5942, Z=-1.7282.
//
// Setup (dev machine only, not part of the site):
//   cd tools/cad ; npm install
//   node convert_flower.mjs "<path to BIOBUZZ_Full Field STEP>"
// Default STEP path: ../../../game_field/BIOBUZZ_Full Field.20260912.step  (takes ~5 minutes)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import occtFactory from 'occt-import-js';
import { MeshoptSimplifier } from 'meshoptimizer';

const here = path.dirname(fileURLToPath(import.meta.url));
const STEP = process.argv[2] || path.join(here, '..', '..', '..', 'game_field', 'BIOBUZZ_Full Field.20260912.step');
const OUT = path.join(here, '..', '..', 'web', 'assets', 'flower.glb');
const CENTER = [-0.5942, 0, -1.7282];   // Flower <1> POLLEN stack center on the tile (CAD meters)
const MIN_DIAGONAL = 0.035;             // drop screws, nuts, rivnuts, spacers
const MAX_TRIS = 16000;                 // whole flower after simplification
const SKIP = /Under Field Bracket|Under Tile Washer/;   // hidden below the tiles

await MeshoptSimplifier.ready;
const occt = await occtFactory();
console.log('reading', STEP);
const r = occt.ReadStepFile(new Uint8Array(fs.readFileSync(STEP)), {
  linearUnit: 'meter', linearDeflectionType: 'absolute_value', linearDeflection: 0.0015, angularDeflection: 0.5,
});
if (!r.success) throw new Error('STEP import failed');

const bbox = (p) => {
  const b = [Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity];
  for (let k = 0; k < p.length; k += 3) for (let a = 0; a < 3; a++) { b[a] = Math.min(b[a], p[k + a]); b[a + 3] = Math.max(b[a + 3], p[k + a]); }
  return b;
};
const allMeshes = (n) => [...n.meshes, ...n.children.flatMap(allMeshes)];
const flower = r.root.children[0].children.find(n => /Flower Assembly <1>/.test(n.name));
if (!flower) throw new Error('Flower Assembly <1> not found');

const groups = new Map();   // colorKey -> { pos, nrm, idx, color, names }
for (const i of allMeshes(flower)) {
  const m = r.meshes[i], p = m.attributes.position.array, b = bbox(p);
  if (SKIP.test(m.name) || Math.hypot(b[3] - b[0], b[4] - b[1], b[5] - b[2]) < MIN_DIAGONAL) continue;
  const color = m.color || [0.6, 0.6, 0.6], key = color.map(c => c.toFixed(3)).join(',');
  if (!groups.has(key)) groups.set(key, { pos: [], nrm: [], idx: [], color, names: new Set() });
  const t = groups.get(key), base = t.pos.length / 3;
  t.names.add(m.name);
  for (let k = 0; k < p.length; k += 3) t.pos.push(p[k] - CENTER[0], p[k + 1] - CENTER[1], p[k + 2] - CENTER[2]);
  const n = m.attributes.normal?.array; if (n) for (const v of n) t.nrm.push(v);
  for (const v of m.index.array) t.idx.push(v + base);
}

const totalIn = [...groups.values()].reduce((a, g) => a + g.idx.length / 3, 0);
// small parts (pipes, supports) keep full detail; only the big CNC plates and the backstop are simplified
const SMALL = 2000, isBig = g => g.idx.length / 3 > SMALL;
const bigIn = [...groups.values()].filter(isBig).reduce((a, g) => a + g.idx.length / 3, 0);
const ratio = Math.min(1, (MAX_TRIS - (totalIn - bigIn)) / Math.max(1, bigIn));

// Minimal GLB writer (one node, one primitive per CAD color).
const chunks = []; let byteLength = 0;
const gltf = { asset: { version: '2.0', generator: 'impulse_3dsim convert_flower' }, scene: 0, scenes: [{ nodes: [0] }],
  nodes: [{ name: 'flower', mesh: 0 }], meshes: [{ name: 'flower', primitives: [] }], materials: [], accessors: [], bufferViews: [], buffers: [] };
const addView = (typed, target) => {
  const buf = Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength), pad = (4 - (buf.length % 4)) % 4;
  gltf.bufferViews.push({ buffer: 0, byteOffset: byteLength, byteLength: buf.length, target });
  chunks.push(buf, Buffer.alloc(pad)); byteLength += buf.length + pad;
  return gltf.bufferViews.length - 1;
};
let totalOut = 0;
for (const [key, t] of groups) {
  if (ratio < 1 && isBig(t)) {
    const target = Math.max(3, Math.floor(t.idx.length * ratio / 3) * 3);
    const [ni] = MeshoptSimplifier.simplify(new Uint32Array(t.idx), new Float32Array(t.pos), 3, target, 0.01, []);
    t.idx = Array.from(ni);
  }
  // drop vertices the simplified index buffer no longer uses
  { const remap = new Map(), pos = [], nrm = [], hasN = t.nrm.length === t.pos.length;
    t.idx = t.idx.map(v => { if (!remap.has(v)) { remap.set(v, pos.length / 3); pos.push(t.pos[v*3], t.pos[v*3+1], t.pos[v*3+2]); if (hasN) nrm.push(t.nrm[v*3], t.nrm[v*3+1], t.nrm[v*3+2]); } return remap.get(v); });
    t.pos = pos; t.nrm = hasN ? nrm : []; }
  if (t.pos.length / 3 > 65535) throw new Error('primitive too large for 16-bit indices; lower MAX_TRIS');
  gltf.materials.push({ name: [...t.names].join(' | ').slice(0, 120), pbrMetallicRoughness: { baseColorFactor: [...t.color, 1], metallicFactor: 0.05, roughnessFactor: 0.6 }, doubleSided: true });
  const pos = new Float32Array(t.pos), b = bbox(pos), attrs = {};
  gltf.accessors.push({ bufferView: addView(pos, 34962), componentType: 5126, count: pos.length / 3, type: 'VEC3', min: b.slice(0, 3), max: b.slice(3) });
  attrs.POSITION = gltf.accessors.length - 1;
  if (t.nrm.length === t.pos.length) {
    gltf.accessors.push({ bufferView: addView(new Float32Array(t.nrm), 34962), componentType: 5126, count: pos.length / 3, type: 'VEC3' });
    attrs.NORMAL = gltf.accessors.length - 1;
  }
  const idx = new Uint16Array(t.idx);
  gltf.accessors.push({ bufferView: addView(idx, 34963), componentType: 5123, count: idx.length, type: 'SCALAR' });
  gltf.meshes[0].primitives.push({ attributes: attrs, indices: gltf.accessors.length - 1, material: gltf.materials.length - 1 });
  totalOut += idx.length / 3;
  console.log(key, [...t.names].join(', '), 'tris', idx.length / 3, 'bbox', b.map(v => v.toFixed(4)).join(' '));
}
gltf.buffers.push({ byteLength });
let json = Buffer.from(JSON.stringify(gltf)); json = Buffer.concat([json, Buffer.alloc((4 - (json.length % 4)) % 4, 0x20)]);
const bin = Buffer.concat(chunks);
const header = Buffer.alloc(12); header.writeUInt32LE(0x46546c67, 0); header.writeUInt32LE(2, 4); header.writeUInt32LE(12 + 8 + json.length + 8 + bin.length, 8);
const jh = Buffer.alloc(8); jh.writeUInt32LE(json.length, 0); jh.writeUInt32LE(0x4e4f534a, 4);
const bh = Buffer.alloc(8); bh.writeUInt32LE(bin.length, 0); bh.writeUInt32LE(0x004e4942, 4);
fs.writeFileSync(OUT, Buffer.concat([header, jh, json, bh, bin]));
console.log('triangles', totalIn, '->', totalOut, 'wrote', OUT, (fs.statSync(OUT).size / 1e6).toFixed(2), 'MB');
