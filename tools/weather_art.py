"""Generates Alterra Weather's own assets into weather/src/main/resources/assets/alterra_weather:
 - particles: dust (8x8 specks) and puff (16x16 clumpy pixel clouds, tinted and made translucent in game)
 - sounds: three seamless stereo wind loops (synthesised: shaped noise, gusts, a faint whistle), as Ogg Vorbis
Needs numpy, Pillow and ffmpeg. Run: python3 tools/weather_art.py"""
import json, math, random, subprocess, tempfile, wave
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

LOOPS = {   # name: (seed, layers (lo Hz, hi Hz, amp, lfo cycles, lfo depth), gust cycles, gust depth)
    "wind_dust": (1, [(40, 220, 0.9, 3, 0.4), (220, 900, 0.8, 5, 0.5), (900, 2200, 0.35, 8, 0.6), (2800, 7000, 0.28, 13, 0.7)], 4, 0.5),
    "wind_snow": (2, [(120, 600, 0.6, 4, 0.4), (500, 1800, 0.8, 6, 0.5), (700, 780, 0.28, 7, 0.9), (3000, 9000, 0.22, 11, 0.6)], 5, 0.55),
    "wind_calm": (3, [(80, 400, 0.8, 3, 0.35), (400, 1200, 0.45, 5, 0.5), (3500, 8000, 0.12, 9, 0.8)], 3, 0.4),
}

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
    (OUT / "sounds.json").write_text(json.dumps(js, indent=2) + "\n")
    (OUT / "lang").mkdir(parents=True, exist_ok=True)
    (OUT / "lang/en_us.json").write_text(json.dumps({f"subtitles.{NS}.wind_dust": "Sandstorm howls", f"subtitles.{NS}.wind_snow": "Blizzard howls", f"subtitles.{NS}.wind_calm": "Wind blows"}, indent=2) + "\n")

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    dust(); puff(); sounds()
    print("weather assets written to", OUT)
