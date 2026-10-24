"""Minimal glTF 2.0 reader (json + external .bin): world-space triangle soup per primitive."""
import json, struct, math
from pathlib import Path
import numpy as np

CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def quat_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def node_matrix(n):
    if 'matrix' in n:
        return np.array(n['matrix'], dtype=float).reshape(4, 4).T
    m = np.eye(4)
    s = np.diag(n.get('scale', [1, 1, 1]) + [1])
    r = np.eye(4)
    r[:3, :3] = quat_mat(n.get('rotation', [0, 0, 0, 1]))
    t = np.eye(4)
    t[:3, 3] = n.get('translation', [0, 0, 0])
    return t @ r @ s


class Gltf:
    def __init__(self, path):
        self.path = Path(path)
        self.j = json.load(open(self.path))
        self.bins = [open(self.path.parent / b['uri'], 'rb').read() for b in self.j['buffers']]

    def acc(self, i):
        a = self.j['accessors'][i]
        bv = self.j['bufferViews'][a['bufferView']]
        buf = self.bins[bv['buffer']]
        dt = np.dtype(CT[a['componentType']])
        nc = NC[a['type']]
        off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
        stride = bv.get('byteStride', dt.itemsize * nc)
        cnt = a['count']
        if stride == dt.itemsize * nc:
            arr = np.frombuffer(buf, dtype=dt, count=cnt * nc, offset=off).reshape(cnt, nc)
        else:
            raw = np.frombuffer(buf, dtype=np.uint8, count=stride * (cnt - 1) + dt.itemsize * nc, offset=off)
            arr = np.lib.stride_tricks.as_strided(raw, shape=(cnt, dt.itemsize * nc), strides=(stride, 1)).copy().view(dt).reshape(cnt, nc)
        arr = arr.astype(np.float64) if dt == np.float32 else arr.astype(np.int64)
        if a.get('normalized') and dt != np.float32:
            arr = arr / float(np.iinfo(dt).max)
        return arr

    def walk(self):
        """yield (node_path_names, world_matrix, mesh_index)"""
        out = []
        def rec(ni, parent, names):
            n = self.j['nodes'][ni]
            m = parent @ node_matrix(n)
            nm = names + [n.get('name', str(ni))]
            if 'mesh' in n:
                out.append((nm, m, n['mesh']))
            for c in n.get('children', []):
                rec(c, m, nm)
        for r in self.j['scenes'][self.j.get('scene', 0)]['nodes']:
            rec(r, np.eye(4), [])
        return out

    def primitives(self):
        """list of dicts: names, mesh, material, pos (n,3), nrm (n,3), uv (n,2), tri (k,3)"""
        res = []
        for names, m, mi in self.walk():
            nm = np.linalg.inv(m[:3, :3]).T
            for p in self.j['meshes'][mi]['primitives']:
                if p.get('mode', 4) != 4:
                    continue
                at = p['attributes']
                pos = self.acc(at['POSITION'])
                pos = (np.c_[pos, np.ones(len(pos))] @ m.T)[:, :3]
                nrm = self.acc(at['NORMAL']) @ nm.T if 'NORMAL' in at else np.zeros_like(pos)
                ln = np.linalg.norm(nrm, axis=1, keepdims=True)
                nrm = nrm / np.maximum(ln, 1e-12)
                uv = self.acc(at['TEXCOORD_0']) if 'TEXCOORD_0' in at else np.zeros((len(pos), 2))
                idx = self.acc(p['indices']).reshape(-1) if 'indices' in p else np.arange(len(pos))
                tri = idx.reshape(-1, 3)
                if np.linalg.det(m[:3, :3]) < 0:
                    tri = tri[:, ::-1]
                res.append(dict(names=names, mesh=mi, material=p.get('material'), pos=pos, nrm=nrm, uv=uv, tri=tri))
        return res
