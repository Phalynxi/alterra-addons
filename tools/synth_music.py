"""Alterra Music: composes and renders the background tracks into music/src/main/resources/assets/alterra_music.

Each kind of land (cold, temperate, warm, dry, seas, caves, sky) has its own scale, tempo, instruments and reverb; every track is
generated from a seed (so the same command always makes the same music): a slow chord progression held by a pad, a sparse arpeggio over
it that thickens and thins out, an occasional long melody note, and quiet bars in between - in the spirit of Minecraft's own ambient music.
Instruments are synthesised from scratch: piano (additive, higher partials die faster, a soft hammer), bell (inharmonic partials),
marimba, plucked string (Karplus-Strong) and a detuned pad. Needs numpy, scipy and ffmpeg. Run: python3 tools/synth_music.py [kind ...]"""
import json, subprocess, sys, tempfile, wave
from pathlib import Path
import numpy as np
from scipy.signal import fftconvolve, lfilter

OUT = Path(__file__).resolve().parent.parent / "music/src/main/resources/assets/alterra_music"
NS = "alterra_music"
SR = 32000
TAIL = 4.0   # seconds of reverb after the last note

SCALES = {"lydian": [0, 2, 4, 6, 7, 9, 11], "ionian": [0, 2, 4, 5, 7, 9, 11], "dorian": [0, 2, 3, 5, 7, 9, 10], "mixolydian": [0, 2, 4, 5, 7, 9, 10],
          "aeolian": [0, 2, 3, 5, 7, 8, 10], "phrygian_dominant": [0, 1, 4, 5, 7, 8, 10], "pentatonic": [0, 2, 4, 7, 9, 12, 14]}

# kind: tempo (bpm), root (MIDI), per track: (scale, progression of scale degrees, arp instrument, melody instrument), pad kind, arp density, reverb seconds
KINDS = {
    "cold":      dict(bpm=54, root=50, pad="glass", density=0.34, reverb=4.5, tracks=[("lydian", [0, 4, 1, 5], "bell", "bell"), ("pentatonic", [0, 3, 4, 2], "bell", "piano")]),
    "temperate": dict(bpm=64, root=48, pad="warm", density=0.40, reverb=3.2, tracks=[("ionian", [0, 4, 5, 3], "piano", "piano"), ("mixolydian", [0, 6, 3, 4], "piano", "bell")]),
    "warm":      dict(bpm=72, root=45, pad="warm", density=0.46, reverb=2.6, tracks=[("mixolydian", [0, 3, 6, 4], "marimba", "piano"), ("dorian", [0, 3, 0, 4], "marimba", "marimba")]),
    "dry":       dict(bpm=58, root=45, pad="drone", density=0.36, reverb=3.0, tracks=[("phrygian_dominant", [0, 1, 0, 6], "pluck", "pluck"), ("aeolian", [0, 5, 6, 0], "pluck", "piano")]),
    "seas":      dict(bpm=46, root=47, pad="deep", density=0.22, reverb=5.5, tracks=[("dorian", [0, 5, 3, 4], "piano", "bell"), ("lydian", [0, 1, 4, 0], "piano", "piano")]),
    "caves":     dict(bpm=40, root=40, pad="drone", density=0.16, reverb=6.0, tracks=[("aeolian", [0, 0, 5, 3], "bell", "piano"), ("phrygian_dominant", [0, 0, 1, 0], "piano", "bell")]),
    "sky":       dict(bpm=60, root=55, pad="glass", density=0.42, reverb=4.2, tracks=[("lydian", [0, 1, 4, 5], "bell", "piano"), ("ionian", [0, 5, 3, 4], "piano", "bell")]),
}

def hz(m): return 440.0 * 2 ** ((m - 69) / 12)
def t_axis(d): return np.arange(int(d * SR)) / SR

# ---------------------------------------------------------------- instruments (each returns a mono array)
def piano(f, d, vel=1.0):
    t = t_axis(d + 0.1); x = np.zeros_like(t)
    base_decay = 2.4 / (f / 220) ** 0.35
    for n in range(1, 13):
        fn = f * n * np.sqrt(1 + 0.0004 * n * n)
        if fn > 9000: break
        x += (1 / n ** 1.15) * np.sin(2 * np.pi * fn * t + n) * np.exp(-t / (base_decay / (1 + 0.55 * (n - 1))))
    a = np.minimum(1, t / 0.004); x *= a
    rng = np.random.default_rng(int(f * 10))
    hammer = rng.normal(size=len(t)) * np.exp(-t / 0.012); hammer = lfilter([0.15], [1, -0.85], hammer)
    return (x * 0.5 + hammer * 0.12) * vel

def bell(f, d, vel=1.0):
    t = t_axis(d + 0.2); x = np.zeros_like(t)
    for r, a, dec in zip([1, 2.0, 2.76, 4.07, 5.4, 6.8], [1, 0.55, 0.5, 0.32, 0.22, 0.12], [3.4, 2.4, 1.9, 1.2, 0.8, 0.55]):
        if f * r < 10000: x += a * np.sin(2 * np.pi * f * r * t) * np.exp(-t / dec)
    return x * np.minimum(1, t / 0.002) * 0.4 * vel

def marimba(f, d, vel=1.0):
    t = t_axis(min(d, 1.4) + 0.1); x = np.zeros_like(t)
    for r, a, dec in zip([1, 3.99, 9.7], [1, 0.38, 0.08], [0.55, 0.16, 0.05]): x += a * np.sin(2 * np.pi * f * r * t) * np.exp(-t / dec)
    return x * np.minimum(1, t / 0.002) * 0.55 * vel

def pluck(f, d, vel=1.0):
    n = int(SR / f); length = int((min(d, 3.0) + 0.1) * SR)
    rng = np.random.default_rng(int(f * 7))
    burst = rng.uniform(-1, 1, n); burst = lfilter([0.5, 0.5], [1], burst)
    a = np.zeros(n + 2); a[0] = 1; a[n] = -0.498; a[n + 1] = -0.498
    x = np.zeros(length); x[:n] = burst
    return lfilter([1], a, x) * 0.6 * vel

def pad(freqs, d, kind):
    t = t_axis(d); x = np.zeros_like(t)
    harm = {"glass": 5, "warm": 7, "deep": 4, "drone": 6}[kind]
    for f in freqs:
        for det in (-0.07, 0.0, 0.08):
            fv = f * 2 ** (det / 12)
            vib = 0.0012 * np.sin(2 * np.pi * 0.2 * t + f)
            for n in range(1, harm + 1): x += (1 / n ** (1.6 if kind != "glass" else 1.1)) * np.sin(2 * np.pi * fv * n * (1 + vib) * t + n * 1.3)
    x /= max(1, len(freqs)) * 3
    att = min(1.6, d / 3); rel = min(2.2, d / 3)
    env = np.minimum(1, t / att) * np.minimum(1, (d - t) / rel)
    return x * env * 0.5

# ---------------------------------------------------------------- composing and mixing
def render(kind, idx):
    cfg = KINDS[kind]; scale_name, prog, arp_i, mel_i = cfg["tracks"][idx]
    rng = np.random.default_rng(hash(("alterra", kind, idx)) % (2 ** 32) if False else (sum(map(ord, kind)) * 977 + idx * 131))
    scale = SCALES[scale_name]; root = cfg["root"]
    beat = 60.0 / cfg["bpm"]; bar = 4 * beat
    bars = int(round(112 / bar)); total = bars * bar
    L = np.zeros(int((total + TAIL + 1) * SR)); R = np.zeros_like(L)
    inst = {"piano": piano, "bell": bell, "marimba": marimba, "pluck": pluck}

    def deg(i, octave=0):   # the note (MIDI) at scale degree i (may be above 6: next octave)
        return root + scale[i % 7] + 12 * (i // 7) + 12 * octave
    def add(sig, at, pan=0.0, gain=1.0):
        i = int(at * SR)
        if i >= len(L): return
        sig = sig[: len(L) - i]
        L[i:i + len(sig)] += sig * gain * (1 - max(0, pan)); R[i:i + len(sig)] += sig * gain * (1 + min(0, pan))

    for b in range(bars):
        chord_deg = prog[b % len(prog)] + (0 if b < bars // 2 or idx == 0 else (0))
        tones = [chord_deg, chord_deg + 2, chord_deg + 4] + ([chord_deg + 6] if rng.random() < 0.35 else [])
        start = b * bar
        pad_notes = [hz(deg(t_, -1)) for t_ in tones[:3]] + ([hz(deg(chord_deg, -2))] if cfg["pad"] in ("deep", "drone") else [])
        if cfg["pad"] == "drone": pad_notes = [hz(root - 12), hz(root - 5)] + pad_notes[:2]
        d = bar * (1.0 if cfg["pad"] != "drone" else 1.0) + 1.2   # overlap into the next bar: no gaps
        add(pad(pad_notes, d, cfg["pad"]), start, 0.0, 0.5)
        # how busy this bar is: quiet at the ends, fullest in the middle, with some bars left to the pad
        phase = b / (bars - 1)
        intensity = 0.25 + 0.75 * np.sin(np.pi * phase) ** 0.8
        if rng.random() < 0.18 and 2 < b < bars - 3: intensity *= 0.1
        pos = 0   # arp position wanders over the chord tones, up and down
        for slot in range(8):
            if rng.random() < cfg["density"] * intensity * (1.3 if slot % 2 == 0 else 0.75):
                pos = int(np.clip(pos + rng.integers(-2, 3), -2, len(tones) + 2))
                tone = tones[pos % len(tones)] + 7 * (pos // len(tones)) + 7   # one octave up from the pad
                at = start + slot * beat / 2 + rng.normal(0, 0.012)
                add(inst[arp_i](hz(deg(tone)), 2.6, 0.5 + 0.5 * rng.random() * intensity), max(0, at), rng.uniform(-0.5, 0.5), 0.55)
        if b % 2 == 1 and rng.random() < 0.55 * intensity + 0.1:   # a long melody note, an octave higher
            n = tones[rng.integers(0, len(tones))] + 14 + int(rng.integers(0, 3)) * (1 if rng.random() < 0.5 else 0)
            add(inst[mel_i](hz(deg(n)), 4.0, 0.75), start + beat * rng.choice([0.0, 1.0, 2.0]) + rng.normal(0, 0.01), rng.uniform(-0.3, 0.3), 0.62)
        if kind == "caves" and rng.random() < 0.12: add(bell(hz(deg(rng.integers(7, 14))), 3.0, 0.5), start + rng.uniform(0, bar), rng.uniform(-0.8, 0.8), 0.35)

    # reverb: a decaying, darkening noise impulse, different in each ear
    def impulse(seed):
        r = np.random.default_rng(seed); n = int(cfg["reverb"] * SR); t = np.arange(n) / SR
        ir = r.normal(size=n) * np.exp(-6.9 * t / cfg["reverb"])
        for k, cut in enumerate((0.55, 0.35)): ir = lfilter([1 - cut], [1, -cut], ir) if k == 0 else ir
        ir[: int(0.012 * SR)] *= np.linspace(0, 1, int(0.012 * SR))
        return ir / np.sqrt(np.sum(ir ** 2))
    wetL = fftconvolve(L, impulse(1))[: len(L)]; wetR = fftconvolve(R, impulse(2))[: len(R)]
    mix = 0.42
    outL = L * (1 - mix * 0.6) + wetL * mix * 1.6; outR = R * (1 - mix * 0.6) + wetR * mix * 1.6
    out = np.stack([outL, outR], 1)
    n = int((total + TAIL) * SR); out = out[:n]
    fade_in = np.minimum(1, np.arange(n) / (2.0 * SR)); fade_out = np.minimum(1, (n - np.arange(n)) / (TAIL * SR))
    out *= (fade_in * fade_out)[:, None]
    out = out / (np.abs(out).max() + 1e-9) * 0.7
    return out

def encode(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as d:
        wav = Path(d) / "x.wav"
        w = wave.open(str(wav), "wb"); w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes()); w.close()
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "vorbis", "-strict", "-2", "-b:a", "80k", str(path)], check=True)

def main(kinds):
    d = OUT / "sounds/music"; d.mkdir(parents=True, exist_ok=True)
    sj = OUT / "sounds.json"; sounds = json.loads(sj.read_text()) if sj.exists() else {}
    for kind in kinds:
        for idx in range(len(KINDS[kind]["tracks"])):
            name = f"{kind}_{idx + 1}"
            encode(render(kind, idx), d / f"{name}.ogg")
            sounds[f"music.{name}"] = {"sounds": [{"name": f"{NS}:music/{name}", "stream": True}]}
            print("wrote", name, f"{(d / (name + '.ogg')).stat().st_size / 1e6:.2f} MB", flush=True)
    sj.write_text(json.dumps(sounds, indent=2) + "\n")

if __name__ == "__main__":
    main(sys.argv[1:] or list(KINDS))
