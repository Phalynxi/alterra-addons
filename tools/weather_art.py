"""Generates Alterra Weather's own assets into weather/src/main/resources/assets/alterra_weather:
 - particles: dust (8x8 specks) and puff (16x16 clumpy pixel clouds, tinted and made translucent in game)
 - sounds: three seamless stereo wind loops (synthesised: shaped noise, gusts, a faint whistle), as Ogg Vorbis
Needs numpy, Pillow and ffmpeg. Run: python3 tools/weather_art.py   (python3 tools/weather_art.py particles = only the particles)"""
import json, math, random, subprocess, sys, tempfile, wave
from pathlib import Path
import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "weather/src/main/resources/assets/alterra_weather"
NS = "alterra_weather"

# ---------------------------------------------------------------- particles
def dust():
    G, g = (240, 240, 240, 255), (200, 200, 200, 190)
    frames = [(["........", "........", "........", "...a....", "........", "........", "........", "........"], {"a": G}),
              (["........", "........", "........", "...ab...", "........", "........", "........", "........"], {"a": G, "b": g}),
              (["........", "........", "...b....", "..bab...", "...b....", "........", "........", "........"], {"a": G, "b": g}),
              (["........", "........", "........", "..ab....", "...a....", "........", "........", "........"], {"a": G, "b": g})]
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    names = []
    for i, (rows, pal) in enumerate(frames):
        im = Image.new("RGBA", (8, 8), (0, 0, 0, 0)); px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal: px[x, y] = pal[ch]
        im.save(d / f"dust_{i}.png"); names.append(f"{NS}:dust_{i}")
    (OUT / "particles").mkdir(parents=True, exist_ok=True)
    (OUT / "particles/dust.json").write_text(json.dumps({"textures": names}, indent=2) + "\n")

def mote():
    """Pollen and ash, tinted in game (yellow / grey): two soft glowing pollen grains and two ragged ash flakes. Same size as the dust, a little bolder."""
    W, S, M = (255, 255, 255, 255), (226, 226, 226, 255), (150, 150, 150, 255)
    h = lambda a: (255, 255, 255, a)
    frames = [(["........", "........", "...b....", "..bab...", "...b....", "........", "........", "........"], {"a": W, "b": h(110)}),
              (["........", "........", "........", "...ab...", "...bb...", "........", "........", "........"], {"a": W, "b": h(150)}),
              (["........", "........", "..sam...", "..ass...", "...sm...", "........", "........", "........"], {"a": W, "s": S, "m": M}),
              (["........", "........", "....a...", "...sa...", "..sm....", "..m.....", "........", "........"], {"a": W, "s": S, "m": M})]
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    names = []
    for i, (rows, pal) in enumerate(frames):
        im = Image.new("RGBA", (8, 8), (0, 0, 0, 0)); px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal: px[x, y] = pal[ch]
        im.save(d / f"mote_{i}.png"); names.append(f"{NS}:mote_{i}")
    (OUT / "particles/mote.json").write_text(json.dumps({"textures": names}, indent=2) + "\n")

def puff():
    """Clumpy cloud shapes in the style of the cloud blocks: a union of discs, three flat tones (lit top, mid, shaded underside)."""
    d = OUT / "textures/particle"; names = []
    for v in range(4):
        r = random.Random(100 + v)
        discs = [(8 + r.uniform(-3.5, 3.5), 9 + r.uniform(-2, 2), r.uniform(2.6, 4.4)) for _ in range(4 + v % 2)]
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
        for y in range(16):
            for x in range(16):
                best = None
                for cx, cy, rad in discs:
                    dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    if dd <= rad and (best is None or dd / rad < best[0]): best = (dd / rad, cx, cy, rad)
                if best:
                    _, cx, cy, rad = best
                    lit = (y + 0.5 - cy) / rad
                    tone = 255 if lit < -0.25 else 225 if lit < 0.35 else 188
                    px[x, y] = (tone, tone, tone, 255)
        im.save(d / f"puff_{v}.png"); names.append(f"{NS}:puff_{v}")
    (OUT / "particles/puff.json").write_text(json.dumps({"textures": names}, indent=2) + "\n")

# ---------------------------------------------------------------- wind
SR, SECONDS = 32000, 20
N = SR * SECONDS

def water():
    """Ripples (a thin ring, soft at its edges) and foam (soft white patches with small bubbles), for the water surface."""
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    im = Image.new("RGBA", (32, 32)); px = im.load()
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5)
            a = max(0.0, 1 - abs(r - 13.0) / 1.9)        # one ring, close to the edge so it can grow
            if a > 0: px[x, y] = (236, 246, 255, int(a * 205))
    im.save(d / "ripple.png")
    for i in range(3):
        rng = random.Random(40 + i); im = Image.new("RGBA", (32, 32)); px = im.load()
        blobs = [(rng.uniform(7, 25), rng.uniform(7, 25), rng.uniform(3.5, 8)) for _ in range(7)]
        for y in range(32):
            for x in range(32):
                v = 0.0
                for bx, by, br in blobs:
                    v = max(v, 1 - math.hypot(x - bx, y - by) / br)
                edge = max(0.0, 1 - math.hypot(x - 15.5, y - 15.5) / 15.5)
                a = max(0.0, min(1.0, v * 1.6)) * edge ** 0.6
                if a > 0.04: px[x, y] = (250, 253, 255, int(a * 230))
        for _ in range(9):   # small bubbles
            bx, by = rng.randrange(6, 26), rng.randrange(6, 26)
            if px[bx, by][3] > 60: px[bx, by] = (255, 255, 255, 255)
        im.save(d / f"foam_{i}.png")
    import json as _j
    pd = OUT / "particles"; pd.mkdir(parents=True, exist_ok=True)
    (pd / "ripple.json").write_text(_j.dumps({"textures": [f"{NS}:ripple"]}))
    (pd / "foam.json").write_text(_j.dumps({"textures": [f"{NS}:foam_{i}" for i in range(3)]}))

def spray():
    """Droplets and foam clumps for splashes: droplets are round white drops (three 8x8 frames); clumps are lumpy white patches of foam (four 16x16 frames) with a pale blue-grey shaded underside, like the foam at the foot of a waterfall."""
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    drops = [["........", "..aaaa..", ".abbbba.", ".abwbba.", ".abbbba.", "..aaaa..", "........", "........"],
             ["........", "..aaa...", ".abbba..", ".abwba..", ".abbba..", "..aaa...", "........", "........"],
             ["........", "...aa...", "..abba..", ".abwbba.", ".abbbba.", "..aaaa..", "........", "........"]]
    pal = {"a": (196, 222, 244, 235), "b": (244, 250, 255, 255), "w": (255, 255, 255, 255)}
    for i, rows in enumerate(drops):
        im = Image.new("RGBA", (8, 8), (0, 0, 0, 0)); px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal: px[x, y] = pal[ch]
        im.save(d / f"spray_{i}.png")
    for i in range(4):   # foam clumps: overlapping blobs, white on top, pale blue-grey underneath, a few holes
        rng = random.Random(40 + i); im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
        blobs = [(rng.uniform(5, 11), rng.uniform(5, 11), rng.uniform(2.2, 3.6)) for _ in range(5 + i % 2)]
        for y in range(16):
            for x in range(16):
                inside = [bl for bl in blobs if (x + 0.5 - bl[0]) ** 2 + (y + 0.5 - bl[1]) ** 2 <= bl[2] ** 2]
                if not inside: continue
                top = any((x + 0.5 - bl[0]) ** 2 + (y + 0.5 - bl[1] - 0.9) ** 2 <= (bl[2] - 0.6) ** 2 for bl in inside)
                px[x, y] = (250, 252, 255, 255) if top else (190, 214, 236, 235)
        for _ in range(3):
            hx, hy = rng.randrange(4, 12), rng.randrange(4, 12)
            if px[hx, hy][3] > 0 and rng.random() < 0.6: px[hx, hy] = (210, 228, 244, 200)
        im.save(d / f"clump_{i}.png")
    pd = OUT / "particles"; pd.mkdir(parents=True, exist_ok=True)
    (pd / "spray.json").write_text(json.dumps({"textures": [f"{NS}:spray_{i}" for i in range(3)]}))
    (pd / "clump.json").write_text(json.dumps({"textures": [f"{NS}:clump_{i}" for i in range(4)]}))

def caustics():
    """Caustics and foam textures, both repeating. Caustics: light from a rippled surface, worked out like the real thing: a height field bends the rays and the light piles up where they bunch (1 / the area they cover), which gives the bright net of thin lines. 128x128, white and orange (lava)."""
    import numpy as np
    d = OUT / "textures/environment"; d.mkdir(parents=True, exist_ok=True)
    N_ = 256; rng = np.random.default_rng(11)
    G = 5   # cells across one tile, jittered, wrapping at the edges
    pts = [((i + rng.uniform(0.15, 0.85)) / G * N_, (j + rng.uniform(0.15, 0.85)) / G * N_) for i in range(G) for j in range(G)]
    yy, xx = np.mgrid[0:N_, 0:N_].astype(float)
    ds = []
    for (px_, py_) in pts:
        dx = np.minimum(abs(xx - px_), N_ - abs(xx - px_)); dy = np.minimum(abs(yy - py_), N_ - abs(yy - py_))
        ds.append(np.hypot(dx, dy))
    ds = np.sort(np.stack(ds), axis=0)
    edge = (ds[1] - ds[0]) / (N_ / G)          # 0 on the line between two cells, grows into a cell
    line = np.clip(1 - edge / 0.075, 0, 1) ** 1.4   # thin rounded cell borders
    glow = np.clip(1 - edge / 0.22, 0, 1) ** 2 * 0.28   # a faint soft light along them
    light = np.clip(line + glow, 0, 1)
    for name, col in (("caustics", (215, 242, 255)), ("lava_glow", (255, 150, 50))):
        im = Image.new("RGBA", (N_, N_)); px = im.load()
        for j in range(N_):
            for i in range(N_):
                px[i, j] = col + (int(light[j, i] * 255),)
        im.save(d / f"{name}.png")
    # foam: a crisp white line. The texture is a ramp over the distance from the land: solid white for the first few texels, then a short soft edge, then clear.
    im = Image.new("RGBA", (64, 2)); px = im.load()
    ramp = [178] * 4 + [172, 160, 142, 118, 92, 68, 46, 28, 14, 5]   # about 70 % white next to the land, thinning out over the next half block
    for j in range(2):
        for i in range(64):
            px[i, j] = (246, 252, 255, ramp[i] if i < len(ramp) else 0)
    im.save(d / "foam_strip.png")

def band(rng, lo, hi, edge=0.25):
    """Noise limited to lo..hi Hz. Made in the frequency domain, so it repeats exactly after SECONDS (a seamless loop)."""
    spec = rng.normal(size=N // 2 + 1) + 1j * rng.normal(size=N // 2 + 1)
    f = np.fft.rfftfreq(N, 1 / SR)
    m = np.zeros_like(f); w = (hi - lo) * edge
    m[(f >= lo) & (f <= hi)] = 1
    up = (f > lo - w) & (f < lo); m[up] = 0.5 - 0.5 * np.cos(np.pi * (f[up] - (lo - w)) / w)
    dn = (f > hi) & (f < hi + w); m[dn] = 0.5 + 0.5 * np.cos(np.pi * (f[dn] - hi) / w)
    x = np.fft.irfft(spec * m, N)
    return x / (np.std(x) + 1e-9)

def lfo(cycles, phase=0.0):
    """Slow wave with a whole number of cycles over the loop (so it also repeats exactly)."""
    return np.sin(2 * np.pi * cycles * np.arange(N) / N + phase)

def wind(seed, layers, gust_cycles, gust_depth):
    out = []
    for ch in range(2):   # two different noises: a wide stereo wind
        rng = np.random.default_rng(seed * 10 + ch)
        x = np.zeros(N)
        for lo, hi, amp, cyc, depth in layers:
            env = 1 + depth * lfo(cyc, rng.uniform(0, 6.28))
            x += band(rng, lo, hi) * amp * np.clip(env, 0, None)
        gust = 1 + gust_depth * lfo(gust_cycles, rng.uniform(0, 6.28) if ch else 0.0) * lfo(gust_cycles * 2 + 1, 1.0 + ch * 0.4)
        out.append(x * np.clip(gust, 0.2, None))
    s = np.stack(out, 1); s *= 0.8 / np.abs(s).max()
    return s

LOOPS = {   # name: (seed, layers (lo Hz, hi Hz, amp, lfo cycles, lfo depth), gust cycles, gust depth): the two winds made of filtered noise
    "wind_dust": (1, [(40, 220, 0.9, 3, 0.4), (220, 900, 0.8, 5, 0.5), (900, 2200, 0.35, 8, 0.6), (2800, 7000, 0.28, 13, 0.7)], 4, 0.5),
    "wind_snow": (2, [(120, 600, 0.6, 4, 0.4), (500, 1800, 0.8, 6, 0.5), (700, 780, 0.28, 7, 0.9), (3000, 9000, 0.22, 11, 0.6)], 5, 0.55),
}

def _tone(freq, phase=0.0):
    """A sine that repeats exactly over the loop (its frequency is rounded to a whole number of cycles)."""
    cycles = max(1, round(freq * SECONDS))
    return np.sin(2 * np.pi * cycles * np.arange(N) / N + phase)

def _stereo(fn, seed):
    return np.stack([fn(np.random.default_rng(seed * 10 + ch), ch) for ch in range(2)], 1)

def _norm(s, peak=0.8): return s * (peak / np.abs(s).max())

def ash_rumble():
    """Ashfall: a deep, slow rumble with now and then a crackle of embers."""
    def one(rng, ch):
        x = band(rng, 28, 120) * 1.0 * (1 + 0.5 * lfo(3, rng.uniform(0, 6.28))) + band(rng, 120, 420) * 0.35 * (1 + 0.6 * lfo(5, rng.uniform(0, 6.28)))
        for _ in range(26):   # pops and crackles: a short burst of noise that dies away fast
            at = rng.integers(0, N - SR // 8); ln = int(SR * rng.uniform(0.012, 0.05))
            burst = rng.normal(size=ln) * np.exp(-np.arange(ln) / (ln / 5)) * rng.uniform(0.3, 1.0)
            x[at:at + ln] += burst * 1.4
        return x
    return _norm(_stereo(one, 11))

def fog_hush():
    """Fog: almost no wind, a soft low drone that swells and fades, and a faint far-away hush."""
    def one(rng, ch):
        x = (_tone(98, rng.uniform(0, 6)) * 0.5 + _tone(147.4, rng.uniform(0, 6)) * 0.3 + _tone(196.9 + ch * 0.5, rng.uniform(0, 6)) * 0.18) * (0.6 + 0.4 * lfo(2, rng.uniform(0, 6)))
        x += band(rng, 300, 1400) * 0.12 * (1 + 0.5 * lfo(3, rng.uniform(0, 6)))
        return x
    return _norm(_stereo(one, 12), 0.55)

def petal_rustle():
    """Petals: leaves rustling, in quick small gusts, and a few soft high notes of a breeze through blossom."""
    def one(rng, ch):
        env = np.abs(band(rng, 2, 14)); env = np.convolve(env, np.ones(int(SR * 0.03)) / (SR * 0.03), "same"); env = env / env.max()
        x = band(rng, 1800, 7000) * (env ** 1.6) * 0.9 + band(rng, 300, 1200) * 0.15 * (1 + 0.5 * lfo(4, rng.uniform(0, 6)))
        for f, a in ((1568, 0.05), (2093, 0.035), (2637, 0.03)):   # a little tinkle
            x += _tone(f + ch * 1.0, rng.uniform(0, 6)) * a * np.clip(lfo(int(rng.integers(2, 6)), rng.uniform(0, 6)), 0, None) ** 3
        return x
    return _norm(_stereo(one, 13), 0.6)

def pollen_hum():
    """Pollen: a warm buzz of insects, a few of them, each wobbling in pitch, drifting in and out."""
    def one(rng, ch):
        x = np.zeros(N)
        for k in range(5):
            f = rng.uniform(170, 330); vib = rng.integers(5, 17)
            carrier = np.sin(2 * np.pi * (round(f * SECONDS) * np.arange(N) / N + 0.004 * np.sin(2 * np.pi * vib * np.arange(N) / N)) + rng.uniform(0, 6))
            buzz = 0.6 + 0.4 * np.sin(2 * np.pi * round(rng.uniform(28, 46) * SECONDS) * np.arange(N) / N)
            near = np.clip(lfo(int(rng.integers(1, 5)), rng.uniform(0, 6.28)), 0, None)
            x += carrier * buzz * near * 0.3
        x += band(rng, 2500, 7000) * 0.04
        return x
    return _norm(_stereo(one, 14), 0.5)

def aurora_chime():
    """Aurora: a high, airy shimmer, long notes that rise and fade in turn, like glass far away."""
    def one(rng, ch):
        x = np.zeros(N)
        for f in (261.6, 392.0, 523.3, 659.3, 784.0, 987.8, 1174.7):
            x += _tone(f * (1 + rng.uniform(-0.003, 0.003)), rng.uniform(0, 6)) * np.clip(lfo(int(rng.integers(1, 4)), rng.uniform(0, 6.28)), 0, None) ** 2 * 0.2
        x += band(rng, 4000, 11000) * 0.03 * (1 + lfo(6, rng.uniform(0, 6)))
        return x
    return _norm(_stereo(one, 15), 0.45)

def rain_loop(heavy):
    """Rain: a steady hiss, and on top of it the patter: the same noise chopped by a fast random level, thousands of drops a second. A heavy rain is louder, with a roar under it."""
    def one(rng, ch):
        hiss = band(rng, 2500, 12000) * (0.30 if not heavy else 0.42)
        mid = band(rng, 700, 2600) * (0.22 if not heavy else 0.36) * (1 + 0.2 * lfo(4, rng.uniform(0, 6)))
        chop = np.abs(band(rng, 40, 420)); chop = chop / chop.max()
        patter = band(rng, 1800, 8000) * (chop ** 1.8) * (0.9 if not heavy else 1.2)
        low = band(rng, 90, 380) * (0.12 if not heavy else 0.4) * (1 + 0.3 * lfo(3, rng.uniform(0, 6)))
        if heavy: low += band(rng, 40, 130) * 0.25
        return hiss + mid + patter + low
    return _norm(_stereo(one, 16 + int(heavy)), 0.62 if heavy else 0.45)

def thunder(seed):
    """One roll of thunder, about twelve seconds: a hard crack with a sizzle, a deep boom, and rolls that surge and fade, pushed through a soft clip so it has weight."""
    ln = SR * 12; t = np.arange(ln) / SR
    chans = []
    for ch in range(2):
        rng = np.random.default_rng(seed * 10 + ch)
        x = band_n(rng, 300, 5000, ln) * np.exp(-t / 0.07) * 2.2                    # the crack
        x += band_n(rng, 1500, 9000, ln) * np.exp(-t / 0.5) * (0.3 + 0.7 * (rng.random(ln) < 0.01)) * 0.5   # the sizzle after it
        for k in range(5 + seed):   # the boom, then rolls: each a surge of low rumble with its own start and length
            at = 0.02 if k == 0 else rng.uniform(0.5, 8.0); att = 0.05 if k == 0 else rng.uniform(0.25, 0.9); dec = rng.uniform(1.4, 3.2) * (1.2 if k == 0 else 1.0)
            env = np.where(t < at, 0, np.minimum(1, (t - at) / att) * np.exp(-np.clip(t - at, 0, None) / dec)) * (1.0 if k == 0 else rng.uniform(0.35, 0.85))
            x += (band_n(rng, 28, 160, ln) * 1.4 + band_n(rng, 160, 600, ln) * 0.7) * env
        x = np.tanh(x * 1.6)
        chans.append(x * np.minimum(1, t / 0.004) * np.minimum(1, (ln / SR - t) / 1.5))
    s = np.stack(chans, 1); return s * (0.95 / np.abs(s).max())

def band_n(rng, lo, hi, n):
    spec = rng.normal(size=n // 2 + 1) + 1j * rng.normal(size=n // 2 + 1)
    f = np.fft.rfftfreq(n, 1 / SR); m = ((f >= lo) & (f <= hi)).astype(float)
    x = np.fft.irfft(spec * m, n); return x / (np.std(x) + 1e-9)

TONES = {"rain_light": lambda: rain_loop(False), "rain_heavy": lambda: rain_loop(True), "ash_rumble": ash_rumble, "fog_hush": fog_hush, "petal_rustle": petal_rustle, "pollen_hum": pollen_hum, "aurora_chime": aurora_chime}

def encode(samples, path):
    pcm = (samples * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / "x.wav"
        w = wave.open(str(wav), "wb"); w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes()); w.close()
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "vorbis", "-strict", "-2", "-b:a", "96k", str(path)], check=True)

def sounds():
    d = OUT / "sounds"; d.mkdir(parents=True, exist_ok=True); js = {}
    for name, (seed, layers, gc, gd) in LOOPS.items():
        encode(wind(seed, layers, gc, gd), d / f"{name}.ogg")
        js[name] = {"sounds": [{"name": f"{NS}:{name}", "stream": True}], "subtitle": f"subtitles.{NS}.{name}"}
    for name, make in TONES.items():
        encode(make(), d / f"{name}.ogg")
        js[name] = {"sounds": [{"name": f"{NS}:{name}", "stream": True}], "subtitle": f"subtitles.{NS}.{name}"}
    (OUT / "sounds.json").write_text(json.dumps(js, indent=2) + "\n")
    (OUT / "lang").mkdir(parents=True, exist_ok=True)
    (OUT / "lang/en_us.json").write_text(json.dumps({f"subtitles.{NS}.wind_dust": "Sandstorm howls", f"subtitles.{NS}.wind_snow": "Blizzard howls", f"subtitles.{NS}.ash_rumble": "Ash rumbles and crackles", f"subtitles.{NS}.fog_hush": "Fog hushes", f"subtitles.{NS}.petal_rustle": "Petals rustle", f"subtitles.{NS}.pollen_hum": "Insects hum", f"subtitles.{NS}.aurora_chime": "Aurora shimmers", f"subtitles.{NS}.rain_light": "Rain falls", f"subtitles.{NS}.rain_heavy": "Rain pours", f"block.{NS}.drain": "Drain", f"block.{NS}.slush": "Slush"}, indent=2) + "\n")

# ---------------------------------------------------------------- falling rain
def rain():
    """Alterra's own falling rain (replaces vanilla's sheets in StormRain): thin pale streaks, mostly see-through, a brighter head and a tail
    fading in clean steps; 64x256 like vanilla's, tiling top to bottom. No noise: each streak is a 1-pixel line."""
    w, h = 64, 256
    px = np.zeros((h, w, 4), dtype=np.uint8)
    rng = random.Random(7)
    cols = list(range(0, w, 2)); rng.shuffle(cols)
    for x in cols[:30]:
        top = rng.randrange(h); length = rng.choice([10, 14, 18, 22, 26])
        head = rng.choice([150, 170, 190])
        for i in range(length):
            y = (top + i) % h
            k = i / (length - 1)          # 0 at the tail, 1 at the head (the drop falls downwards: the head is the lowest pixel)
            a = head if k > 0.85 else int(head * (0.62 if k > 0.55 else 0.38 if k > 0.25 else 0.2))
            px[y, x] = (214, 226, 240, a)
    d = OUT / "textures/environment"; d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(px, "RGBA").save(d / "rain.png")

# ---------------------------------------------------------------- footprints
def prints():
    """Boot prints (16x16, left and right), white with a soft edge: tinted dark blue-grey on snow, brown on sand and mud in game.
    A forefoot, a small gap, a heel; the right one is the left one mirrored. Hand-placed pixels, no noise."""
    def make(mirror):
        im = np.zeros((16, 16, 4), dtype=np.uint8)
        fore = {2: (5, 10), 3: (4, 11), 4: (4, 11), 5: (4, 10), 6: (5, 10), 7: (5, 10), 8: (6, 10)}   # row: (first col, last col +1): wider on the outside, an arch on the inside
        heel = {10: (5, 10), 11: (5, 10), 12: (5, 9), 13: (6, 9)}
        for rows in (fore, heel):
            for y, (a, b) in rows.items():
                for x in range(a, b):
                    edge = x in (a, b - 1) or y in (min(rows), max(rows))
                    im[y, x] = (255, 255, 255, 150 if edge else 235)
        if mirror: im = im[:, ::-1].copy()
        return Image.fromarray(im, "RGBA")
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    names = []
    for i, m in enumerate((False, True)):
        make(m).save(d / f"print_{i}.png"); names.append(f"{NS}:print_{i}")
    p = OUT / "particles"; p.mkdir(parents=True, exist_ok=True)
    (p / "print.json").write_text(json.dumps({"textures": names}, indent=2))

# ---------------------------------------------------------------- hail
def hail():
    """A hailstone: a small round ice ball (8x8), white with a pale blue shade on its lower side and a bright speck on top. Two sizes' worth in one picture
    (the particle scales it). Hand-placed, no noise."""
    rows = ["..aaaa..", ".abbbba.", "abccbbba", "abcbbbda", "abbbbdda", "abbbddda", ".adddda.", "..aaaa.."]   # a rim, b ice, c bright, d shade
    pal = {"a": (196, 214, 232, 255), "b": (232, 242, 250, 255), "c": (255, 255, 255, 255), "d": (176, 200, 224, 255), ".": (0, 0, 0, 0)}
    im = np.zeros((8, 8, 4), dtype=np.uint8)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r): im[y, x] = pal[ch]
    d = OUT / "textures/particle"; d.mkdir(parents=True, exist_ok=True)
    Image.fromarray(im, "RGBA").save(d / "hail_0.png")
    p = OUT / "particles"; p.mkdir(parents=True, exist_ok=True)
    (p / "hail.json").write_text(json.dumps({"textures": [f"{NS}:hail_0"]}, indent=2))

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    if sys.argv[1:] == ["particles"]: dust(); puff(); mote(); water(); caustics(); spray(); rain(); prints(); hail()      # leave the sounds alone
    else: dust(); puff(); mote(); water(); caustics(); spray(); rain(); prints(); hail(); sounds()
    print("weather assets written to", OUT)
