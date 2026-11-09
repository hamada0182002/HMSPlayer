"""Generates every HMS Player icon/logo asset (orange brand).
Run:  python3 design/make_logo.py   (needs Pillow)
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
SS = 4  # supersampling

C1, C2, C3 = (255, 184, 77), (255, 122, 0), (232, 58, 0)   # soft orange -> orange -> deep orange
NAVY = (11, 18, 32)


def gradient(size):
    """diagonal orange gradient"""
    w = h = size
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2)
            if t < .5:
                k = t / .5
                c = tuple(int(C1[i] + (C2[i] - C1[i]) * k) for i in range(3))
            else:
                k = (t - .5) / .5
                c = tuple(int(C2[i] + (C3[i] - C2[i]) * k) for i in range(3))
            px[x, y] = c
    return img


def glyph(size, color=(255, 255, 255, 255)):
    """white play-button ring + play triangle + stream signal arcs, on transparent"""
    S = size * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy, R = .47 * S, .56 * S, .225 * S
    sw = .052 * S
    # ring
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=color, width=int(sw))
    # rounded play triangle
    t = .105 * S
    pts = [(cx - t * .75, cy - t * 1.15), (cx - t * .75, cy + t * 1.15), (cx + t * 1.25, cy)]
    r = int(.022 * S)
    d.polygon(pts, fill=color)
    d.line(pts + [pts[0], pts[1]], fill=color, width=r * 2, joint="curve")
    for p in pts:
        d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color)
    # signal arcs (top-right), rounded caps
    for rad, a in ((R + .085 * S, 255), (R + .165 * S, 235)):
        a0, a1 = -78, -12
        bw = int(.040 * S)
        box = [cx - rad, cy - rad, cx + rad, cy + rad]
        d.arc(box, a0, a1, fill=color[:3] + (a,), width=bw)
        for ang in (a0, a1):
            ex = cx + (rad - bw / 2) * math.cos(math.radians(ang))
            ey = cy + (rad - bw / 2) * math.sin(math.radians(ang))
            d.ellipse([ex - bw / 2, ey - bw / 2, ex + bw / 2, ey + bw / 2], fill=color[:3] + (a,))
    return im.resize((size, size), Image.LANCZOS)


def squircle_mask(size, radius_frac=.23):
    S = size * SS
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * radius_frac), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def circle_mask(size):
    S = size * SS
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse([0, 0, S - 1, S - 1], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def badge(size, shape="squircle"):
    bg = gradient(size).convert("RGBA")
    # soft highlight on top-left
    hl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([-size * .3, -size * .55, size * .85, size * .45], fill=(255, 255, 255, 38))
    hl = hl.filter(ImageFilter.GaussianBlur(size * .09))
    bg = Image.alpha_composite(bg, hl)
    g = glyph(size)
    # drop shadow for the glyph
    sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh.paste((120, 30, 0, 110), mask=g.split()[3])
    sh = sh.filter(ImageFilter.GaussianBlur(size * .012))
    bg = Image.alpha_composite(bg, Image.new("RGBA", (size, size), (0, 0, 0, 0)))
    off = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    off.paste(sh, (0, int(size * .012)))
    bg = Image.alpha_composite(bg, off)
    bg = Image.alpha_composite(bg, g)
    m = squircle_mask(size) if shape == "squircle" else circle_mask(size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(bg, mask=m)
    return out


def adaptive_fg(size):
    """white glyph centred inside the 66% safe zone of a 108dp canvas"""
    g = glyph(size * 2)
    bbox = g.getbbox()
    g = g.crop(bbox)
    target = int(size * .56)
    s = target / max(g.size)
    g = g.resize((int(g.size[0] * s), int(g.size[1] * s)), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(g, ((size - g.size[0]) // 2, (size - g.size[1]) // 2), g)
    return out


def font(sz, bold=True):
    p = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    return ImageFont.truetype(p, sz)


def banner(w=640, h=360):
    """Android TV banner (shown as 320x180dp)"""
    im = Image.new("RGBA", (w, h), NAVY + (255,))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([-w * .2, -h * .6, w * .6, h * .9], fill=(255, 110, 0, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    im = Image.alpha_composite(im, glow)
    b = badge(int(h * .56))
    by = (h - b.size[1]) // 2
    im.paste(b, (int(w * .09), by), b)
    d = ImageDraw.Draw(im)
    tx = int(w * .09) + b.size[0] + int(w * .06)
    d.text((tx, int(h * .27)), "HMS", font=font(int(h * .27)), fill=(255, 255, 255, 255))
    d.text((tx + 4, int(h * .60)), "PLAYER", font=font(int(h * .135)), fill=C2 + (255,))
    d.rounded_rectangle([tx + 6, int(h * .565), tx + int(w * .10), int(h * .565) + 5], radius=3, fill=C1 + (255,))
    return im


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in dens.items():
        d = os.path.join(RES, f"mipmap-{name}")
        save(badge(int(48 * k)), f"{d}/ic_launcher.png")
        save(badge(int(48 * k), "circle"), f"{d}/ic_launcher_round.png")
        save(adaptive_fg(int(108 * k)), f"{d}/ic_launcher_foreground.png")
        save(gradient(int(108 * k)).convert("RGBA"), f"{d}/ic_launcher_background.png")
    save(badge(512), os.path.join(RES, "drawable-nodpi", "logo_mark.png"))
    save(banner(), os.path.join(RES, "drawable-xhdpi", "tv_banner.png"))
    here = os.path.dirname(__file__)
    save(badge(512), os.path.join(here, "playstore_icon_512.png"))
    save(banner(1280, 720), os.path.join(here, "logo_banner_preview.png"))
    print("done")
