package com.qza.custom.knife;

import java.util.Arrays;

public final class KnifeMesh {
    public static final int STRIDE = 8;
    public static final int FIXED = 0;
    public static final int BLADE = 1;
    public static final int BACK = 2;
    public static final int GROUPS = 3;

    public static final int TEX_W = 512;
    public static final int TEX_H = 256;
    public static final int HALF = 128;

    public static final int BLADE_FRONT = 0;
    public static final int BLADE_BACK = 1;
    public static final int FINISHED = 2;
    public static final int GRIP = 3;
    public static final int METAL = 4;
    public static final int ACCENT = 5;
    static final int[][] REGIONS = {
            {0, 0, 256, 64},
            {0, 64, 256, 64},
            {256, 0, 128, 64},
            {256, 64, 128, 64},
            {384, 0, 64, 128},
            {448, 0, 64, 128},
    };

    private static final float ROOT_HALF = 0.70710678f;
    private static final float INSET = 1.5f;

    private final float[][] groups;
    private final float[] min;
    private final float[] max;

    private KnifeMesh(float[][] groups, float[] min, float[] max) {
        this.groups = groups;
        this.min = min;
        this.max = max;
    }

    public float[] group(int group) {
        return groups[group];
    }

    public float[] min() {
        return min;
    }

    public float[] max() {
        return max;
    }

    public static float modelX(float ox, float oy, float a, float b) {
        return (ox + (a + b) * ROOT_HALF) / 16f;
    }

    public static float modelY(float ox, float oy, float a, float b) {
        return 1f - (oy + (b - a) * ROOT_HALF) / 16f;
    }

    static float u(int region, float s) {
        int[] r = REGIONS[region];
        return (r[0] + INSET + clamp01(s) * (r[2] - 2f * INSET)) / TEX_W;
    }

    static float v(int region, float w) {
        int[] r = REGIONS[region];
        return (r[1] + INSET + clamp01(w) * (r[3] - 2f * INSET)) / TEX_H;
    }

    static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    static final class Keys {
        private final float[] t;
        private final float[] v;

        private Keys(float[] pairs) {
            t = new float[pairs.length / 2];
            v = new float[pairs.length / 2];
            for (int i = 0; i < t.length; i++) {
                t[i] = pairs[i * 2];
                v[i] = pairs[i * 2 + 1];
            }
        }

        static Keys of(float... pairs) {
            return new Keys(pairs.length == 1 ? new float[]{0f, pairs[0], 1f, pairs[0]} : pairs);
        }

        float at(float x) {
            if (x <= t[0]) {
                return v[0];
            }
            int last = t.length - 1;
            if (x >= t[last]) {
                return v[last];
            }
            int i = 0;
            while (x > t[i + 1]) {
                i++;
            }
            float f = (x - t[i]) / (t[i + 1] - t[i]);
            float m0 = slope(i);
            float m1 = slope(i + 1);
            float h = t[i + 1] - t[i];
            float f2 = f * f;
            float f3 = f2 * f;
            return (2f * f3 - 3f * f2 + 1f) * v[i] + (f3 - 2f * f2 + f) * h * m0
                    + (3f * f2 - 2f * f3) * v[i + 1] + (f3 - f2) * h * m1;
        }

        private float slope(int i) {
            if (i == 0 || i == t.length - 1) {
                return 0f;
            }
            float d0 = (v[i] - v[i - 1]) / (t[i] - t[i - 1]);
            float d1 = (v[i + 1] - v[i]) / (t[i + 1] - t[i]);
            return d0 * d1 <= 0f ? 0f : 2f / (1f / d0 + 1f / d1);
        }
    }

    static final class Curve {
        final float[] a;
        final float[] b;
        final float[] ta;
        final float[] tb;
        final float length;

        private Curve(float[] a, float[] b, float[] ta, float[] tb, float length) {
            this.a = a;
            this.b = b;
            this.ta = ta;
            this.tb = tb;
            this.length = length;
        }

        static Curve of(float[] points, boolean closed, boolean smooth, int count) {
            int n = points.length / 2;
            int dense = Math.max(count * 8, 64);
            float[] da = new float[dense + 1];
            float[] db = new float[dense + 1];
            int segments = closed ? n : n - 1;
            for (int k = 0; k <= dense; k++) {
                float g = (float) k / dense * segments;
                int seg = Math.min((int) g, segments - 1);
                float f = g - seg;
                int i1 = seg;
                int i2 = (seg + 1) % n;
                int i0 = closed ? (seg - 1 + n) % n : Math.max(seg - 1, 0);
                int i3 = closed ? (seg + 2) % n : Math.min(seg + 2, n - 1);
                if (smooth) {
                    da[k] = catmull(points[i0 * 2], points[i1 * 2], points[i2 * 2], points[i3 * 2], f);
                    db[k] = catmull(points[i0 * 2 + 1], points[i1 * 2 + 1], points[i2 * 2 + 1], points[i3 * 2 + 1], f);
                } else {
                    da[k] = points[i1 * 2] + (points[i2 * 2] - points[i1 * 2]) * f;
                    db[k] = points[i1 * 2 + 1] + (points[i2 * 2 + 1] - points[i1 * 2 + 1]) * f;
                }
            }
            float[] cum = new float[dense + 1];
            for (int k = 1; k <= dense; k++) {
                cum[k] = cum[k - 1] + (float) Math.hypot(da[k] - da[k - 1], db[k] - db[k - 1]);
            }
            float total = cum[dense];
            float[] a = new float[count + 1];
            float[] b = new float[count + 1];
            int k = 0;
            for (int i = 0; i <= count; i++) {
                float want = total * i / count;
                while (k < dense - 1 && cum[k + 1] < want) {
                    k++;
                }
                float span = cum[k + 1] - cum[k];
                float f = span <= 1.0e-6f ? 0f : (want - cum[k]) / span;
                a[i] = da[k] + (da[k + 1] - da[k]) * f;
                b[i] = db[k] + (db[k + 1] - db[k]) * f;
            }
            if (closed) {
                a[count] = a[0];
                b[count] = b[0];
            }
            float[] ta = new float[count + 1];
            float[] tb = new float[count + 1];
            for (int i = 0; i <= count; i++) {
                int p = i == 0 ? (closed ? count - 1 : 0) : i - 1;
                int q = i == count ? (closed ? 1 : count) : i + 1;
                float x = a[q] - a[p];
                float y = b[q] - b[p];
                float len = (float) Math.hypot(x, y);
                if (len < 1.0e-6f) {
                    x = 1f;
                    y = 0f;
                    len = 1f;
                }
                ta[i] = x / len;
                tb[i] = y / len;
            }
            return new Curve(a, b, ta, tb, total);
        }

        private static float catmull(float p0, float p1, float p2, float p3, float t) {
            float t2 = t * t;
            float t3 = t2 * t;
            return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                    + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
        }
    }

    static final class Blade {
        float[] spine;
        float[] edge;
        Keys thick = Keys.of(0f, 0.16f, 0.75f, 0.13f, 1f, 0.03f);
        float swedgeWidth = 0.06f;
        Keys swedge = Keys.of(1f);
        float grind = 0.42f;
        float edgeBevel = 0.9f;
        float hollow = 0.3f;
        float teethFrom;
        float teethTo;
        int teeth;
        float teethDepth;
        float serrFrom;
        float serrTo;
        int serrations;
        float serrDepth;
        float fullerFrom = 2f;
        float fullerTo;
        float fullerW0;
        float fullerW1;
        float fullerDepth;
        int stations = 72;
        int group = FIXED;
        float z;
        int frontRegion = BLADE_FRONT;
        int backRegion = BLADE_BACK;
        boolean doubleEdge;
    }

    static final class Sweep {
        float[] path;
        boolean closed;
        boolean smooth = true;
        Keys top = Keys.of(0.5f);
        Keys bottom;
        Keys depth = Keys.of(0.4f);
        Keys shift;
        float exponent = 4f;
        float z;
        int segments = 16;
        int stations = 24;
        int region = GRIP;
        int group = FIXED;
        boolean caps = true;
        float uFrom;
        float uTo = 1f;
    }

    static final class Builder {
        private final float ox;
        private final float oy;
        private final Grow[] out = new Grow[GROUPS];
        private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        Builder(float ox, float oy) {
            this.ox = ox;
            this.oy = oy;
            for (int i = 0; i < GROUPS; i++) {
                out[i] = new Grow();
            }
        }

        KnifeMesh build() {
            float[][] groups = new float[GROUPS][];
            for (int i = 0; i < GROUPS; i++) {
                groups[i] = out[i].trim();
            }
            return new KnifeMesh(groups, min, max);
        }

        private void vertex(int group, float a, float b, float z, float na, float nb, float nz, float u, float v) {
            float x = modelX(ox, oy, a, b);
            float y = modelY(ox, oy, a, b);
            float mz = 0.5f + z / 16f;
            float nx = (na + nb) * ROOT_HALF;
            float ny = (na - nb) * ROOT_HALF;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-6f) {
                nx = 0f;
                ny = 0f;
                nz = 1f;
                len = 1f;
            }
            out[group].add(x, y, mz, nx / len, ny / len, nz / len, u, v);
            min[0] = Math.min(min[0], x);
            min[1] = Math.min(min[1], y);
            min[2] = Math.min(min[2], mz);
            max[0] = Math.max(max[0], x);
            max[1] = Math.max(max[1], y);
            max[2] = Math.max(max[2], mz);
        }

        private void quad(int group, float[][] p, float[][] n, float[][] uv) {
            for (int i = 0; i < 4; i++) {
                vertex(group, p[i][0], p[i][1], p[i][2], n[i][0], n[i][1], n[i][2], uv[i][0], uv[i][1]);
            }
        }

        void blade(Blade spec) {
            int ns = spec.stations;
            Curve spine = Curve.of(spec.spine, false, true, ns);
            Curve edge = Curve.of(spec.edge, false, true, ns);
            float[] sa = new float[ns + 1];
            float[] sb = new float[ns + 1];
            float[] ea = new float[ns + 1];
            float[] eb = new float[ns + 1];
            for (int i = 0; i <= ns; i++) {
                float s = (float) i / ns;
                sa[i] = spine.a[i];
                sb[i] = spine.b[i];
                ea[i] = edge.a[i];
                eb[i] = edge.b[i];
                float da = sa[i] - ea[i];
                float db = sb[i] - eb[i];
                float len = (float) Math.hypot(da, db);
                if (len > 1.0e-5f) {
                    da /= len;
                    db /= len;
                }
                float tooth = wave(s, spec.teethFrom, spec.teethTo, spec.teeth) * spec.teethDepth;
                sa[i] += da * tooth;
                sb[i] += db * tooth;
                float cut = scallop(s, spec.serrFrom, spec.serrTo, spec.serrations) * spec.serrDepth;
                ea[i] += da * cut;
                eb[i] += db * cut;
            }
            float sw = spec.swedgeWidth;
            float[] rails = spec.doubleEdge
                    ? new float[]{0f, 0.1f, 0.5f - 0.01f, 0.5f + 0.01f, 0.9f, 1f}
                    : new float[]{0f, sw, spec.grind, spec.edgeBevel, 1f};
            int[] subdiv = spec.doubleEdge ? new int[]{1, 4, 1, 4, 1} : new int[]{1, 2, 5, 1};
            for (int face = 0; face < 2; face++) {
                float sign = face == 0 ? 1f : -1f;
                int region = face == 0 ? spec.frontRegion : spec.backRegion;
                for (int band = 0; band < subdiv.length; band++) {
                    int nw = subdiv[band];
                    float[][][] pos = new float[ns + 1][nw + 1][];
                    for (int i = 0; i <= ns; i++) {
                        float s = (float) i / ns;
                        for (int j = 0; j <= nw; j++) {
                            float w = rails[band] + (rails[band + 1] - rails[band]) * j / nw;
                            float h = height(spec, s, w, band);
                            pos[i][j] = new float[]{sa[i] + (ea[i] - sa[i]) * w, sb[i] + (eb[i] - sb[i]) * w,
                                    spec.z + sign * h, w};
                        }
                    }
                    for (int i = 0; i < ns; i++) {
                        for (int j = 0; j < nw; j++) {
                            float[][] p = {pos[i][j], pos[i + 1][j], pos[i + 1][j + 1], pos[i][j + 1]};
                            float[][] n = new float[4][];
                            int[][] at = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                            float[][] uv = new float[4][];
                            for (int k = 0; k < 4; k++) {
                                n[k] = gridNormal(pos, at[k][0], at[k][1], ns, nw, sign);
                                uv[k] = new float[]{u(region, (float) at[k][0] / ns), v(region, pos[at[k][0]][at[k][1]][3])};
                            }
                            quad(spec.group, p, n, uv);
                        }
                    }
                }
            }
            strip(spec, sa, sb, ea, eb, ns, true);
            strip(spec, sa, sb, ea, eb, ns, false);
            baseCap(spec, sa, sb, ea, eb, rails);
        }

        private float height(Blade spec, float s, float w, int band) {
            float t = spec.thick.at(s);
            float edgeT = Math.max(t * 0.16f, 0.012f);
            float h;
            if (spec.doubleEdge) {
                float x = Math.abs(w - 0.5f) * 2f;
                if (x < 0.02f) {
                    h = t;
                } else if (x < 0.8f) {
                    float f = (x - 0.02f) / 0.78f;
                    h = edgeT + (t - edgeT) * (float) Math.pow(1f - f, 1f + spec.hollow * 1.5f);
                } else {
                    float f = (x - 0.8f) / 0.2f;
                    h = edgeT + (0.004f - edgeT) * f;
                }
                return h;
            }
            if (w <= spec.swedgeWidth) {
                float k = spec.swedge.at(s);
                h = t * (k + (1f - k) * (w / spec.swedgeWidth));
            } else if (w <= spec.grind) {
                h = t;
            } else if (w <= spec.edgeBevel) {
                float f = (w - spec.grind) / (spec.edgeBevel - spec.grind);
                h = edgeT + (t - edgeT) * (float) Math.pow(1f - f, 1f + spec.hollow * 1.5f);
            } else {
                float f = (w - spec.edgeBevel) / (1f - spec.edgeBevel);
                h = edgeT + (0.004f - edgeT) * f;
            }
            if (s >= spec.fullerFrom && s <= spec.fullerTo && w > spec.fullerW0 && w < spec.fullerW1) {
                float across = (w - spec.fullerW0) / (spec.fullerW1 - spec.fullerW0);
                float along = Math.min((s - spec.fullerFrom) / 0.06f, (spec.fullerTo - s) / 0.06f);
                float dip = (float) Math.sin(across * Math.PI) * Math.min(1f, along);
                h -= spec.fullerDepth * t * dip;
            }
            return h;
        }

        private static float wave(float s, float from, float to, int count) {
            if (count <= 0 || s < from || s > to) {
                return 0f;
            }
            float x = (s - from) / (to - from) * count;
            float f = x - (float) Math.floor(x);
            return f < 0.7f ? f / 0.7f : (1f - f) / 0.3f;
        }

        private static float scallop(float s, float from, float to, int count) {
            if (count <= 0 || s < from || s > to) {
                return 0f;
            }
            float x = (s - from) / (to - from) * count;
            float f = x - (float) Math.floor(x);
            return (float) Math.sin(f * Math.PI);
        }

        private static float[] gridNormal(float[][][] pos, int i, int j, int ns, int nw, float sign) {
            int i0 = Math.max(i - 1, 0);
            int i1 = Math.min(i + 1, ns);
            int j0 = Math.max(j - 1, 0);
            int j1 = Math.min(j + 1, nw);
            float[] ds = sub(pos[i1][j], pos[i0][j]);
            float[] dw = sub(pos[i][j1], pos[i][j0]);
            if (length(ds) < 1.0e-5f) {
                int k = i == 0 ? 1 : ns - 1;
                ds = sub(pos[Math.min(k + 1, ns)][j], pos[Math.max(k - 1, 0)][j]);
            }
            if (length(dw) < 1.0e-5f) {
                int k = i == 0 ? Math.min(1, ns) : Math.max(ns - 1, 0);
                dw = sub(pos[k][j1], pos[k][j0]);
            }
            float[] n = cross(ds, dw);
            if (n[2] * sign < 0f) {
                n[0] = -n[0];
                n[1] = -n[1];
                n[2] = -n[2];
            }
            if (length(n) < 1.0e-6f) {
                return new float[]{0f, 0f, sign};
            }
            return n;
        }

        private void strip(Blade spec, float[] sa, float[] sb, float[] ea, float[] eb, int ns, boolean spineSide) {
            float w = spineSide ? 0f : 1f;
            for (int i = 0; i < ns; i++) {
                float[][] p = new float[4][];
                float[][] n = new float[4][];
                float[][] uv = new float[4][];
                int[] idx = {i, i + 1, i + 1, i};
                float[] zs = new float[4];
                for (int k = 0; k < 4; k++) {
                    int at = idx[k];
                    float s = (float) at / ns;
                    float h = height(spec, s, w, 0);
                    zs[k] = spec.z + (k < 2 ? h : -h);
                    float a = spineSide ? sa[at] : ea[at];
                    float b = spineSide ? sb[at] : eb[at];
                    p[k] = new float[]{a, b, zs[k]};
                    float oa = spineSide ? sa[at] - ea[at] : ea[at] - sa[at];
                    float ob = spineSide ? sb[at] - eb[at] : eb[at] - sb[at];
                    int prev = Math.max(at - 1, 0);
                    int next = Math.min(at + 1, ns);
                    float ta = (spineSide ? sa[next] - sa[prev] : ea[next] - ea[prev]);
                    float tb = (spineSide ? sb[next] - sb[prev] : eb[next] - eb[prev]);
                    float na = -tb;
                    float nb = ta;
                    if (na * oa + nb * ob < 0f) {
                        na = -na;
                        nb = -nb;
                    }
                    float len = (float) Math.hypot(na, nb);
                    if (len < 1.0e-6f) {
                        na = oa;
                        nb = ob;
                        len = Math.max((float) Math.hypot(na, nb), 1.0e-6f);
                    }
                    na /= len;
                    nb /= len;
                    float tilt = k < 2 ? 0.35f : -0.35f;
                    n[k] = new float[]{na, nb, tilt};
                    uv[k] = new float[]{u(spec.frontRegion, s), v(spec.frontRegion, w)};
                }
                quad(spec.group, p, n, uv);
            }
        }

        private void baseCap(Blade spec, float[] sa, float[] sb, float[] ea, float[] eb, float[] rails) {
            float na = sa[0] - sa[1] + ea[0] - ea[1];
            float nb = sb[0] - sb[1] + eb[0] - eb[1];
            for (int r = 0; r < rails.length - 1; r++) {
                float w0 = rails[r];
                float w1 = rails[r + 1];
                float h0 = height(spec, 0f, w0, 0);
                float h1 = height(spec, 0f, w1, 0);
                float a0 = sa[0] + (ea[0] - sa[0]) * w0;
                float b0 = sb[0] + (eb[0] - sb[0]) * w0;
                float a1 = sa[0] + (ea[0] - sa[0]) * w1;
                float b1 = sb[0] + (eb[0] - sb[0]) * w1;
                float[][] p = {{a0, b0, spec.z + h0}, {a1, b1, spec.z + h1}, {a1, b1, spec.z - h1}, {a0, b0, spec.z - h0}};
                float[] nn = {na, nb, 0f};
                float[][] n = {nn, nn, nn, nn};
                float[] t = {u(spec.frontRegion, 0f), v(spec.frontRegion, 0.5f)};
                float[][] uv = {t, t, t, t};
                quad(spec.group, p, n, uv);
            }
        }

        void sweep(Sweep spec) {
            int ns = spec.stations;
            int m = spec.segments;
            Curve path = Curve.of(spec.path, spec.closed, spec.smooth, ns);
            Keys bottom = spec.bottom == null ? spec.top : spec.bottom;
            float[][][] ring = new float[ns + 1][m + 1][];
            float[][] centre = new float[ns + 1][];
            for (int i = 0; i <= ns; i++) {
                float t = (float) i / ns;
                float ca = path.a[i];
                float cb = path.b[i];
                float pa = -path.tb[i];
                float pb = path.ta[i];
                float up = spec.top.at(t);
                float down = bottom.at(t);
                float depth = spec.depth.at(t);
                float shift = spec.shift == null ? 0f : spec.shift.at(t);
                centre[i] = new float[]{ca + pa * ((down - up) * 0.5f + shift), cb + pb * ((down - up) * 0.5f + shift), spec.z};
                for (int k = 0; k <= m; k++) {
                    double phi = Math.PI * 2.0 * k / m;
                    float cx = superellipse(Math.cos(phi), spec.exponent);
                    float cy = superellipse(Math.sin(phi), spec.exponent);
                    float x = (cx > 0f ? cx * down : cx * up) + shift;
                    float y = cy * depth;
                    ring[i][k] = new float[]{ca + pa * x, cb + pb * x, spec.z + y, (float) k / m};
                }
            }
            for (int i = 0; i < ns; i++) {
                for (int k = 0; k < m; k++) {
                    int[][] at = {{i, k}, {i, k + 1}, {i + 1, k + 1}, {i + 1, k}};
                    float[][] p = new float[4][];
                    float[][] n = new float[4][];
                    float[][] uv = new float[4][];
                    for (int q = 0; q < 4; q++) {
                        int ii = at[q][0];
                        int kk = at[q][1];
                        p[q] = ring[ii][kk];
                        n[q] = ringNormal(ring, centre, ii, kk, ns, m, spec.closed);
                        float along = spec.uFrom + (spec.uTo - spec.uFrom) * ii / ns;
                        uv[q] = new float[]{u(spec.region, along), v(spec.region, (float) kk / m)};
                    }
                    quad(spec.group, p, n, uv);
                }
            }
            if (!spec.closed && spec.caps) {
                cap(spec, ring, centre[0], -path.ta[0], -path.tb[0], 0, m);
                cap(spec, ring, centre[ns], path.ta[ns], path.tb[ns], ns, m);
            }
        }

        private void cap(Sweep spec, float[][][] ring, float[] c, float na, float nb, int i, int m) {
            float[] nn = {na, nb, 0f};
            float along = i == 0 ? spec.uFrom : spec.uTo;
            float[] t = {u(spec.region, along), v(spec.region, 0.5f)};
            for (int k = 0; k < m; k++) {
                float[][] p = {c, ring[i][k], ring[i][k + 1], ring[i][k + 1]};
                float[][] n = {nn, nn, nn, nn};
                float[][] uv = {t, {u(spec.region, along), v(spec.region, (float) k / m)},
                        {u(spec.region, along), v(spec.region, (float) (k + 1) / m)},
                        {u(spec.region, along), v(spec.region, (float) (k + 1) / m)}};
                quad(spec.group, p, n, uv);
            }
        }

        private static float[] ringNormal(float[][][] ring, float[][] centre, int i, int k, int ns, int m, boolean closed) {
            int km = (k - 1 + m) % m;
            int kp = (k + 1) % m;
            int kk = k % m;
            int i0;
            int i1;
            if (closed) {
                i0 = i == 0 ? ns - 1 : i - 1;
                i1 = i == ns ? 1 : i + 1;
            } else {
                i0 = Math.max(i - 1, 0);
                i1 = Math.min(i + 1, ns);
            }
            float[] around = sub(ring[i][kp], ring[i][km]);
            float[] along = sub(ring[i1][kk], ring[i0][kk]);
            float[] n = cross(along, around);
            float[] out = sub(ring[i][kk], centre[i]);
            if (dot(n, out) < 0f) {
                n[0] = -n[0];
                n[1] = -n[1];
                n[2] = -n[2];
            }
            if (length(n) < 1.0e-6f) {
                return out;
            }
            return n;
        }

        private static float superellipse(double c, float exponent) {
            double mag = Math.pow(Math.abs(c), 2.0 / exponent);
            return (float) (c < 0 ? -mag : mag);
        }
    }

    static float[] sub(float[] p, float[] q) {
        return new float[]{p[0] - q[0], p[1] - q[1], p[2] - q[2]};
    }

    static float[] cross(float[] p, float[] q) {
        return new float[]{p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0]};
    }

    static float dot(float[] p, float[] q) {
        return p[0] * q[0] + p[1] * q[1] + p[2] * q[2];
    }

    static float length(float[] p) {
        return (float) Math.sqrt(dot(p, p));
    }

    private static final class Grow {
        private float[] data = new float[STRIDE * 256];
        private int size;

        void add(float... values) {
            if (size + values.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
            }
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        float[] trim() {
            return Arrays.copyOf(data, size);
        }
    }
}
