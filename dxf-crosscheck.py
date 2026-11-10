#!/usr/bin/env python3
"""Cross-check for the Kotlin DxfParser (LaserNesting).

Mirrors the Kotlin grouping logic in plain Python so the sample file can be
verified on a machine without kotlinc/JDK:
  - $INSUNITS units, BLOCKS/INSERT expansion (nested, depth 3, mirror/rotate)
  - ARC sampled at <=5 deg, LWPOLYLINE bulges sampled the same way
  - duplicate-segment removal, 0.01 mm match / 0.1 mm endpoint snap
  - closed-loop tracing (straightest continuation at junctions)
  - outermost loops are parts; loops inside another loop are cutouts
  - CIRCLE inside a part is a hole; CIRCLE outside every part is a round part
  - closed LWPOLYLINE inside another outline is a detail, not a part

Usage: python3 dxf-crosscheck.py [path/to/file.dxf]
"""
import math
import sys
from collections import Counter, defaultdict

PATH = sys.argv[1] if len(sys.argv) > 1 else \
    "/home/hatch/workspace/user/files/1_6_lzph.dxf"

lines = open(PATH, encoding="utf-8", errors="replace").read().splitlines()
pairs = []
i = 0
while i + 1 < len(lines):
    try:
        c = int(lines[i].strip())
    except ValueError:
        i += 1
        continue
    pairs.append((c, lines[i + 1]))
    i += 2


def sec_range(name):
    start = None
    for idx, (c, v) in enumerate(pairs):
        if c == 0 and v.strip() == "SECTION" and idx + 1 < len(pairs) \
                and pairs[idx + 1][1].strip() == name:
            start = idx
        elif start is not None and c == 0 and v.strip() == "ENDSEC":
            return start, idx
    return start, len(pairs)


def parse_ents(ps):
    ents, cur = [], None
    for c, v in ps:
        if c == 0:
            if v.strip() in ("SECTION", "ENDSEC"):
                cur = None
                continue
            cur = {"type": v.strip(), "g": {}, "multi": defaultdict(list)}
            ents.append(cur)
        elif cur is not None:
            cur["g"].setdefault(c, v)
            cur["multi"][c].append(v)
    return ents


def f(e, code, default=0.0):
    try:
        return float(e["g"].get(code, default))
    except ValueError:
        return default


def s(e, code, default=""):
    return e["g"].get(code, default).strip()


# --- header units -------------------------------------------------------
hs, he = sec_range("HEADER")
units = 4
for idx in range(hs, he):
    if pairs[idx][0] == 9 and pairs[idx][1].strip() == "$INSUNITS":
        for j in range(idx + 1, he):
            if pairs[j][0] == 70:
                units = int(pairs[j][1])
                break
        break
SCALE = {1: 25.4, 2: 304.8, 3: 1609344.0, 4: 1.0, 5: 10.0, 6: 1000.0}.get(units, 1.0)
print(f"$INSUNITS={units} scale x{SCALE}")

# --- blocks -------------------------------------------------------------
bs, be = sec_range("BLOCKS")
blocks, cur = {}, None
for e in parse_ents(pairs[bs:be]):
    if e["type"] == "BLOCK":
        cur = s(e, 2)
        blocks[cur] = (f(e, 10), f(e, 20), [])
    elif e["type"] == "ENDBLK":
        cur = None
    elif cur is not None:
        blocks[cur][2].append(e)

es, ee = sec_range("ENTITIES")
eents = [e for e in parse_ents(pairs[es:ee]) if e["type"] != "SECTION"]
print("ENTITIES direct:", dict(Counter(e["type"] for e in eents)))
print(f"blocks defined: {len(blocks)}")


# --- expansion ----------------------------------------------------------
def arc_points(cx, cy, r, a0, a1, step=5.0):
    sweep = (a1 - a0) % 360.0 or 360.0
    n = max(2, math.ceil(sweep / step))
    return [(cx + r * math.cos(math.radians(a0 + sweep * k / n)),
             cy + r * math.sin(math.radians(a0 + sweep * k / n)))
            for k in range(n + 1)]


def xform(pt, ins, sx, sy, rot_deg):
    x, y = pt[0] * sx, pt[1] * sy
    th = math.radians(rot_deg)
    c, sn = math.cos(th), math.sin(th)
    return (ins[0] + x * c - y * sn, ins[1] + x * sn + y * c)


def bulge_edge(p0, p1, bulge):
    if abs(bulge) < 1e-9:
        return [p1]
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    chord = math.hypot(dx, dy)
    if chord < 1e-12:
        return [p1]
    ang = 4 * math.atan(bulge)
    if abs(math.sin(ang / 2)) < 1e-9:
        return [p1]
    r = abs(chord / (2 * math.sin(ang / 2)))
    h = math.sqrt(max(0.0, r * r - (chord / 2) ** 2))
    mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
    sgn = 1 if bulge > 0 else -1
    cx, cy = mx + sgn * h * (-dy / chord), my + sgn * h * (dx / chord)
    a0 = math.degrees(math.atan2(p0[1] - cy, p0[0] - cx))
    a1 = math.degrees(math.atan2(p1[1] - cy, p1[0] - cx))
    sweep = (a1 - a0) % 360
    sweep = sweep if bulge > 0 else sweep - 360
    if sweep == 0:
        return [p1]
    n = max(2, math.ceil(abs(sweep) / 5.0))
    return [(cx + r * math.cos(math.radians(a0 + sweep * k / n)),
             cy + r * math.sin(math.radians(a0 + sweep * k / n)))
            for k in range(1, n + 1)]


def expand(e, ins=(0, 0), sx=1.0, sy=1.0, rot=0.0, depth=0, origin=None):
    t, layer = e["type"], s(e, 8, "0")
    if origin is None:
        origin = layer
    if t == "LINE":
        p1 = xform((f(e, 10), f(e, 20)), ins, sx, sy, rot)
        p2 = xform((f(e, 11), f(e, 21)), ins, sx, sy, rot)
        if p1 != p2:
            yield ("seg", p1, p2, layer, origin)
    elif t == "ARC":
        pts = [xform(p, ins, sx, sy, rot) for p in
               arc_points(f(e, 10), f(e, 20), f(e, 40), f(e, 50), f(e, 51))]
        for a, b in zip(pts, pts[1:]):
            yield ("seg", a, b, layer, origin)
    elif t == "CIRCLE":
        c = xform((f(e, 10), f(e, 20)), ins, sx, sy, rot)
        yield ("circle", c[0], c[1], f(e, 40) * SCALE * (abs(sx) + abs(sy)) / 2,
               layer, origin)
    elif t == "LWPOLYLINE":
        xs, ys = e["multi"].get(10, []), e["multi"].get(20, [])
        bs_ = e["multi"].get(42, [])
        verts = [(float(x), float(y)) for x, y in zip(xs, ys)]
        closed = (int(float(e["g"].get(70, "0"))) & 1) == 1
        pts = [verts[0]]
        for k in range(1, len(verts)):
            pts += bulge_edge(verts[k - 1], verts[k],
                              float(bs_[k - 1]) if k - 1 < len(bs_) else 0.0)
        if closed and len(verts) > 1:
            pts += bulge_edge(verts[-1], verts[0],
                              float(bs_[-1]) if len(bs_) >= len(verts) else 0.0)
        pts = [xform(p, ins, sx, sy, rot) for p in pts]
        if closed:
            yield ("lw", pts, True, layer, origin)
        else:
            for a, b in zip(pts, pts[1:]):
                yield ("seg", a, b, layer, origin)
    elif t == "INSERT":
        if depth >= 3:
            return
        name = s(e, 2)
        blk = blocks.get(name)
        if blk is None:
            return
        bx, by, body = blk
        nsx, nsy = sx * f(e, 41, 1.0), sy * f(e, 42, 1.0)
        nrot = rot + f(e, 50, 0.0)
        nins = xform((f(e, 10), f(e, 20)), ins, sx, sy, rot)
        for be_ in body:
            yield from expand(be_, nins, nsx, nsy, nrot, depth + 1, origin)


segs, circles, lws = [], [], []
for e in eents:
    for item in expand(e, sx=SCALE, sy=SCALE):
        if item[0] == "seg":
            segs.append(item)
        elif item[0] == "circle":
            circles.append(item)
        else:
            lws.append(item)
print(f"expanded: {len(segs)} segments, {len(circles)} circles, "
      f"{len(lws)} closed LWPOLYLINEs")
print("circles by radius:", dict(Counter(round(c[3], 2) for c in circles)))

# --- snap + dedupe (dedupe AFTER snapping, on canonical endpoints) ------
def q(p):
    return (round(p[0] / 0.01), round(p[1] / 0.01))

nodes = list({p for _, a, b, _, _ in segs for p in (a, b)})
cell = 0.1
grid = defaultdict(list)
for idx, p in enumerate(nodes):
    grid[(math.floor(p[0] / cell), math.floor(p[1] / cell))].append(idx)
parent = list(range(len(nodes)))


def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x


for idx, p in enumerate(nodes):
    cx, cy = math.floor(p[0] / cell), math.floor(p[1] / cell)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for j in grid.get((cx + dx, cy + dy), []):
                if j > idx and math.hypot(p[0] - nodes[j][0], p[1] - nodes[j][1]) <= 0.1:
                    ra, rb = find(idx), find(j)
                    if ra != rb:
                        parent[ra] = rb
clusters = defaultdict(list)
for i, p in enumerate(nodes):
    clusters[find(i)].append(p)
rep = {}
for members in clusters.values():
    mx = sum(m[0] for m in members) / len(members)
    my = sum(m[1] for m in members) / len(members)
    for m in members:
        rep[m] = (mx, my)

csegs = []
seen = set()
for _, a, b, layer, origin in segs:
    ca, cb = rep[a], rep[b]
    if ca == cb:
        continue
    k = (q(ca), q(cb)) if q(ca) <= q(cb) else (q(cb), q(ca))
    if k in seen:
        continue
    seen.add(k)
    csegs.append((ca, cb, layer, origin))
print(f"canonical segments after snap+dedupe: {len(csegs)} "
      f"({len(segs) - len(csegs)} degenerate/duplicate removed)")
adj = defaultdict(list)
for idx, (a, b, _, _) in enumerate(csegs):
    adj[a].append((idx, b))
    adj[b].append((idx, a))


def ang(p, q):
    return math.atan2(q[1] - p[1], q[0] - p[0])


used = [False] * len(csegs)
loops, opens = [], []
for start in range(len(csegs)):
    if used[start]:
        continue
    used[start] = True
    a, b, _, _ = csegs[start]
    chain = [a, b]
    cur, incoming = b, ang(a, b)
    closed = False
    for _ in range(len(csegs) + 2):
        cands = [(si, o) for si, o in adj[cur] if not used[si]]
        if not cands:
            break
        # Straightest continuation only. Never prefer a segment that closes
        # the loop: at a T-junction that can be a shortcut across the
        # outline (verified on the sample: preferring "close" cut the
        # 580x30 rail down to 550 wide and orphaned 2 holes).
        si, other = min(
            cands,
            key=lambda c: abs((ang(cur, c[1]) - incoming + math.pi) % (2 * math.pi) - math.pi))
        used[si] = True
        incoming = ang(cur, other)
        cur = other
        if other == chain[0]:
            closed = True
            break
        chain.append(other)
    (loops if closed and len(chain) >= 3 else opens).append(chain)
print(f"closed loops: {len(loops)}, open chains: {len(opens)}")


def area(poly):
    return 0.5 * abs(sum(poly[i][0] * poly[(i + 1) % len(poly)][1]
                         - poly[(i + 1) % len(poly)][0] * poly[i][1]
                         for i in range(len(poly))))


def pip(pt, poly):
    x, y = pt
    inside = False
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        if (y1 > y) != (y2 > y):
            if x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                inside = not inside
    return inside


def bbox(poly):
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    return min(xs), max(xs), min(ys), max(ys)


areas = [area(l) for l in loops]
boxes = [bbox(l) for l in loops]
parent_of = []
for i, l in enumerate(loops):
    best, best_a = -1, None
    for j, m in enumerate(loops):
        if i == j or areas[j] <= areas[i]:
            continue
        if boxes[i][0] >= boxes[j][0] - 0.01 and boxes[i][1] <= boxes[j][1] + 0.01 \
                and boxes[i][2] >= boxes[j][2] - 0.01 and boxes[i][3] <= boxes[j][3] + 0.01 \
                and pip(l[0], m) and (best_a is None or areas[j] < best_a):
            best, best_a = j, areas[j]
    parent_of.append(best)
toplevel = [i for i, p in enumerate(parent_of) if p == -1]
print(f"top-level outlines (parts): {len(toplevel)}, "
      f"inner loops (cutouts): {len(loops) - len(toplevel)}")

# closed LWPOLYLINEs: part only if not inside any loop
lw_inside = sum(1 for pts, *_ in [(x[1],) for x in lws]
                if any(pip(pts[0], l) for l in loops))
print(f"closed LWPOLYLINEs inside another outline (details): {lw_inside} of {len(lws)}")

holes = Counter()
orphan_circles = 0
for c in circles:
    owners = [i for i in toplevel if pip((c[1], c[2]), loops[i])]
    if owners:
        holes[min(owners, key=lambda i: areas[i])] += 1
    else:
        orphan_circles += 1
print(f"circles as holes: {sum(holes.values())}, "
      f"circles as standalone round parts: {orphan_circles}")

print("\nPARTS (discovery order):")
for n, i in enumerate(toplevel, 1):
    bx0, bx1, by0, by1 = boxes[i]
    print(f"  Part {n}: {bx1 - bx0:.2f} x {by1 - by0:.2f} mm, "
          f"{len(loops[i])} outline pts, {holes.get(i, 0)} holes, "
          f"area {areas[i]:.0f} mm^2")
print("\nSize histogram:", dict(Counter(
    (round(boxes[i][1] - boxes[i][0]), round(boxes[i][3] - boxes[i][2]))
    for i in toplevel)))
print(f"\nTOTAL: {len(toplevel) + orphan_circles} parts, "
      f"{sum(holes.values())} circle holes")
