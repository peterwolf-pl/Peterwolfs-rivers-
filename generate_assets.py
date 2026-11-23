#!/usr/bin/env python3
"""
Asset and Model Generator for 'Wressla' - Historic Wrocław Tram River Catamaran.
Generates:
  1. High-resolution 2048x2048 texture atlas (wressla.png)
  2. Blockbench 3D model (wressla.bbmodel) with embedded texture, hierarchy, and animations
  3. Wavefront OBJ mesh (wressla.obj + wressla.mtl)
  4. Item texture (wressla_item.png) and Mod icon (icon.png)
"""

import json
import base64
import math
import uuid
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent

def create_texture():
    atlas = Image.new("RGBA", (2048, 2048), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)

    # =========================================================================
    # Tile 0: Tram Ruby Red Panels & Decals (0..512, 0..512)
    # =========================================================================
    red_tile = Image.new("RGBA", (512, 512), (140, 24, 32, 255))
    rdraw = ImageDraw.Draw(red_tile)
    # Subtle panel grain
    for y in range(0, 512, 4):
        tint = (135 + (y % 7), 22 + (y % 3), 30, 255)
        rdraw.line([(0, y), (511, y)], fill=tint)
    # White perimeter border
    rdraw.rectangle([(8, 8), (503, 503)], outline=(245, 247, 248, 255), width=8)
    rdraw.rectangle([(24, 24), (487, 487)], outline=(245, 247, 248, 255), width=4)
    # Decorative corner gussets (tram style filigree curves)
    for cx, cy in [(24, 24), (487, 24), (24, 487), (487, 487)]:
        rdraw.arc([cx-24, cy-24, cx+24, cy+24], start=0, end=360, fill=(245, 247, 248, 255), width=4)
    atlas.paste(red_tile, (0, 0))

    # =========================================================================
    # Tile 1: White Metal Framework (512..1024, 0..512)
    # =========================================================================
    white_tile = Image.new("RGBA", (512, 512), (242, 245, 247, 255))
    wdraw = ImageDraw.Draw(white_tile)
    for x in range(0, 512, 16):
        wdraw.line([(x, 0), (x, 511)], fill=(228, 232, 235, 255), width=1)
        wdraw.line([(x+8, 0), (x+8, 511)], fill=(255, 255, 255, 255), width=1)
    for y in range(0, 512, 32):
        wdraw.line([(0, y), (511, y)], fill=(215, 220, 224, 255), width=2)
    atlas.paste(white_tile, (512, 0))

    # =========================================================================
    # Tile 2: Teak Deck Planking (1024..1536, 0..512)
    # =========================================================================
    teak_tile = Image.new("RGBA", (512, 512), (180, 134, 82, 255))
    tdraw = ImageDraw.Draw(teak_tile)
    plank_w = 16
    for x in range(0, 512, plank_w):
        # Caulking line
        tdraw.line([(x, 0), (x, 511)], fill=(70, 52, 36, 255), width=2)
        # Highlight edge
        tdraw.line([(x+2, 0), (x+2, 511)], fill=(200, 155, 102, 255), width=1)
        # Random wood color variance
        pv = ((x * 37) % 25) - 12
        p_color = (180 + pv, 134 + pv, 82 + pv // 2, 255)
        tdraw.rectangle([(x+3, 0), (x+plank_w-1, 511)], fill=p_color)
        # Butt joints
        for by in range((x * 19) % 128, 512, 128):
            tdraw.line([(x+2, by), (x+plank_w-1, by)], fill=(70, 52, 36, 255), width=2)
    atlas.paste(teak_tile, (1024, 0))

    # =========================================================================
    # Tile 3: Pontoon Black Marine Floats (1536..2048, 0..512)
    # =========================================================================
    pontoon_tile = Image.new("RGBA", (512, 512), (30, 34, 38, 255))
    pdraw = ImageDraw.Draw(pontoon_tile)
    for y in range(0, 512, 32):
        # Flanged modular pontoon segments
        pdraw.line([(0, y), (511, y)], fill=(18, 20, 23, 255), width=4)
        pdraw.line([(0, y+4), (511, y+4)], fill=(48, 54, 60, 255), width=2)
    for x in range(0, 512, 64):
        pdraw.line([(x, 0), (x, 511)], fill=(22, 25, 28, 255), width=3)
    atlas.paste(pontoon_tile, (1536, 0))

    # =========================================================================
    # Tile 4: Outboard Motor & Mercury Accents (0..512, 512..1024)
    # =========================================================================
    motor_tile = Image.new("RGBA", (512, 512), (20, 22, 25, 255))
    mdraw = ImageDraw.Draw(motor_tile)
    # Gloss sheen
    mdraw.rectangle([(0, 64), (511, 160)], fill=(32, 36, 40, 255))
    # Racing red Mercury stripe
    mdraw.rectangle([(0, 160), (511, 200)], fill=(220, 28, 36, 255))
    mdraw.rectangle([(0, 204), (511, 212)], fill=(245, 245, 245, 255))
    # Metallic lower unit
    mdraw.rectangle([(0, 320), (511, 511)], fill=(55, 60, 66, 255))
    for y in range(320, 511, 16):
        mdraw.line([(0, y), (511, y)], fill=(42, 46, 50, 255), width=2)
    # Propeller bronze / stainless
    mdraw.ellipse([(128, 384), (256, 511)], fill=(190, 195, 200, 255), outline=(130, 135, 140, 255), width=3)
    atlas.paste(motor_tile, (0, 512))

    # =========================================================================
    # Tile 5: Translucent Tinted Canopy Glass (512..1024, 512..1024)
    # =========================================================================
    glass_tile = Image.new("RGBA", (512, 512), (90, 165, 195, 120))
    gdraw = ImageDraw.Draw(glass_tile)
    # Glass frame dividers and reflection lines
    for x in range(0, 512, 64):
        gdraw.line([(x, 0), (x, 511)], fill=(240, 248, 255, 200), width=3)
    for y in range(0, 512, 128):
        gdraw.line([(0, y), (511, y)], fill=(240, 248, 255, 200), width=3)
    # Reflection glint
    for diag in range(-512, 512, 96):
        gdraw.line([(diag, 0), (diag+300, 511)], fill=(255, 255, 255, 60), width=6)
    atlas.paste(glass_tile, (512, 512))

    # =========================================================================
    # Tile 6: Wooden Benches & Steering Wheel (1024..1536, 512..1024)
    # =========================================================================
    bench_tile = Image.new("RGBA", (512, 512), (160, 105, 55, 255))
    bdraw = ImageDraw.Draw(bench_tile)
    for y in range(0, 256, 16):
        bdraw.line([(0, y), (511, y)], fill=(90, 55, 25, 255), width=2)
        bdraw.line([(0, y+2), (511, y+2)], fill=(185, 125, 70, 255), width=1)
    # Steering wheel brass hub & spoke texture
    bdraw.rectangle([(0, 256), (511, 511)], fill=(130, 85, 45, 255))
    bdraw.ellipse([(64, 320), (192, 448)], fill=(210, 160, 60, 255), outline=(150, 105, 30, 255), width=6)
    bdraw.ellipse([(96, 352), (160, 416)], fill=(70, 50, 20, 255))
    # Control console instrument panel
    bdraw.rectangle([(256, 256), (511, 511)], fill=(35, 40, 45, 255))
    bdraw.ellipse([(280, 280), (340, 340)], outline=(200, 205, 210, 255), width=3)
    bdraw.ellipse([(360, 280), (420, 340)], outline=(200, 205, 210, 255), width=3)
    bdraw.rectangle([(450, 300), (470, 450)], fill=(20, 22, 24, 255)) # Throttle track
    bdraw.ellipse([(445, 340), (475, 370)], fill=(210, 30, 35, 255)) # Throttle red knob
    atlas.paste(bench_tile, (1024, 512))

    # =========================================================================
    # Tile 7: Marine Accessories - Fenders, Lifebuoy, Flag (1536..2048, 512..1024)
    # =========================================================================
    acc_tile = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    adraw = ImageDraw.Draw(acc_tile)
    # Blue Fender texture
    adraw.rectangle([(0, 0), (255, 300)], fill=(20, 85, 195, 255))
    adraw.rectangle([(20, 20), (235, 280)], fill=(28, 100, 218, 255))
    adraw.rectangle([(0, 0), (255, 30)], fill=(245, 245, 245, 255)) # top eyelet
    adraw.rectangle([(0, 270), (255, 300)], fill=(245, 245, 245, 255)) # bottom cap
    # Fender rope
    for ry in range(300, 512, 6):
        adraw.line([(64, ry), (80, ry+3)], fill=(190, 180, 150, 255), width=3)
    # Orange Lifebuoy
    adraw.ellipse([(270, 16), (496, 242)], fill=(255, 102, 0, 255))
    adraw.ellipse([(326, 72), (440, 186)], fill=(0, 0, 0, 0)) # hollow center
    # 4 reflective white bands
    adraw.rectangle([(370, 16), (396, 56)], fill=(250, 250, 250, 255))
    adraw.rectangle([(370, 202), (396, 242)], fill=(250, 250, 250, 255))
    adraw.rectangle([(270, 116), (310, 142)], fill=(250, 250, 250, 255))
    adraw.rectangle([(456, 116), (496, 142)], fill=(250, 250, 250, 255))
    # Blue Flag with "W" logo
    adraw.rectangle([(270, 270), (500, 480)], fill=(16, 75, 170, 255))
    # W wavy monogram on flag
    w_pts = [(300, 340), (330, 420), (360, 360), (390, 420), (420, 340),
             (405, 340), (385, 395), (360, 345), (335, 395), (315, 340)]
    adraw.polygon(w_pts, fill=(255, 255, 255, 255))
    atlas.paste(acc_tile, (1536, 512))

    # =========================================================================
    # Tile 8: High Resolution WRESSLA Decals & Wrocław Tram Logo (0..1024, 1024..1536)
    # =========================================================================
    decal_tile = Image.new("RGBA", (1024, 512), (140, 24, 32, 255))
    ddraw = ImageDraw.Draw(decal_tile)
    # White border
    ddraw.rectangle([(12, 12), (1011, 499)], outline=(245, 247, 248, 255), width=8)
    # Filigree corner curves
    for cx, cy in [(28, 28), (995, 28), (28, 483), (995, 483)]:
        ddraw.arc([cx-28, cy-28, cx+28, cy+28], start=0, end=360, fill=(245, 247, 248, 255), width=6)

    # Stylized Wrocław River Tram Logo on the left:
    # 3 wavy lines forming 'W' + Circled 'T'
    # Waves:
    logo_cx, logo_cy = 180, 256
    for offset in [-36, 0, 36]:
        wave_pts = []
        for x in range(logo_cx - 90, logo_cx + 10, 4):
            dx = x - (logo_cx - 40)
            y = logo_cy + offset + math.sin(dx * 0.08) * 12
            wave_pts.append((x, y))
        ddraw.line(wave_pts, fill=(255, 255, 255, 255), width=7)
    # Circled 'T':
    t_cx = logo_cx + 55
    ddraw.ellipse([(t_cx - 40, logo_cy - 40), (t_cx + 40, logo_cy + 40)], outline=(255, 255, 255, 255), width=8)
    # 'T' letter
    ddraw.line([(t_cx - 24, logo_cy - 22), (t_cx + 24, logo_cy - 22)], fill=(255, 255, 255, 255), width=8)
    ddraw.line([(t_cx, logo_cy - 22), (t_cx, logo_cy + 24)], fill=(255, 255, 255, 255), width=8)

    # Bold Typography "W R E S S L A"
    # We draw crisp blocky lettering so it's sharp without external TTF dependencies
    text_start_x = 340
    text_y = 180
    h = 150
    w_char = 60
    spacing = 30
    line_w = 16

    def draw_letter(char, x):
        if char == 'W':
            ddraw.line([(x, text_y), (x+15, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+15, text_y+h), (x+30, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+30, text_y+h//2), (x+45, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+45, text_y+h), (x+60, text_y)], fill=(255, 255, 255, 255), width=line_w)
        elif char == 'R':
            ddraw.line([(x, text_y), (x, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y), (x+50, text_y)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+50, text_y), (x+50, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y+h//2), (x+50, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+25, text_y+h//2), (x+55, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
        elif char == 'E':
            ddraw.line([(x, text_y), (x, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y), (x+50, text_y)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y+h//2), (x+40, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y+h), (x+50, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
        elif char == 'S':
            ddraw.line([(x+50, text_y), (x, text_y)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y), (x, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y+h//2), (x+50, text_y+h//2)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+50, text_y+h//2), (x+50, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+50, text_y+h), (x, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
        elif char == 'L':
            ddraw.line([(x, text_y), (x, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x, text_y+h), (x+50, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
        elif char == 'A':
            ddraw.line([(x, text_y+h), (x+25, text_y)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+25, text_y), (x+50, text_y+h)], fill=(255, 255, 255, 255), width=line_w)
            ddraw.line([(x+12, text_y+h//2+15), (x+38, text_y+h//2+15)], fill=(255, 255, 255, 255), width=line_w)

    curr_x = text_start_x
    for ch in "WRESSLA":
        draw_letter(ch, curr_x)
        curr_x += w_char + spacing

    atlas.paste(decal_tile, (0, 1024))

    return atlas

def generate_bbmodel(atlas_img):
    """Generates Blockbench .bbmodel with generic/modded entity format, elements, pivots, and animations."""
    img_byte_arr = io.BytesIO()
    atlas_img.save(img_byte_arr, format='PNG')
    img_b64 = "data:image/png;base64," + base64.b64encode(img_byte_arr.getvalue()).decode('ascii')

    tex_uuid = str(uuid.uuid4())
    elements = []
    outliner = []

    def make_cube(name, fx, fy, fz, tx, ty, tz, ox, oy, oz, u1, v1, u2, v2):
        u_id = str(uuid.uuid4())
        # Blockbench UV mapping: [minU, minV, maxU, maxV]
        scale_u = 2048.0
        scale_v = 2048.0
        face_uv = [u1 / scale_u * 16.0, v1 / scale_v * 16.0, u2 / scale_u * 16.0, v2 / scale_v * 16.0]
        # Box UV or Per-face UV
        faces = {}
        for f in ["north", "south", "east", "west", "up", "down"]:
            faces[f] = {"uv": face_uv, "texture": 0}

        elem = {
            "name": name,
            "box_uv": False,
            "from": [round(fx, 2), round(fy, 2), round(fz, 2)],
            "to": [round(tx, 2), round(ty, 2), round(tz, 2)],
            "origin": [round(ox, 2), round(oy, 2), round(oz, 2)],
            "faces": faces,
            "type": "cube",
            "uuid": u_id
        }
        elements.append(elem)
        return u_id

    # 1. FLOATS (CATAMARAN PONTOONS)
    # Port Float (X = -24 .. -12)
    # Main tube
    f_p_main = make_cube("float_port_main", -24, 0, -64, -12, 12, 72, -18, 6, 4, 1536, 0, 2048, 512)
    # Bow wedge taper
    f_p_bow = make_cube("float_port_bow", -23, 1, -80, -13, 11, -64, -18, 6, -72, 1536, 0, 2048, 512)
    # Stern cap
    f_p_stern = make_cube("float_port_stern", -23.5, 0.5, 72, -12.5, 11.5, 76, -18, 6, 74, 1536, 0, 2048, 512)
    # Risers to deck
    f_p_riser1 = make_cube("float_port_riser1", -21, 12, -48, -15, 14, -40, -18, 13, -44, 1536, 0, 2048, 512)
    f_p_riser2 = make_cube("float_port_riser2", -21, 12, 0, -15, 14, 8, -18, 13, 4, 1536, 0, 2048, 512)
    f_p_riser3 = make_cube("float_port_riser3", -21, 12, 48, -15, 14, 56, -18, 13, 52, 1536, 0, 2048, 512)

    # Starboard Float (X = +12 .. +24)
    f_s_main = make_cube("float_starboard_main", 12, 0, -64, 24, 12, 72, 18, 6, 4, 1536, 0, 2048, 512)
    f_s_bow = make_cube("float_starboard_bow", 13, 1, -80, 23, 11, -64, 18, 6, -72, 1536, 0, 2048, 512)
    f_s_stern = make_cube("float_starboard_stern", 12.5, 0.5, 72, 23.5, 11.5, 76, 18, 6, 74, 1536, 0, 2048, 512)
    f_s_riser1 = make_cube("float_starboard_riser1", 15, 12, -48, 21, 14, -40, 18, 13, -44, 1536, 0, 2048, 512)
    f_s_riser2 = make_cube("float_starboard_riser2", 15, 12, 0, 21, 14, 8, 18, 13, 4, 1536, 0, 2048, 512)
    f_s_riser3 = make_cube("float_starboard_riser3", 15, 12, 48, 21, 14, 56, 18, 13, 52, 1536, 0, 2048, 512)

    # Transom crossbeam & Motor mount plate
    transom_beam = make_cube("transom_crossbeam", -24, 12, 74, 24, 15, 78, 0, 13.5, 76, 1536, 0, 2048, 512)
    motor_mount_bracket = make_cube("motor_mount_plate", -5, 8, 77, 5, 18, 81, 0, 13, 79, 0, 512, 512, 1024)

    floats_group = {
        "name": "floats",
        "origin": [0, 0, 0],
        "children": [f_p_main, f_p_bow, f_p_stern, f_p_riser1, f_p_riser2, f_p_riser3,
                     f_s_main, f_s_bow, f_s_stern, f_s_riser1, f_s_riser2, f_s_riser3,
                     transom_beam, motor_mount_bracket]
    }

    # 2. DECK (3.5m wide x 10m long = 56 x 160 units, Y = 14 to 16)
    deck_main = make_cube("deck_platform", -28, 14, -76, 28, 16, 76, 0, 15, 0, 1024, 0, 1536, 512)
    deck_bow_taper = make_cube("deck_bow_tip", -24, 14, -80, 24, 16, -76, 0, 15, -78, 1024, 0, 1536, 512)
    rubrail_p = make_cube("rubrail_port", -28.5, 14, -77, -27.5, 16.5, 77, -28, 15, 0, 512, 0, 1024, 512)
    rubrail_s = make_cube("rubrail_starboard", 27.5, 14, -77, 28.5, 16.5, 77, 28, 15, 0, 512, 0, 1024, 512)
    rubrail_f = make_cube("rubrail_front", -25, 14, -80.5, 25, 16.5, -79.5, 0, 15, -80, 512, 0, 1024, 512)
    rubrail_b = make_cube("rubrail_back", -28.5, 14, 76, 28.5, 16.5, 77, 0, 15, 76.5, 512, 0, 1024, 512)

    deck_group = {
        "name": "deck",
        "origin": [0, 15, 0],
        "children": [deck_main, deck_bow_taper, rubrail_p, rubrail_s, rubrail_f, rubrail_b]
    }

    # 3. SUPERSTRUCTURE - TRAM RED BULWARKS & WHITE FRAMING (Y = 16 to 30)
    # Port bulwark wall (with WRESSLA side decal)
    bw_p_mid = make_cube("bulwark_port_panel", -28, 16, -72, -27, 28, 72, -27.5, 22, 0, 0, 1024, 1024, 1536)
    bw_p_rail = make_cube("bulwark_port_handrail", -28.5, 28, -73, -26.5, 29.5, 73, -27.5, 28.75, 0, 512, 0, 1024, 512)

    # Starboard bulwark wall (with WRESSLA side decal)
    bw_s_mid = make_cube("bulwark_starboard_panel", 27, 16, -72, 28, 28, 72, 27.5, 22, 0, 0, 1024, 1024, 1536)
    bw_s_rail = make_cube("bulwark_starboard_handrail", 26.5, 28, -73, 28.5, 29.5, 73, 27.5, 28.75, 0, 512, 0, 1024, 512)

    # Front bow rail & panel
    bw_f_panel = make_cube("bulwark_front_panel", -26, 16, -78, 26, 28, -77, 0, 22, -77.5, 0, 0, 512, 512)
    bw_f_rail = make_cube("bulwark_front_handrail", -27, 28, -78.5, 27, 29.5, -76.5, 0, 28.75, -77.5, 512, 0, 1024, 512)

    # Stern railings (Port & Starboard with central gap)
    bw_b_p_panel = make_cube("bulwark_back_port", -28, 16, 75, -8, 28, 76, -18, 22, 75.5, 0, 0, 512, 512)
    bw_b_p_rail = make_cube("bulwark_back_port_rail", -28, 28, 74.5, -8, 29.5, 76.5, -18, 28.75, 75.5, 512, 0, 1024, 512)
    bw_b_s_panel = make_cube("bulwark_back_starboard", 8, 16, 75, 28, 28, 76, 18, 22, 75.5, 0, 0, 512, 512)
    bw_b_s_rail = make_cube("bulwark_back_starboard_rail", 8, 28, 74.5, 28, 29.5, 76.5, 18, 28.75, 75.5, 512, 0, 1024, 512)

    # White Vertical Canopy Pillars (5 pairs along length)
    pillars = []
    for z in [-56, -28, 0, 28, 56]:
        p_p = make_cube(f"canopy_post_p_{z}", -27, 16, z-1, -25, 42, z+1, -26, 29, z, 512, 0, 1024, 512)
        p_s = make_cube(f"canopy_post_s_{z}", 25, 16, z-1, 27, 42, z+1, 26, 29, z, 512, 0, 1024, 512)
        pillars.extend([p_p, p_s])

    # Decorative Arched Tram Brackets under roof beams
    brackets = []
    for z in [-42, -14, 14, 42]:
        b_p = make_cube(f"tram_arch_p_{z}", -26.5, 39, z-10, -25.5, 41.5, z+10, -26, 40, z, 512, 0, 1024, 512)
        b_s = make_cube(f"tram_arch_s_{z}", 25.5, 39, z-10, 26.5, 41.5, z+10, 26, 40, z, 512, 0, 1024, 512)
        brackets.extend([b_p, b_s])

    # Longitudinal white roof side beams
    r_beam_p = make_cube("roof_beam_port", -27, 41, -64, -25, 43, 64, -26, 42, 0, 512, 0, 1024, 512)
    r_beam_s = make_cube("roof_beam_starboard", 25, 41, -64, 27, 43, 64, 26, 42, 0, 512, 0, 1024, 512)

    # TWO-TIER MONITOR ROOF ("Dach ze świetlikiem / latarnią tramwajową")
    # Lower side roofs (slanted / flat translucent canopy)
    r_side_p = make_cube("roof_lower_port", -27, 42, -64, -13, 43, 64, -20, 42.5, 0, 512, 512, 1024, 1024)
    r_side_s = make_cube("roof_lower_starboard", 13, 42, -64, 27, 43, 64, 20, 42.5, 0, 512, 512, 1024, 1024)

    # Raised central monitor clerestory sides (vertical transom windows)
    r_mon_side_p = make_cube("monitor_clerestory_port", -13.5, 42, -64, -12.5, 47, 64, -13, 44.5, 0, 512, 512, 1024, 1024)
    r_mon_side_s = make_cube("monitor_clerestory_starboard", 12.5, 42, -64, 13.5, 47, 64, 13, 44.5, 0, 512, 512, 1024, 1024)

    # Raised monitor arched roof cap (Y = 47 to 49)
    r_mon_top = make_cube("monitor_roof_cap", -14, 47, -66, 14, 49, 66, 0, 48, 0, 512, 0, 1024, 512)

    # Front bow mast / navigation light
    bow_mast = make_cube("bow_light_mast", -0.75, 28, -78, 0.75, 42, -76.5, 0, 35, -77.25, 512, 0, 1024, 512)
    bow_lantern = make_cube("bow_lantern", -2, 40, -79, 2, 44, -75, 0, 42, -77, 512, 0, 1024, 512)

    # Stern flagpole with blue flag
    flagpole = make_cube("stern_flagpole", 5.25, 28, 75, 6.75, 52, 76.5, 6, 40, 75.75, 512, 0, 1024, 512)
    flag_cloth = make_cube("stern_flag_cloth", 6, 43, 76, 6.5, 51, 92, 6.25, 47, 84, 1536, 512, 2048, 1024)

    # Boarding ladder on port stern
    ladder_rail_1 = make_cube("ladder_rail_1", -12, 6, 76, -11, 28, 77.5, -11.5, 17, 76.75, 512, 0, 1024, 512)
    ladder_rail_2 = make_cube("ladder_rail_2", -8, 6, 76, -7, 28, 77.5, -7.5, 17, 76.75, 512, 0, 1024, 512)
    ladder_rung_1 = make_cube("ladder_rung_1", -11, 8, 76.5, -8, 9, 77.5, -9.5, 8.5, 77, 512, 0, 1024, 512)
    ladder_rung_2 = make_cube("ladder_rung_2", -11, 14, 76.5, -8, 15, 77.5, -9.5, 14.5, 77, 512, 0, 1024, 512)
    ladder_rung_3 = make_cube("ladder_rung_3", -11, 20, 76.5, -8, 21, 77.5, -9.5, 20.5, 77, 512, 0, 1024, 512)

    # Lifebuoy on starboard stern
    lifebuoy = make_cube("stern_lifebuoy", 16, 20, 75.5, 24, 28, 77, 20, 24, 76.25, 1536, 512, 2048, 1024)

    # 4 Hanging Blue Fenders
    fenders = []
    for fz in [-40, 40]:
        f_p = make_cube(f"fender_port_{fz}", -29.5, 17, fz-2.5, -27.5, 25, fz+2.5, -28.5, 21, fz, 1536, 512, 2048, 1024)
        f_s = make_cube(f"fender_starboard_{fz}", 27.5, 17, fz-2.5, 29.5, 25, fz+2.5, 28.5, 21, fz, 1536, 512, 2048, 1024)
        fenders.extend([f_p, f_s])

    superstructure_group = {
        "name": "superstructure",
        "origin": [0, 16, 0],
        "children": [
            bw_p_mid, bw_p_rail, bw_s_mid, bw_s_rail, bw_f_panel, bw_f_rail,
            bw_b_p_panel, bw_b_p_rail, bw_b_s_panel, bw_b_s_rail,
            *pillars, *brackets, r_beam_p, r_beam_s, r_side_p, r_side_s,
            r_mon_side_p, r_mon_side_s, r_mon_top,
            bow_mast, bow_lantern, flagpole, flag_cloth,
            ladder_rail_1, ladder_rail_2, ladder_rung_1, ladder_rung_2, ladder_rung_3,
            lifebuoy, *fenders
        ]
    }

    # 4. INTERIOR - BENCHES & HELM PEDESTAL
    # Port slatted wooden passenger bench (X = -25 .. -18, Z = -52 .. 52)
    bench_p_seat = make_cube("bench_port_seat", -25, 21, -52, -18, 22.5, 52, -21.5, 21.75, 0, 1024, 512, 1536, 1024)
    bench_p_back = make_cube("bench_port_backrest", -26, 22.5, -52, -24.5, 28, 52, -25.25, 25.25, 0, 1024, 512, 1536, 1024)
    bench_p_legs = make_cube("bench_port_legs", -24, 16, -48, -19, 21, 48, -21.5, 18.5, 0, 512, 0, 1024, 512)

    # Starboard slatted wooden passenger bench (X = 18 .. 25, Z = -52 .. 52)
    bench_s_seat = make_cube("bench_starboard_seat", 18, 21, -52, 25, 22.5, 52, 21.5, 21.75, 0, 1024, 512, 1536, 1024)
    bench_s_back = make_cube("bench_starboard_backrest", 24.5, 22.5, -52, 26, 28, 52, 25.25, 25.25, 0, 1024, 512, 1536, 1024)
    bench_s_legs = make_cube("bench_starboard_legs", 19, 16, -48, 24, 21, 48, 21.5, 18.5, 0, 512, 0, 1024, 512)

    # Steering Console / Pedestal (at Z = -10, centered)
    helm_pedestal = make_cube("helm_pedestal", -4, 16, -14, 4, 28, -6, 0, 22, -10, 1024, 512, 1536, 1024)
    helm_dash = make_cube("helm_dashboard", -4.5, 28, -14.5, 4.5, 30, -5.5, 0, 29, -10, 1024, 512, 1536, 1024)

    interior_group = {
        "name": "interior",
        "origin": [0, 16, 0],
        "children": [
            bench_p_seat, bench_p_back, bench_p_legs,
            bench_s_seat, bench_s_back, bench_s_legs,
            helm_pedestal, helm_dash
        ]
    }

    # 5. STEERING WHEEL ("Koło sterowe" - clickable, rotates around Z)
    # Pivot at (0, 30, -6)
    wheel_hub = make_cube("wheel_hub", -1.5, 28.5, -5.5, 1.5, 31.5, -4.5, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_rim_t = make_cube("wheel_rim_top", -4, 34, -5.2, 4, 35.5, -4.8, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_rim_b = make_cube("wheel_rim_bottom", -4, 24.5, -5.2, 4, 26, -4.8, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_rim_l = make_cube("wheel_rim_left", -5.5, 26, -5.2, -4, 34, -4.8, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_rim_r = make_cube("wheel_rim_right", 4, 26, -5.2, 5.5, 34, -4.8, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_spoke_v = make_cube("wheel_spoke_vertical", -0.75, 25, -5.3, 0.75, 35, -4.7, 0, 30, -5, 1024, 512, 1536, 1024)
    wheel_spoke_h = make_cube("wheel_spoke_horizontal", -5, 29.25, -5.3, 5, 30.75, -4.7, 0, 30, -5, 1024, 512, 1536, 1024)

    wheel_group = {
        "name": "wheel",
        "origin": [0, 30, -5],
        "children": [wheel_hub, wheel_rim_t, wheel_rim_b, wheel_rim_l, wheel_rim_r, wheel_spoke_v, wheel_spoke_h]
    }

    # 6. OUTBOARD MOTOR ("Zaburtowy silnik, wychylający się przy skręcie")
    # Swivel Pivot at transom: (0, 14, 80), pivots around Y-axis when steering!
    engine_cowl_main = make_cube("engine_cowl_main", -4, 18, 77, 4, 29, 87, 0, 14, 80, 0, 512, 512, 1024)
    engine_cowl_top = make_cube("engine_cowl_top", -3.5, 29, 78, 3.5, 31, 86, 0, 14, 80, 0, 512, 512, 1024)
    engine_bracket = make_cube("engine_swivel_bracket", -2.5, 12, 78, 2.5, 18, 83, 0, 14, 80, 0, 512, 512, 1024)
    engine_shaft = make_cube("engine_drive_shaft", -2, 4, 79, 2, 12, 85, 0, 14, 80, 0, 512, 512, 1024)
    engine_cav_plate = make_cube("engine_cavitation_plate", -4.5, 3.5, 78, 4.5, 4.5, 87, 0, 14, 80, 0, 512, 512, 1024)
    engine_torpedo = make_cube("engine_gearcase_torpedo", -2, 0.5, 77, 2, 3.5, 87, 0, 14, 80, 0, 512, 512, 1024)
    engine_skeg = make_cube("engine_skeg_fin", -0.5, -3, 80, 0.5, 0.5, 86, 0, 14, 80, 0, 512, 512, 1024)
    engine_tiller = make_cube("engine_steering_tiller", -1, 17, 75, 1, 18.5, 78, 0, 14, 80, 0, 512, 512, 1024)

    # Propeller (spins around Z axis at (0, 2, 77))
    prop_hub = make_cube("propeller_hub", -1, 1, 75.8, 1, 3, 77.2, 0, 2, 76.5, 0, 512, 512, 1024)
    prop_blade_1 = make_cube("propeller_blade_1", -0.5, 3, 76, 0.5, 6, 77, 0, 2, 76.5, 0, 512, 512, 1024)
    prop_blade_2 = make_cube("propeller_blade_2", 1.8, -1, 76, 4.3, 0.8, 77, 0, 2, 76.5, 0, 512, 512, 1024)
    prop_blade_3 = make_cube("propeller_blade_3", -4.3, -1, 76, -1.8, 0.8, 77, 0, 2, 76.5, 0, 512, 512, 1024)

    propeller_group = {
        "name": "propeller",
        "origin": [0, 2, 76.5],
        "children": [prop_hub, prop_blade_1, prop_blade_2, prop_blade_3]
    }

    outboard_motor_group = {
        "name": "outboard_motor",
        "origin": [0, 14, 80],
        "children": [
            engine_cowl_main, engine_cowl_top, engine_bracket, engine_shaft,
            engine_cav_plate, engine_torpedo, engine_skeg, engine_tiller,
            propeller_group
        ]
    }

    # ROOT HIERARCHY
    root_group = {
        "name": "root",
        "origin": [0, 0, 0],
        "children": [floats_group, deck_group, superstructure_group, interior_group, wheel_group, outboard_motor_group]
    }

    outliner.append(root_group)

    # ANIMATIONS
    animations = [
        {
            "name": "steer_left",
            "loop": "hold",
            "length": 1.0,
            "animators": {
                "wheel": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [0, 0, -45]
                    }
                },
                "outboard_motor": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [0, 28, 0]
                    }
                }
            }
        },
        {
            "name": "steer_right",
            "loop": "hold",
            "length": 1.0,
            "animators": {
                "wheel": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [0, 0, 45]
                    }
                },
                "outboard_motor": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [0, -28, 0]
                    }
                }
            }
        },
        {
            "name": "idle_motoring",
            "loop": "loop",
            "length": 1.0,
            "animators": {
                "root": {
                    "position": {
                        "0.0": [0, 0, 0],
                        "0.5": [0, 0.4, 0],
                        "1.0": [0, 0, 0]
                    },
                    "rotation": {
                        "0.0": [0, 0, -0.3],
                        "0.5": [0, 0, 0.3],
                        "1.0": [0, 0, -0.3]
                    }
                },
                "propeller": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "1.0": [0, 0, 720]
                    }
                }
            }
        }
    ]

    bbmodel = {
        "meta": {
            "format_version": "4.10",
            "model_format": "modded_entity",
            "box_uv": False
        },
        "name": "wressla",
        "model_identifier": "wressla",
        "visible_box": [3.5, 3.0, 10.0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_marker_length": 100,
        "resolution": {
            "width": 2048,
            "height": 2048
        },
        "elements": elements,
        "outliner": outliner,
        "textures": [
            {
                "path": "wressla.png",
                "name": "wressla.png",
                "folder": "",
                "namespace": "",
                "id": "0",
                "particle": False,
                "render_mode": "default",
                "visible": True,
                "mode": "bitmap",
                "source": img_b64
            }
        ],
        "animations": animations
    }

    return bbmodel, elements

def generate_obj(elements):
    """Generates an OBJ and MTL mesh export."""
    obj_lines = [
        "# Wressla - Wrocław Tram River Catamaran",
        "# Scale: 1 unit = 1/16 meter (Minecraft standard)",
        "mtllib wressla.mtl",
        "o Wressla",
        "s off"
    ]

    mtl_lines = [
        "# Wressla Material Library",
        "newmtl Material_Wressla",
        "Ka 1.000 1.000 1.000",
        "Kd 1.000 1.000 1.000",
        "Ks 0.200 0.200 0.200",
        "Ns 10.0",
        "d 1.0",
        "illum 2",
        "map_Kd wressla.png"
    ]

    v_idx = 1
    vt_idx = 1

    for elem in elements:
        fx, fy, fz = elem["from"]
        tx, ty, tz = elem["to"]
        name = elem["name"]

        obj_lines.append(f"\ng {name}")

        # 8 vertices of cube (convert Y to standard 3D)
        verts = [
            (fx, fy, fz),
            (tx, fy, fz),
            (tx, ty, fz),
            (fx, ty, fz),
            (fx, fy, tz),
            (tx, fy, tz),
            (tx, ty, tz),
            (fx, ty, tz)
        ]

        for vx, vy, vz in verts:
            obj_lines.append(f"v {vx/16.0:.4f} {vy/16.0:.4f} {vz/16.0:.4f}")

        # UV coordinates from 'faces'
        u1, v1, u2, v2 = elem["faces"]["north"]["uv"]
        # Normalize UVs to 0..1
        nu1 = u1 / 16.0
        nv1 = 1.0 - (v1 / 16.0)
        nu2 = u2 / 16.0
        nv2 = 1.0 - (v2 / 16.0)

        obj_lines.append(f"vt {nu1:.4f} {nv1:.4f}")
        obj_lines.append(f"vt {nu2:.4f} {nv1:.4f}")
        obj_lines.append(f"vt {nu2:.4f} {nv2:.4f}")
        obj_lines.append(f"vt {nu1:.4f} {nv2:.4f}")

        obj_lines.append("usemtl Material_Wressla")

        # 6 quad faces
        # North (Z-)
        obj_lines.append(f"f {v_idx} {v_idx+1} {v_idx+2} {v_idx+3}")
        # South (Z+)
        obj_lines.append(f"f {v_idx+5} {v_idx+4} {v_idx+7} {v_idx+6}")
        # West (X-)
        obj_lines.append(f"f {v_idx+4} {v_idx} {v_idx+3} {v_idx+7}")
        # East (X+)
        obj_lines.append(f"f {v_idx+1} {v_idx+5} {v_idx+6} {v_idx+2}")
        # Up (Y+)
        obj_lines.append(f"f {v_idx+3} {v_idx+2} {v_idx+6} {v_idx+7}")
        # Down (Y-)
        obj_lines.append(f"f {v_idx+4} {v_idx+5} {v_idx+1} {v_idx}")

        v_idx += 8
        vt_idx += 4

    return "\n".join(obj_lines), "\n".join(mtl_lines)

def generate_item_icon(atlas_img):
    """Creates a 64x64 item icon and 128x128 mod icon depicting the Wressla tram catamaran."""
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(icon)

    # Water wave background
    idraw.rectangle([(0, 80), (127, 127)], fill=(35, 95, 140, 255))
    idraw.line([(0, 80), (127, 80)], fill=(75, 160, 210, 255), width=2)

    # Twin black floats
    idraw.rectangle([(12, 82), (116, 92)], fill=(25, 28, 32, 255))

    # Teak deck
    idraw.rectangle([(8, 76), (120, 82)], fill=(180, 135, 80, 255))

    # Red tram bulwark
    idraw.rectangle([(10, 58), (118, 76)], fill=(140, 24, 32, 255))
    idraw.rectangle([(10, 58), (118, 76)], outline=(245, 247, 248, 255), width=2)

    # Wressla text / white lines
    idraw.line([(32, 67), (96, 67)], fill=(255, 255, 255, 255), width=3)

    # Canopy pillars
    for px in [16, 40, 64, 88, 112]:
        idraw.line([(px, 32), (px, 58)], fill=(240, 244, 246, 255), width=2)

    # Clerestory monitor roof
    idraw.rectangle([(12, 30), (116, 34)], fill=(80, 150, 180, 180)) # lower glass
    idraw.rectangle([(28, 22), (100, 30)], fill=(245, 247, 248, 255)) # raised monitor

    # Outboard motor at stern (right side)
    idraw.rectangle([(114, 68), (124, 86)], fill=(18, 20, 22, 255))
    idraw.line([(114, 73), (124, 73)], fill=(220, 28, 36, 255), width=2) # red stripe

    # Item icon (scaled to 64x64)
    item_icon = icon.resize((64, 64), Image.Resampling.LANCZOS)
    return icon, item_icon

if __name__ == "__main__":
    import io
    print("Generating Wressla assets...")
    atlas = create_texture()

    # Destination directories: both in mod assets and in wressla export folder
    dest_dirs = [
        ROOT / "wressla",
        ROOT / "src/main/resources/assets/peterwolfs_rivers/textures/entity",
        ROOT / "src/main/resources/assets/peterwolfs_rivers/textures/item",
        ROOT / "src/main/resources/assets/peterwolfs_rivers"
    ]
    for d in dest_dirs:
        d.mkdir(parents=True, exist_ok=True)

    # 1. Texture Atlas
    atlas_path = ROOT / "wressla/wressla.png"
    atlas.save(atlas_path)
    atlas.save(ROOT / "src/main/resources/assets/peterwolfs_rivers/textures/entity/wressla.png")
    print(f"Texture atlas saved to {atlas_path}")

    # 2. Blockbench Model (.bbmodel)
    bbmodel, elements = generate_bbmodel(atlas)
    bb_path = ROOT / "wressla/wressla.bbmodel"
    with open(bb_path, "w", encoding="utf-8") as f:
        json.dump(bbmodel, f, indent=2)
    print(f"Blockbench model saved to {bb_path}")

    # 3. OBJ & MTL mesh export
    obj_str, mtl_str = generate_obj(elements)
    obj_path = ROOT / "wressla/wressla.obj"
    mtl_path = ROOT / "wressla/wressla.mtl"
    with open(obj_path, "w", encoding="utf-8") as f:
        f.write(obj_str)
    with open(mtl_path, "w", encoding="utf-8") as f:
        f.write(mtl_str)
    print(f"OBJ model saved to {obj_path}")

    # 4. Mod icon and Item icon
    mod_icon, item_icon = generate_item_icon(atlas)
    mod_icon.save(ROOT / "wressla/icon.png")
    mod_icon.save(ROOT / "src/main/resources/assets/peterwolfs_rivers/icon.png")
    item_icon.save(ROOT / "wressla/wressla_item.png")
    item_icon.save(ROOT / "src/main/resources/assets/peterwolfs_rivers/textures/item/wressla.png")
    print("Item icon and mod icon generated successfully.")
