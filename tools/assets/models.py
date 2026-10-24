"""Geometry of the TEL (transporter-erector-launcher), the missile and the re-entry vehicle.

All dimensions in metres. The TEL is modelled on an 8-axle wheeled chassis with the transport-launch canister
(TPK) on top, hinged at the rear; raising it 90 degrees stands the canister on its base plate behind the vehicle.
Numbers shared with the Java side are exported to LauncherGeometry.java by gen_assets.py.
"""
import math

import numpy as np

from mesh import Material, Part, box, cylinder, face, lathe, translate, rot_x, rot_y, rot_z, align_y_to, scale

# ------------------------------------------------------------------ shared geometry
G = dict(
    LENGTH_FRONT=12.0, LENGTH_REAR=-13.0, HALF_WIDTH=1.7,
    WHEEL_R=0.82, WHEEL_W=0.6, WHEEL_X=1.40,
    AXLES=[11.0, 8.8, 6.6, 4.4, -1.6, -3.8, -6.0, -8.2],
    FRAME_Y0=1.10, FRAME_Y1=1.62,
    TPK_R=1.12, TPK_Y=3.55, TPK_Z0=-13.0, TPK_Z1=9.2,
    HINGE_Y=2.30, HINGE_Z=-11.6,
    RAM_A=(0.95, 1.70, -8.0), RAM_B=(0.95, 2.45, -5.0),
    JACKS=[(1.92, 2.2), (-1.92, 2.2), (1.92, -10.0), (-1.92, -10.0)],
    JACK_REST_Y=0.55, JACK_TOP_Y=1.75,
    MISSILE_BASE_Z=-12.8,
    CAB_Z0=9.4, CAB_Z1=12.0, CAB_Y0=1.62, CAB_Y1=3.30,
)

# launcher atlas materials (4x4 tiles of 512 px)
L = dict(
    paint=Material('paint_a', 0, 0, 2.0),
    paint_b=Material('paint_b', 1, 0, 2.0),
    dark=Material('paint_dark', 2, 0, 2.0),
    metal=Material('metal', 3, 0, 1.0),
    chrome=Material('chrome', 0, 1, 1.0),
    tread=Material('tread', 1, 1, 1.0),
    side=Material('sidewall', 2, 1, 0.9),
    grille=Material('grille', 3, 1, 1.0),
    glass=Material('glass', 0, 2, 2.0),
    tpk=Material('container', 1, 2, 2.0),
    rubber=Material('rubber', 2, 2, 1.0),
    stripes=Material('stripes', 3, 2, 1.0),
    cabfront=Material('cab_front', 0, 3, 99, unique=True),
    marks=Material('markings', 1, 3, 99, unique=True),
    lights=Material('lights', 2, 3, 99, unique=True),
    cap=Material('cap', 3, 3, 2.4),
)
L['lights'].emissive = True

# missile atlas materials
M = dict(
    body=Material('m_paint', 0, 0, 2.0),
    body_b=Material('m_paint_b', 1, 0, 2.0),
    dark=Material('m_dark', 2, 0, 2.0),
    metal=Material('m_metal', 3, 0, 1.0),
    nozzle_in=Material('nozzle_in', 0, 1, 1.0),
    nozzle_out=Material('nozzle_out', 1, 1, 1.0),
    grid=Material('grid_fin', 2, 1, 1.0),
    insul=Material('insulation', 3, 1, 1.0),
    heat=Material('heatshield', 0, 2, 1.2),
    heat_b=Material('heatshield_b', 1, 2, 1.2),
    marks=Material('m_marks', 2, 2, 99, unique=True),
    band=Material('band', 3, 2, 1.0),
    white=Material('m_white', 0, 3, 2.0),
)


def sides_for(r, lod):
    base = 10 + int(r * 22)
    return max(8, base if lod == 0 else base // 2)


# ------------------------------------------------------------------ TEL
def wheel(part, x, z, lod, left):
    r, w = G['WHEEL_R'], G['WHEEL_W']
    sx = 1 if left else -1
    n = 28 if lod == 0 else 12
    # tyre: rounded profile revolved around X
    prof = [(0.50, -w / 2), (0.70, -w / 2), (r - 0.05, -w / 2 + 0.03), (r, -w / 2 + 0.10), (r, w / 2 - 0.10),
            (r - 0.05, w / 2 - 0.03), (0.70, w / 2), (0.50, w / 2)]
    start = len(part.pos)
    lathe(part, prof, n, L['tread'], smooth=True)
    part.transform(translate(x, r, z) @ rot_z(-math.pi / 2 * sx), start)
    # rim / hub disk on the outer face
    start = len(part.pos)
    hub = [(0.0, 0.0), (0.50, 0.0)]
    lathe(part, [(0.50, 0.0), (0.44, 0.04), (0.30, 0.06), (0.18, 0.10), (0.08, 0.14), (0.0, 0.15)], n, L['side'], smooth=False)
    part.transform(translate(x + sx * (w / 2 - 0.02), r, z) @ rot_z(-math.pi / 2 * sx), start)
    if lod == 0:
        # wheel nuts
        for k in range(6):
            a = k / 6 * 2 * math.pi
            cy, cz = r + 0.22 * math.cos(a), z + 0.22 * math.sin(a)
            box(part, (x + sx * (w / 2 + 0.06) - 0.025, cy - 0.025, cz - 0.025), (x + sx * (w / 2 + 0.06) + 0.025, cy + 0.025, cz + 0.025), L['chrome'], 0.8)


def tel(lod=0):
    parts = {}
    body = Part('body')
    wheels = Part('wheels')
    erector = Part('erector', pivot=(0, G['HINGE_Y'], G['HINGE_Z']))
    cap = Part('cap', pivot=(0, G['TPK_Y'], G['TPK_Z1'] + 0.25))
    beacons = Part('beacons')
    beacons.emissive = True
    lamps = Part('lamps')
    lamps.emissive = True
    hw = G['HALF_WIDTH']
    fy0, fy1 = G['FRAME_Y0'], G['FRAME_Y1']
    P, PB, D, MT = L['paint'], L['paint_b'], L['dark'], L['metal']

    # --- frame rails and cross members
    for sx in (1, -1):
        box(body, (sx * 0.42 - 0.13, fy0, -11.8), (sx * 0.42 + 0.13, fy1, 11.6), D, 0.8)
    for z in np.arange(-11.0, 11.0, 2.2):
        box(body, (-0.42, fy0 + 0.08, z - 0.08), (0.42, fy1 - 0.08, z + 0.08), D, 0.7)
    # --- deck plates and side equipment boxes between the axle groups
    box(body, (-hw + 0.05, fy1, -11.4), (hw - 0.05, fy1 + 0.06, 7.0), PB, 0.95)
    for (z0, z1) in ((-0.5, 3.4), (-11.4, -9.4)):
        for sx in (1, -1):
            x0, x1 = (0.6, hw) if sx > 0 else (-hw, -0.6)
            box(body, (x0, 0.85, z0), (x1, fy1, z1), P)
            if lod == 0:
                # doors: slightly proud panels with handles
                for zz in np.arange(z0 + 0.15, z1 - 0.6, 1.25):
                    xf = x1 + 0.012 if sx > 0 else x0 - 0.012
                    face(body, (xf, 0.95, zz + (1.05 if sx > 0 else 0)), (0, 0, -1 if sx > 0 else 1), (0, 1, 0), 1.05, 0.6, PB, 0.97)
                    box(body, (xf - 0.02, 1.25, zz + 0.45), (xf + 0.02 * sx + 0.02, 1.29, zz + 0.62), L['chrome'], 0.8)
    # fuel tanks (cylinders) on the sides
    for sx in (1, -1):
        cylinder(body, (sx * 1.25, 1.12, -1.2), (sx * 1.25, 1.12, 3.0) if False else (sx * 1.25, 1.12, 0.6), 0.32, sides_for(0.32, lod), MT)
    # --- fenders over every wheel
    for z in G['AXLES']:
        for sx in (1, -1):
            x0, x1 = (G['WHEEL_X'] - 0.36, hw + 0.04) if sx > 0 else (-hw - 0.04, -G['WHEEL_X'] + 0.36)
            arc_n = 8 if lod == 0 else 4
            prev = None
            for k in range(arc_n + 1):
                a = math.pi * k / arc_n
                pz = z + math.cos(a) * 1.02
                py = G['WHEEL_R'] + math.sin(a) * 1.02
                if prev is not None:
                    lo = (x0, min(py, prev[1]) - 0.02, min(pz, prev[0]))
                    hi = (x1, max(py, prev[1]) + 0.05, max(pz, prev[0]))
                    box(body, lo, hi, D, 0.85, faces='xXYzZ')
                prev = (pz, py)
    # --- wheels and axles
    for z in G['AXLES']:
        for sx in (1, -1):
            wheel(wheels, sx * G['WHEEL_X'], z, lod, sx > 0)
        cylinder(body, (-1.1, G['WHEEL_R'], z), (1.1, G['WHEEL_R'], z), 0.11, 10, MT)
        box(body, (-0.35, G['WHEEL_R'] - 0.2, z - 0.3), (0.35, G['WHEEL_R'] + 0.15, z + 0.3), D, 0.6)

    # --- cab
    cz0, cz1, cy0, cy1 = G['CAB_Z0'], G['CAB_Z1'], G['CAB_Y0'], G['CAB_Y1']
    box(body, (-hw, cy0, cz0), (hw, cy1 - 0.55, cz1 - 0.35), P, faces='xXyz')
    # upper cab with slanted windshield
    slant = 0.35
    wy0 = cy1 - 0.95
    box(body, (-hw, wy0, cz0), (hw, cy1, cz1 - 0.35 - slant), P, faces='xXYz')
    # windshield (slanted face) and front lower face
    top_front = cz1 - 0.35 - slant
    face(body, (hw, wy0, cz1 - 0.35), (-1, 0, 0), np.array([0, 0.95, -slant]) / math.hypot(0.95, slant), 2 * hw, math.hypot(0.95, slant), L['glass'], 1.0, flip=True)
    face(body, (-hw, cy0, cz1 - 0.35), (1, 0, 0), (0, 1, 0), 2 * hw, wy0 - cy0, L['cabfront'], 1.0)
    # side windows
    for sx in (1, -1):
        xf = sx * (hw + 0.006)
        face(body, (xf, wy0 + 0.12, cz1 - 0.55 if sx > 0 else cz0 + 0.25), (0, 0, -1 if sx > 0 else 1), (0, 1, 0), 1.7, 0.66, L['glass'], 1.0)
    # bumper, steps, mirrors, roof gear
    box(body, (-hw - 0.05, 0.95, cz1 - 0.35), (hw + 0.05, 1.42, cz1 + 0.05), D, 0.75)
    for sx in (1, -1):
        box(body, (sx * (hw - 0.1) - 0.3, 1.05, cz1 - 0.1), (sx * (hw - 0.1) + 0.3, 1.1, cz1 + 0.1), L['metal'], 0.8) if False else None
        # mirrors on arms
        mx = sx * (hw + 0.35)
        box(body, (min(sx * hw, mx) , cy1 - 0.65, cz1 - 0.55), (max(sx * hw, mx), cy1 - 0.6, cz1 - 0.5), MT, 0.8)
        box(body, (mx - 0.06, cy1 - 1.0, cz1 - 0.58), (mx + 0.06, cy1 - 0.5, cz1 - 0.47), D, 0.8)
        # grab handles / steps under the doors
        box(body, (sx * hw - 0.25 * sx - 0.25, 0.95, cz0 + 0.6), (sx * hw - 0.25 * sx + 0.25, 1.0, cz0 + 1.2), MT, 0.7) if lod == 0 else None
    box(body, (-0.9, cy1, cz0 + 0.3), (0.9, cy1 + 0.12, cz0 + 1.3), D, 0.9)  # roof box
    if lod == 0:
        cylinder(body, (0.9, cy1, cz0 + 0.5), (0.9, cy1 + 2.1, cz0 + 0.5), 0.015, 6, MT, caps=False)  # antenna
        cylinder(body, (-0.9, cy1, cz0 + 0.5), (-0.9, cy1 + 1.4, cz0 + 0.5), 0.015, 6, MT, caps=False)
    # headlights and marker lamps (emissive)
    lt = L['lights']
    for sx in (1, -1):
        face(lamps, (sx * 1.25 - 0.18, 1.02, cz1 + 0.056), (1, 0, 0), (0, 1, 0), 0.36, 0.22, lt.sub(0.0, 0.0, 0.5, 0.3))
        face(lamps, (sx * 1.25 - 0.18, cy1 - 0.12, cz1 - 0.35 - slant - 0.002), (1, 0, 0), (0, 1, 0), 0.36, 0.08, lt.sub(0.5, 0.0, 1.0, 0.15))
    # beacons on the cab roof (blink during the launch sequence)
    for sx in (1, -1):
        cylinder(beacons, (sx * 1.3, cy1, cz0 + 0.4), (sx * 1.3, cy1 + 0.18, cz0 + 0.4), 0.09, 12, lt.sub(0.5, 0.5, 1.0, 1.0))

    # --- engine compartment behind the cab, louvres
    box(body, (-1.6, fy1, 7.0), (1.6, 2.25, cz0), P, faces='xXYz')
    if lod == 0:
        for sx in (1, -1):
            face(body, (sx * 1.605, 1.75, cz0 - 0.2 if sx > 0 else 7.2), (0, 0, -1 if sx > 0 else 1), (0, 1, 0), 2.0, 0.4, L['grille'], 1.0)
    for sx in (1, -1):
        cylinder(body, (sx * 1.35, 2.25, 7.4), (sx * 1.35, 3.1, 7.4), 0.09, 12, MT)  # exhaust stacks
        cylinder(body, (sx * 1.35, 3.1, 7.4), (sx * 1.35, 3.2, 7.25), 0.09, 12, MT)

    # --- cradle supports for the canister, hinge brackets, ram brackets, jack housings
    for z in (6.4, -2.6, -9.0):
        box(body, (-1.25, fy1, z - 0.18), (1.25, 2.25, z + 0.18), D, 0.85)
        for sx in (1, -1):
            box(body, (sx * 0.9 - 0.12, 2.25, z - 0.18), (sx * 0.9 + 0.12, 2.6, z + 0.18), D, 0.85)
    for sx in (1, -1):
        box(body, (sx * 0.62 - 0.12, fy1, G['HINGE_Z'] - 0.35), (sx * 0.62 + 0.12, G['HINGE_Y'] + 0.2, G['HINGE_Z'] + 0.35), D, 0.8)
    cylinder(body, (-0.9, G['HINGE_Y'], G['HINGE_Z']), (0.9, G['HINGE_Y'], G['HINGE_Z']), 0.13, 14, MT)
    ax, ay, az = G['RAM_A']
    for sx in (1, -1):
        box(body, (sx * ax - 0.14, fy1, az - 0.25), (sx * ax + 0.14, ay + 0.15, az + 0.25), D, 0.8)
    for (jx, jz) in G['JACKS']:
        cylinder(body, (jx, G['JACK_TOP_Y'] - 1.0, jz), (jx, G['JACK_TOP_Y'], jz), 0.17, 14, MT)
        box(body, (min(jx, 0) if jx < 0 else 1.0, 1.3, jz - 0.15), (max(jx, 0) if jx > 0 else -1.0, 1.55, jz + 0.15), D, 0.8)
    # rear lights, warning stripes on the rear frame
    box(body, (-hw, 0.9, -11.85), (hw, 1.3, -11.6), L['stripes'], 1.0, faces='xXYyz')

    # --- jacks (moving pistons with feet)
    jacks = []
    for i, (jx, jz) in enumerate(G['JACKS']):
        j = Part(f'jack{i}', pivot=(jx, G['JACK_REST_Y'], jz))
        cylinder(j, (jx, G['JACK_REST_Y'] + 0.05, jz), (jx, G['JACK_TOP_Y'] - 0.2, jz), 0.12, 14, L['chrome'])
        box(j, (jx - 0.32, G['JACK_REST_Y'] - 0.08, jz - 0.32), (jx + 0.32, G['JACK_REST_Y'] + 0.05, jz + 0.32), D, 0.8)
        jacks.append(j)

    # --- erector: canister, ribs, rings, rear end, cradle beams, base plate
    R, Y, Z0, Z1 = G['TPK_R'], G['TPK_Y'], G['TPK_Z0'], G['TPK_Z1']
    tpk_len = Z1 - Z0
    n = sides_for(R, lod)
    start = len(erector.pos)
    lathe(erector, [(R, 0.0), (R, tpk_len)], n, L['tpk'])
    erector.transform(translate(0, Y, Z0) @ rot_x(math.pi / 2), start)
    # inner wall (seen through the open mouth after the cap is blown)
    start = len(erector.pos)
    lathe(erector, [(R - 0.06, tpk_len), (R - 0.06, tpk_len - 3.0)], n, L['dark'], shade=0.35)
    erector.transform(translate(0, Y, Z0) @ rot_x(math.pi / 2), start)
    # mouth ring
    start = len(erector.pos)
    lathe(erector, [(R - 0.06, tpk_len), (R + 0.1, tpk_len), (R + 0.1, tpk_len - 0.2), (R, tpk_len - 0.2)], n, L['metal'], smooth=False)
    erector.transform(translate(0, Y, Z0) @ rot_x(math.pi / 2), start)
    # circumferential rings
    for zz in np.arange(Z0 + 0.6, Z1 - 0.3, 2.4):
        start = len(erector.pos)
        lathe(erector, [(R, 0), (R + 0.07, 0.02), (R + 0.07, 0.14), (R, 0.16)], n, L['paint_b'], smooth=False)
        erector.transform(translate(0, Y, zz) @ rot_x(math.pi / 2), start)
    # longitudinal ribs (8)
    if True:
        for k in range(8):
            a = (k + 0.5) / 8 * 2 * math.pi
            cx, cy = math.cos(a) * (R + 0.04), math.sin(a) * (R + 0.04)
            start = len(erector.pos)
            box(erector, (-0.05, -0.04, Z0 + 0.3), (0.05, 0.06, Z1 - 0.3), L['paint_b'], 0.95)
            erector.transform(translate(cx, Y + cy, 0) @ rot_z(a - math.pi / 2), start)
    # rear end cap (powder pressure accumulator housing)
    start = len(erector.pos)
    lathe(erector, [(0.0, -0.55), (0.45, -0.55), (0.6, -0.35), (R + 0.12, -0.15), (R + 0.12, 0.25), (R, 0.3)], n, L['metal'], smooth=False)
    erector.transform(translate(0, Y, Z0) @ rot_x(math.pi / 2), start)
    # cradle beams under the canister and the ram attachment brackets
    bx, by, bz = G['RAM_B']
    for sx in (1, -1):
        box(erector, (sx * 0.78 - 0.12, Y - R - 0.25, Z0 + 0.4), (sx * 0.78 + 0.12, Y - R + 0.15, Z1 - 1.0), D, 0.8)
        box(erector, (sx * bx - 0.16, by - 0.2, bz - 0.35), (sx * bx + 0.16, Y - R, bz + 0.35), D, 0.8)
    for zz in np.arange(Z0 + 1.0, Z1 - 1.0, 3.0):
        box(erector, (-0.9, Y - R - 0.2, zz - 0.12), (0.9, Y - R + 0.05, zz + 0.12), D, 0.75)
    # base plate that meets the ground when vertical
    box(erector, (-1.25, Y - 0.6, Z0 - 0.75), (1.25, Y + 0.6, Z0 - 0.55), D, 0.8)
    # hinge eyes
    for sx in (1, -1):
        box(erector, (sx * 0.85 - 0.1, G['HINGE_Y'] - 0.2, G['HINGE_Z'] - 0.25), (sx * 0.85 + 0.1, Y - R + 0.1, G['HINGE_Z'] + 0.25), D, 0.8)
    # stencils along the canister
    mk = L['marks']
    for sx in (1, -1):
        xx = sx * (R + 0.012)
        face(erector, (xx, Y - 0.25, Z1 - 1.2 if sx > 0 else Z1 - 4.2), (0, 0, -1 if sx > 0 else 1), (0, 1, 0), 3.0, 0.5, mk.sub(0.0, 0.0, 1.0, 0.25))
        face(erector, (xx, Y - 0.25, Z0 + 6.0 if sx > 0 else Z0 + 3.0), (0, 0, -1 if sx > 0 else 1), (0, 1, 0), 3.0, 0.5, mk.sub(0.0, 0.25, 1.0, 0.5))

    # --- front cap of the canister (blown off before launch)
    start = len(cap.pos)
    lathe(cap, [(R + 0.12, 0.0), (R + 0.12, 0.12), (R * 0.85, 0.3), (R * 0.45, 0.42), (0.0, 0.45)], n, L['cap'], smooth=True)
    cap.transform(translate(0, Y, Z1) @ rot_x(math.pi / 2), start)

    # --- ram units (unit cylinders, scaled and placed at runtime)
    ram_rod = Part('ram_rod')
    cylinder(ram_rod, (0, 0, 0), (0, 1, 0), 1.0, 16, L['chrome'], caps=True)
    ram_tube = Part('ram_tube')
    cylinder(ram_tube, (0, 0, 0), (0, 1, 0), 1.0, 16, L['metal'], caps=True)

    parts = [body, wheels, erector, cap, beacons, lamps, ram_rod, ram_tube] + jacks
    shade_ao(parts, ground_y=0.0)
    anchors = {
        'hinge': (0, G['HINGE_Y'], G['HINGE_Z']),
        'ram_a': G['RAM_A'], 'ram_b': G['RAM_B'],
        'mouth': (0, Y, Z1), 'missile_base': (0, Y, G['MISSILE_BASE_Z']),
    }
    return parts, anchors


def shade_ao(parts, ground_y=0.0):
    """cheap ambient occlusion in the vertex shade: dark under the vehicle, darker toward the ground"""
    for p in parts:
        for i, (x, y, z) in enumerate(p.pos):
            n = p.nrm[i]
            occl = 1.0
            under = y < G['FRAME_Y1'] + 0.05 and abs(x) < G['HALF_WIDTH'] - 0.25
            if under:
                occl *= 0.55 + 0.45 * min(1.0, max(0.0, (y - 0.3) / 1.3))
            if n[1] < -0.5:
                occl *= 0.75
            occl *= 0.8 + 0.2 * min(1.0, max(0.0, (y - ground_y) / 1.6))
            p.col[i] = max(0.05, min(1.0, p.col[i] * occl))


# ------------------------------------------------------------------ missile
MS = dict(S1_TOP=8.4, I12_TOP=9.2, S2_TOP=13.8, I23_TOP=14.2, S3_TOP=17.3, NOSE_TOP=21.4,
          R1=0.93, R2=0.805, R3=0.79)


def missile(lod=0):
    s1, i12, s2, s2n, i23, s3, s3n, nose, tray = (Part(n) for n in
                                                  ('s1', 'inter12', 's2', 's2_nozzle', 'inter23', 's3', 's3_nozzle', 'nose', 'tray'))
    n1 = sides_for(MS['R1'], lod)
    # stage 1 body with aft skirt
    lathe(s1, [(MS['R1'] + 0.04, 0.0), (MS['R1'] + 0.04, 0.9), (MS['R1'], 1.0), (MS['R1'], MS['S1_TOP'])], n1, M['body'])
    lathe(s1, [(0.0, 0.02), (MS['R1'] + 0.04, 0.0)], n1, M['dark'], shade=0.5)
    # nozzle (bell) under stage 1
    lathe(s1, [(0.62, -0.75), (0.5, -0.45), (0.36, -0.15), (0.32, 0.02)], n1, M['nozzle_out'], smooth=True)
    lathe(s1, [(0.30, 0.0), (0.34, -0.15), (0.48, -0.45), (0.59, -0.74)], n1, M['nozzle_in'], smooth=True, shade=0.6)
    # bands, cable tunnel
    for yy in (2.2, 4.6, 7.0):
        lathe(s1, [(MS['R1'], yy), (MS['R1'] + 0.015, yy + 0.01), (MS['R1'] + 0.015, yy + 0.18), (MS['R1'], yy + 0.19)], n1, M['band'], smooth=False)
    box(s1, (-0.08, 0.9, MS['R1'] - 0.02), (0.08, MS['S1_TOP'] - 0.2, MS['R1'] + 0.09), M['body_b'], 0.9)
    # Independent fins: hidden inside the canister, unfolded after ejection.
    fins = []
    for k in range(4):
        a = k / 4 * 2 * math.pi + math.pi / 4
        start = len(s1.pos)
        fin = Part('fin' + str(k))
        # frame
        w, h, d = 0.9, 0.75, 0.12
        box(fin, (0, 0, -d / 2), (w, 0.04, d / 2), M['metal'])
        box(fin, (0, h - 0.04, -d / 2), (w, h, d / 2), M['metal'])
        box(fin, (0, 0, -d / 2), (0.04, h, d / 2), M['metal'])
        box(fin, (w - 0.04, 0, -d / 2), (w, h, d / 2), M['metal'])
        if lod == 0:
            cells = 5
            for c in range(1, cells):
                t = c / cells
                # diagonal lattice approximated by a crossed grid
                box(fin, (w * t - 0.012, 0, -d / 2), (w * t + 0.012, h, d / 2), M['grid'])
                box(fin, (0, h * t - 0.012, -d / 2), (w, h * t + 0.012, d / 2), M['grid'])
        fin.transform(rot_y(-a) @ translate(MS['R1'] + 0.03, 0.15, 0))
        # small stabiliser at 45 deg offset
        st = Part('tmp')
        box(st, (0, 0, -0.025), (0.45, 0.9, 0.025), M['body_b'])
        st.transform(rot_y(-(a + math.pi / 4)) @ translate(MS['R1'], 0.05, 0))
        fin.merge(st)
        fins.append(fin)
    # interstage 1-2
    lathe(i12, [(MS['R1'], MS['S1_TOP']), (MS['R2'], MS['I12_TOP'])], n1, M['dark'])
    # stage 2
    n2 = sides_for(MS['R2'], lod)
    lathe(s2, [(MS['R2'], MS['I12_TOP']), (MS['R2'], MS['S2_TOP'])], n2, M['body'])
    lathe(s2, [(0.0, MS['I12_TOP'] + 0.01), (MS['R2'], MS['I12_TOP'])], n2, M['insul'], shade=0.6)
    for yy in (10.6, 12.4):
        lathe(s2, [(MS['R2'], yy), (MS['R2'] + 0.012, yy + 0.01), (MS['R2'] + 0.012, yy + 0.15), (MS['R2'], yy + 0.16)], n2, M['band'], smooth=False)
    lathe(s2n, [(0.5, MS['I12_TOP'] - 0.85), (0.38, MS['I12_TOP'] - 0.45), (0.27, MS['I12_TOP'] - 0.1), (0.25, MS['I12_TOP'])], n2, M['nozzle_out'])
    lathe(s2n, [(0.23, MS['I12_TOP']), (0.26, MS['I12_TOP'] - 0.1), (0.36, MS['I12_TOP'] - 0.45), (0.48, MS['I12_TOP'] - 0.84)], n2, M['nozzle_in'], shade=0.6)
    # interstage 2-3
    lathe(i23, [(MS['R2'], MS['S2_TOP']), (MS['R3'], MS['I23_TOP'])], n2, M['dark'])
    # stage 3
    lathe(s3, [(MS['R3'], MS['I23_TOP']), (MS['R3'], MS['S3_TOP'])], n2, M['body_b'])
    lathe(s3, [(0.0, MS['I23_TOP'] + 0.01), (MS['R3'], MS['I23_TOP'])], n2, M['insul'], shade=0.6)
    lathe(s3n, [(0.42, MS['I23_TOP'] - 0.75), (0.3, MS['I23_TOP'] - 0.35), (0.22, MS['I23_TOP'])], n2, M['nozzle_out'])
    lathe(s3n, [(0.2, MS['I23_TOP']), (0.28, MS['I23_TOP'] - 0.35), (0.4, MS['I23_TOP'] - 0.74)], n2, M['nozzle_in'], shade=0.6)
    # nose fairing: tangent ogive
    prof = []
    Ln = MS['NOSE_TOP'] - MS['S3_TOP']
    Rn = MS['R3']
    rho = (Rn * Rn + Ln * Ln) / (2 * Rn)
    steps = 16 if lod == 0 else 8
    for k in range(steps + 1):
        x = Ln * k / steps
        r = math.sqrt(max(0.0, rho * rho - x * x)) + Rn - rho
        prof.append((max(0.0, r), MS['S3_TOP'] + x))
    prof[-1] = (0.0, MS['NOSE_TOP'])
    lathe(nose, prof, n2, M['white'])
    # stencils on stage 1
    for k in range(2):
        a = k * math.pi
        dec = Part('tmp')
        face(dec, (0.5, 3.0, 0), (-1, 0, 0), (0, 1, 0), 1.0, 2.6, M['marks'].sub(0.0, 0.0, 0.25, 1.0))
        dec.transform(rot_y(-a) @ translate(0, 0, MS['R1'] + 0.006))
        s1.merge(dec)
    # pallet under the missile (falls off when it leaves the canister)
    lathe(tray, [(0.0, -0.95), (MS['R1'] + 0.12, -0.95), (MS['R1'] + 0.12, -0.75), (0.0, -0.75)], n1, M['metal'], smooth=False)
    parts = [s1, i12, s2, s2n, i23, s3, s3n, nose, tray] + fins
    anchors = {'s1_base': (0, 0, 0), 's2_base': (0, MS['I12_TOP'], 0), 's3_base': (0, MS['I23_TOP'], 0),
               'nose_base': (0, MS['S3_TOP'], 0), 'top': (0, MS['NOSE_TOP'], 0),
               'n1': (0, -0.75, 0), 'n2': (0, MS['I12_TOP'] - 0.85, 0), 'n3': (0, MS['I23_TOP'] - 0.75, 0)}
    return parts, anchors


def reentry_vehicle(lod=0):
    rv = Part('rv')
    n = 28 if lod == 0 else 14
    Lc, Rb = 2.3, 0.475
    prof = [(0.0, 0.0), (Rb * 0.9, 0.0), (Rb, 0.06)]
    for k in range(1, 9):
        y = 0.06 + (Lc - 0.12) * k / 8
        prof.append((Rb - (Rb - 0.06) * (y - 0.06) / (Lc - 0.12), y))
    prof += [(0.035, Lc - 0.02), (0.0, Lc)]
    lathe(rv, prof, n, M['heat'])
    return [rv], {'base': (0, 0, 0), 'tip': (0, Lc, 0)}
