"""Original compact single-shot game props. No dimensions or parts from real weapon engineering."""
import math
import numpy as np
from assets.mesh import *

L={name:Material(name,col,row,tile) for name,col,row,tile in [
    ('paint',0,0,1.2),('paint_b',1,0,1.2),('dark',2,0,1),('metal',3,0,.5),
    ('chrome',0,1,.5),('rubber',2,2,.7),('stripes',3,2,.6),('glass',0,2,.6)]}
L['marks']=Material('marks',1,3,99,unique=True)
L['lights']=Material('lights',2,3,99,unique=True)
M={name:Material(name,col,row,tile) for name,col,row,tile in [
    ('body',0,0,1.2),('dark',2,0,.6),('metal',3,0,.4),('nozzle',1,1,.4),('white',0,3,1)]}

def silo(lod=0):
    body=Part('body');cap=Part('cap');lamps=Part('lamps');lamps.emissive=True
    n=32 if lod==0 else 16
    # Fold-out silhouette is replaced by an already-deployed, field-serviceable container.
    box(body,(-.63,.08,-.63),(.63,.32,.63),L['dark'])
    cylinder(body,(0,.3,0),(0,3.5,0),.48,n,L['paint'],caps=False)
    # Inner mouth is open, with no disk covering the missile.
    lathe(body,[(.43,3.54),(.43,3.2)],n,L['dark'],shade=.4)
    for y in [.36,1.05,2.05,3.43]:
        lathe(body,[(.48,y),(.54,y+.02),(.54,y+.10),(.48,y+.12)],n,L['metal'],smooth=False)
    for i in range(4):
        a=i*math.pi/2
        temp=Part('brace')
        box(temp,(-.055,.38,.46),(.055,3.39,.56),L['paint_b'])
        cylinder(temp,(0,.9,.50),(0,.18,1.03),.06,10,L['metal'])
        box(temp,(-.23,.04,.81),(.23,.14,1.18),L['dark'])
        cylinder(temp,(0,.13,1.02),(0,.34,1.02),.075,10,L['chrome'])
        temp.transform(rot_y(a));body.merge(temp)
    # Side connector enclosure, weather cover, carry handles.
    box(body,(.48,.75,-.23),(.74,1.34,.23),L['dark'])
    face(body,(.746,.91,-.17),(0,0,1),(0,1,0),.34,.22,L['glass'])
    for y in [1.45,2.7]:
        for sign in [-1,1]:
            x=sign*.53
            cylinder(body,(x,y,-.13),(x,y+.25,-.13),.026,8,L['metal'])
            cylinder(body,(x,y,-.13),(x,y,.13),.026,8,L['metal'])
    face(body,(-.22,1.65,.488),(1,0,0),(0,1,0),.44,.28,L['marks'].sub(0,0,1,.25))
    face(body,(-.22,2.3,.488),(1,0,0),(0,1,0),.44,.24,L['stripes'])
    cylinder(lamps,(.73,1.22,-.08),(.76,1.22,-.08),.035,12,L['lights'].sub(.5,0,1,.15))
    lathe(cap,[(0,3.58),(.48,3.58),(.55,3.55),(.55,3.66),(.4,3.71),(0,3.71)],n,L['paint_b'],smooth=False)
    for x in [-.18,.18]:
        cylinder(cap,(x,3.69,-.12),(x,3.77,-.12),.025,8,L['metal'])
    cylinder(cap,(-.18,3.77,-.12),(.18,3.77,-.12),.025,8,L['metal'])
    return [body,cap,lamps],{'mouth':(0,3.6,0)}

def rocket(lod=0):
    body=Part('body');fins=Part('fins')
    n=28 if lod==0 else 14
    lathe(body,[(.23,0),(.23,.3),(.215,.4),(.215,2.4)],n,M['body'])
    lathe(body,[(.215,2.4),(.21,2.5),(.17,2.8),(.10,3.04),(.025,3.20),(0,3.22)],n,M['white'])
    lathe(body,[(.13,-.15),(.10,.06),(.06,.15)],n,M['nozzle'])
    lathe(body,[(.05,.15),(.08,.04),(.115,-.15)],n,M['dark'])
    for y in [.28,1.3,2.36]:
        lathe(body,[(.215,y),(.23,y+.01),(.23,y+.05),(.215,y+.06)],n,M['metal'],smooth=False)
    for k in range(4):
        a=k*math.pi/2
        temp=Part('fin')
        # Thin tapered game fin, independent rendering until the round clears the mouth.
        points=[(.20,.08,0),(.49,.08,0),(.49,.38,0),(.20,.74,0)]
        ids=[temp.vert(p,(0,0,1),M['body'].uv((p[0]-.2)/.3,p[1]/.8)) for p in points]
        temp.quad(*ids);temp.transform(rot_y(a));fins.merge(temp)
    return [body,fins],{'base':(0,0,0),'tip':(0,3.22,0)}

def remote(lod=0):
    p=Part('body')
    box(p,(-.16,0,-.12),(.16,.15,.27),L['dark'])
    box(p,(-.12,.15,-.10),(.12,.27,.19),L['paint'])
    box(p,(-.075,-.20,-.06),(.075,0,.07),L['rubber'])
    cylinder(p,(0,.21,.19),(0,.21,.32),.065,20,L['metal'])
    cylinder(p,(0,.21,.321),(0,.21,.327),.051,20,L['glass'])
    cylinder(p,(-.13,.17,-.06),(-.13,.39,-.06),.008,8,L['metal'])
    face(p,(-.09,.16,-.103),(1,0,0),(0,1,0),.18,.09,L['glass'],flip=True)
    return [p],{}
