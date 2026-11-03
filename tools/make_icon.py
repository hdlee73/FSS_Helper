"""앱 아이콘(시안 C: 문서 위 돋보기 + FSS)을 PNG/ICO로 그린다. 사용: python3 tools/make_icon.py"""
from PIL import Image, ImageDraw, ImageFont
import os, sys

S = 1024
U = S / 64  # 64 그리드 기준
GREEN = (33, 92, 69, 255)
DEEP = (22, 66, 49, 255)
MINT = (127, 214, 168, 255)
WHITE = (255, 255, 255, 255)
LINE = (178, 214, 196, 255)
FONT = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"

def u(v):
    return int(round(v * U))

def draw():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=u(14), fill=GREEN)
    # 문서
    d.rounded_rectangle([u(10), u(8), u(42), u(53)], radius=u(5), fill=WHITE)
    # FSS 글자
    f = ImageFont.truetype(FONT, u(12.5))
    text = "FSS"
    bb = d.textbbox((0, 0), text, font=f)
    tw = bb[2] - bb[0]
    d.text((u(26) - tw / 2 - bb[0], u(14.5) - bb[1]), text, font=f, fill=GREEN)
    # 조문 줄
    for y, x2 in ((33, 35), (39, 30), (45, 25)):
        d.rounded_rectangle([u(16), u(y - 1.5), u(x2), u(y + 1.5)], radius=u(1.5), fill=LINE)
    # 돋보기: 렌즈(흰 바탕 반투명) + 민트 테두리 + 손잡이
    cx, cy, r = 42, 42, 11
    lens = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lens)
    ld.ellipse([u(cx - r), u(cy - r), u(cx + r), u(cy + r)], fill=(240, 250, 245, 255))
    im = Image.alpha_composite(im, lens)
    d = ImageDraw.Draw(im)
    w = u(4.2)
    d.ellipse([u(cx - r), u(cy - r), u(cx + r), u(cy + r)], outline=MINT, width=w)
    hx0, hy0 = cx + r * 0.72, cy + r * 0.72
    d.line([u(hx0), u(hy0), u(55), u(55)], fill=MINT, width=u(6))
    d.ellipse([u(55) - u(3), u(55) - u(3), u(55) + u(3), u(55) + u(3)], fill=MINT)
    return im

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/resources/icons"
    os.makedirs(out, exist_ok=True)
    im = draw()
    im.save(os.path.join(out, "app-1024.png"))
    im.resize((256, 256), Image.LANCZOS).save(os.path.join(out, "app.png"))
    im.save(os.path.join(out, "app.ico"), sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("ok")
