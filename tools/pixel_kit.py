"""A small toolkit for hand-built 16x16 food sprites: shapes shaded from one base colour (dark edge, shadow, base, light, highlight), light from the top left."""
import math
from PIL import Image

def clamp(v): return max(0, min(255, int(v)))
def ramp(c):
    """Five tones of a colour: 0 the dark edge (shifted towards blue), 1 shadow, 2 base, 3 light, 4 highlight."""
    r, g, b = c
    return [(clamp(r * 0.30 + 8), clamp(g * 0.26 + 6), clamp(b * 0.32 + 14)), (clamp(r * 0.62), clamp(g * 0.58), clamp(b * 0.64)), (clamp(r), clamp(g), clamp(b)),
            (clamp(r + (255 - r) * 0.34 + 6), clamp(g + (255 - g) * 0.30), clamp(b + (255 - b) * 0.26)), (clamp(r + (255 - r) * 0.68 + 4), clamp(g + (255 - g) * 0.64), clamp(b + (255 - b) * 0.58))]
def mix(a, b, t): return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))

class Sp:
    def __init__(self): self.g = [[None] * 16 for _ in range(16)]
    def put(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < 16 and 0 <= y < 16 and c: self.g[y][x] = tuple(c)
    def at(self, x, y): return self.g[y][x] if 0 <= x < 16 and 0 <= y < 16 else None
    def line(self, x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0), 1))
        for i in range(n + 1): self.put(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, c)
    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1): self.put(x, y, c)
    def ball(self, cx, cy, r, base, sy=1.0, hi=True):
        """A shaded ball (or egg: sy squashes it): light from the top left, a shadowed bottom right, a glint."""
        t = ramp(base)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, (y + 0.5 - cy) / sy; d = math.hypot(dx, dy)
                if d <= r:
                    l = (dx * -0.7 + dy * -0.7) / max(d, 0.01) * (d / r)
                    self.put(x, y, t[3] if l > 0.42 else (t[2] if l > -0.18 else t[1]))
        if hi: self.put(cx - r * 0.38, cy - sy * r * 0.42, t[4])
    def ellipse(self, cx, cy, rx, ry, base, hi=True):
        t = ramp(base)
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                if dx * dx + dy * dy <= 1:
                    l = dx * -0.7 + dy * -0.7
                    self.put(x, y, t[3] if l > 0.45 else (t[2] if l > -0.25 else t[1]))
    def rim(self):
        """A shadowed edge at the bottom and right and a lit one at the top and left."""
        new = [row[:] for row in self.g]
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if not c: continue
                if self.at(x + 1, y) is None or self.at(x, y + 1) is None: new[y][x] = tuple(clamp(v * 0.78) for v in c)
                elif self.at(x - 1, y) is None or self.at(x, y - 1) is None: new[y][x] = tuple(clamp(v + (255 - v) * 0.18) for v in c)
        self.g = new
    def outline(self, c=None):
        """A dark edge round the shape, in a darker version of the colour beside it unless one is given."""
        edge = [(x, y) for y in range(16) for x in range(16) if self.g[y][x] is None and any(self.at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
        for x, y in edge:
            near = [self.at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if self.at(x + dx, y + dy)][0]
            self.g[y][x] = c or ramp(near)[0]
    def image(self):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); p = im.load()
        for y in range(16):
            for x in range(16):
                if self.g[y][x]: p[x, y] = tuple(self.g[y][x]) + (255,)
        return im
