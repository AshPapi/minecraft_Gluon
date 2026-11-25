package net.ashpapi.papioptimization.memory;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.ashpapi.papioptimization.mixin.memory.ArrayVoxelShapeAccessor;
import net.ashpapi.papioptimization.mixin.memory.BitSetDiscreteVoxelShapeAccessor;
import net.ashpapi.papioptimization.mixin.memory.SliceShapeAccessor;
import net.ashpapi.papioptimization.mixin.memory.SubShapeAccessor;
import net.ashpapi.papioptimization.mixin.memory.VoxelShapeMemoryAccessor;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.SubShape;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PapiShapeCanonicalizer {

    private static final int MAX_STURDY = 4096;
    private static final int MAX_COORDS = 8192;
    private static final int MAX_DISCRETE = 16384;
    private static final int MAX_SHAPES = 32768;
    private static final int MAX_FACE_ARRAYS = 16384;

    private static final Long2ObjectOpenHashMap<boolean[]> STURDY_INTERNER = new Long2ObjectOpenHashMap<>(64);

    private static final ObjectOpenCustomHashSet<DoubleList> COORD_INTERNER =
            new ObjectOpenCustomHashSet<>(256, DoubleListStrategy.INSTANCE);

    private static final ObjectOpenCustomHashSet<DiscreteVoxelShape> DISCRETE_INTERNER =
            new ObjectOpenCustomHashSet<>(512, DiscreteShapeStrategy.INSTANCE);

    private static final ObjectOpenCustomHashSet<VoxelShape> SHAPE_INTERNER =
            new ObjectOpenCustomHashSet<>(1024, VoxelShapeStrategy.INSTANCE);

    private static final ObjectOpenCustomHashSet<VoxelShape[]> FACE_ARRAY_INTERNER =
            new ObjectOpenCustomHashSet<>(512, FaceArrayStrategy.INSTANCE);

    private static final ThreadLocal<ReferenceOpenHashSet<VoxelShape>> ACTIVE_SHAPES =
            ThreadLocal.withInitial(() -> new ReferenceOpenHashSet<>(8));

    private PapiShapeCanonicalizer() {
    }

    public static boolean[] internFaceSturdy(boolean[] raw) {
        if (raw == null || raw.length > 32) {
            return raw;
        }
        int mask = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i]) {
                mask |= (1 << i);
            }
        }
        long key = ((long) raw.length << 32) | Integer.toUnsignedLong(mask);
        synchronized (STURDY_INTERNER) {
            boolean[] existing = STURDY_INTERNER.get(key);
            if (existing != null) {
                return existing;
            }
            if (STURDY_INTERNER.size() < MAX_STURDY) {
                STURDY_INTERNER.put(key, raw);
            }
            return raw;
        }
    }

    public static DoubleList internDoubleList(DoubleList coords) {
        if (coords == null) {
            return null;
        }
        synchronized (COORD_INTERNER) {
            DoubleList existing = COORD_INTERNER.get(coords);
            if (existing != null) {
                return existing;
            }
            if (COORD_INTERNER.size() < MAX_COORDS) {
                COORD_INTERNER.add(coords);
            }
            return coords;
        }
    }

    public static DiscreteVoxelShape internDiscrete(DiscreteVoxelShape discrete) {
        if (discrete == null) {
            return null;
        }
        Class<?> cls = discrete.getClass();
        if (cls != SubShape.class && cls != BitSetDiscreteVoxelShape.class) {
            return discrete;
        }
        synchronized (DISCRETE_INTERNER) {
            DiscreteVoxelShape existing = DISCRETE_INTERNER.get(discrete);
            if (existing != null) {
                return existing;
            }
        }
        if (cls == SubShape.class) {
            SubShapeAccessor sub = (SubShapeAccessor) discrete;
            DiscreteVoxelShape parent = sub.papi$getParent();
            DiscreteVoxelShape canonParent = internDiscrete(parent);
            if (canonParent != parent) {
                sub.papi$setParent(canonParent);
            }
        }
        synchronized (DISCRETE_INTERNER) {
            DiscreteVoxelShape existing = DISCRETE_INTERNER.get(discrete);
            if (existing != null) {
                return existing;
            }
            if (DISCRETE_INTERNER.size() < MAX_DISCRETE) {
                DISCRETE_INTERNER.add(discrete);
            }
            return discrete;
        }
    }

    public static VoxelShape internShape(VoxelShape shape) {
        if (shape == null || shape == Shapes.empty() || shape == Shapes.block()) {
            return shape;
        }
        Class<?> cls = shape.getClass();
        if (cls != ArrayVoxelShape.class && cls != CubeVoxelShape.class && cls != SliceShape.class) {
            return shape;
        }

        ReferenceOpenHashSet<VoxelShape> active = ACTIVE_SHAPES.get();
        if (!active.add(shape)) {
            return shape;
        }
        try {
            VoxelShape canonical = null;
            synchronized (SHAPE_INTERNER) {
                VoxelShape existing = SHAPE_INTERNER.get(shape);
                if (existing != null) {
                    canonical = existing;
                }
            }

            if (canonical == null) {
                VoxelShapeMemoryAccessor baseAcc = (VoxelShapeMemoryAccessor) shape;
                DiscreteVoxelShape rawDiscrete = baseAcc.papi$getShape();
                DiscreteVoxelShape canonDiscrete = internDiscrete(rawDiscrete);
                if (canonDiscrete != rawDiscrete) {
                    baseAcc.papi$setShape(canonDiscrete);
                }

                if (cls == ArrayVoxelShape.class) {
                    ArrayVoxelShapeAccessor arrAcc = (ArrayVoxelShapeAccessor) shape;
                    DoubleList xs = arrAcc.papi$getXs();
                    DoubleList ys = arrAcc.papi$getYs();
                    DoubleList zs = arrAcc.papi$getZs();
                    DoubleList cXs = internDoubleList(xs);
                    DoubleList cYs = internDoubleList(ys);
                    DoubleList cZs = internDoubleList(zs);
                    if (cXs != xs) {
                        arrAcc.papi$setXs(cXs);
                    }
                    if (cYs != ys) {
                        arrAcc.papi$setYs(cYs);
                    }
                    if (cZs != zs) {
                        arrAcc.papi$setZs(cZs);
                    }
                } else if (cls == SliceShape.class) {
                    SliceShapeAccessor sliceAcc = (SliceShapeAccessor) shape;
                    VoxelShape delegate = sliceAcc.papi$getDelegate();
                    VoxelShape canonDelegate = internShape(delegate);
                    if (canonDelegate != delegate) {
                        sliceAcc.papi$setDelegate(canonDelegate);
                    }
                }

                synchronized (SHAPE_INTERNER) {
                    VoxelShape existing = SHAPE_INTERNER.get(shape);
                    if (existing != null) {
                        canonical = existing;
                    } else {
                        if (SHAPE_INTERNER.size() < MAX_SHAPES) {
                            SHAPE_INTERNER.add(shape);
                        }
                        canonical = shape;
                    }
                }
            }

            VoxelShapeMemoryAccessor canonAcc = (VoxelShapeMemoryAccessor) canonical;
            VoxelShape[] faces = canonAcc.papi$getFaces();
            if (faces != null && faces.length == 6) {
                VoxelShape[] canonFaces = internFaceArray(faces);
                if (canonFaces != faces) {
                    canonAcc.papi$setFaces(canonFaces);
                }
            }

            return canonical;
        } finally {
            active.remove(shape);
        }
    }

    public static VoxelShape[] internFaceArray(VoxelShape[] faces) {
        if (faces == null || faces.length != 6) {
            return faces;
        }
        synchronized (FACE_ARRAY_INTERNER) {
            VoxelShape[] already = FACE_ARRAY_INTERNER.get(faces);
            if (already == faces) {
                return faces;
            }
        }
        VoxelShape f0 = internShape(faces[0]);
        VoxelShape f1 = internShape(faces[1]);
        VoxelShape f2 = internShape(faces[2]);
        VoxelShape f3 = internShape(faces[3]);
        VoxelShape f4 = internShape(faces[4]);
        VoxelShape f5 = internShape(faces[5]);
        boolean complete = f0 != null
                && f1 != null
                && f2 != null
                && f3 != null
                && f4 != null
                && f5 != null;
        boolean changed = f0 != faces[0]
                || f1 != faces[1]
                || f2 != faces[2]
                || f3 != faces[3]
                || f4 != faces[4]
                || f5 != faces[5];
        if (!complete) {
            if (changed) {
                faces[0] = f0;
                faces[1] = f1;
                faces[2] = f2;
                faces[3] = f3;
                faces[4] = f4;
                faces[5] = f5;
            }
            return faces;
        }
        VoxelShape[] candidate = changed ? new VoxelShape[]{f0, f1, f2, f3, f4, f5} : faces;
        synchronized (FACE_ARRAY_INTERNER) {
            VoxelShape[] existing = FACE_ARRAY_INTERNER.get(candidate);
            if (existing != null) {
                return existing;
            }
            if (FACE_ARRAY_INTERNER.size() < MAX_FACE_ARRAYS) {
                FACE_ARRAY_INTERNER.add(candidate);
            }
            return candidate;
        }
    }

    private static final class DoubleListStrategy implements Hash.Strategy<DoubleList> {
        static final DoubleListStrategy INSTANCE = new DoubleListStrategy();

        @Override
        public int hashCode(DoubleList o) {
            if (o == null) {
                return 0;
            }
            int size = o.size();
            int h = 1 + size;
            for (int i = 0; i < size; i++) {
                long bits = Double.doubleToLongBits(o.getDouble(i));
                h = 31 * h + Long.hashCode(bits);
            }
            return h;
        }

        @Override
        public boolean equals(DoubleList a, DoubleList b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            int size = a.size();
            if (size != b.size()) {
                return false;
            }
            for (int i = 0; i < size; i++) {
                if (Double.doubleToLongBits(a.getDouble(i)) != Double.doubleToLongBits(b.getDouble(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class DiscreteShapeStrategy implements Hash.Strategy<DiscreteVoxelShape> {
        static final DiscreteShapeStrategy INSTANCE = new DiscreteShapeStrategy();

        @Override
        public int hashCode(DiscreteVoxelShape d) {
            if (d == null) {
                return 0;
            }
            Class<?> cls = d.getClass();
            int h = cls.hashCode();
            h = 31 * h + d.getXSize();
            h = 31 * h + d.getYSize();
            h = 31 * h + d.getZSize();
            if (cls == BitSetDiscreteVoxelShape.class) {
                BitSetDiscreteVoxelShapeAccessor acc = (BitSetDiscreteVoxelShapeAccessor) d;
                h = 31 * h + acc.papi$getXMin();
                h = 31 * h + acc.papi$getYMin();
                h = 31 * h + acc.papi$getZMin();
                h = 31 * h + acc.papi$getXMax();
                h = 31 * h + acc.papi$getYMax();
                h = 31 * h + acc.papi$getZMax();
                h = 31 * h + acc.papi$getStorage().hashCode();
            } else if (cls == SubShape.class) {
                SubShapeAccessor acc = (SubShapeAccessor) d;
                h = 31 * h + System.identityHashCode(acc.papi$getParent());
                h = 31 * h + acc.papi$getStartX();
                h = 31 * h + acc.papi$getStartY();
                h = 31 * h + acc.papi$getStartZ();
                h = 31 * h + acc.papi$getEndX();
                h = 31 * h + acc.papi$getEndY();
                h = 31 * h + acc.papi$getEndZ();
            }
            return h;
        }

        @Override
        public boolean equals(DiscreteVoxelShape a, DiscreteVoxelShape b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null || a.getClass() != b.getClass()) {
                return false;
            }
            if (a.getXSize() != b.getXSize() || a.getYSize() != b.getYSize() || a.getZSize() != b.getZSize()) {
                return false;
            }
            Class<?> cls = a.getClass();
            if (cls == BitSetDiscreteVoxelShape.class) {
                BitSetDiscreteVoxelShapeAccessor a1 = (BitSetDiscreteVoxelShapeAccessor) a;
                BitSetDiscreteVoxelShapeAccessor a2 = (BitSetDiscreteVoxelShapeAccessor) b;
                return a1.papi$getXMin() == a2.papi$getXMin()
                        && a1.papi$getYMin() == a2.papi$getYMin()
                        && a1.papi$getZMin() == a2.papi$getZMin()
                        && a1.papi$getXMax() == a2.papi$getXMax()
                        && a1.papi$getYMax() == a2.papi$getYMax()
                        && a1.papi$getZMax() == a2.papi$getZMax()
                        && a1.papi$getStorage().equals(a2.papi$getStorage());
            }
            if (cls == SubShape.class) {
                SubShapeAccessor s1 = (SubShapeAccessor) a;
                SubShapeAccessor s2 = (SubShapeAccessor) b;
                return s1.papi$getParent() == s2.papi$getParent()
                        && s1.papi$getStartX() == s2.papi$getStartX()
                        && s1.papi$getStartY() == s2.papi$getStartY()
                        && s1.papi$getStartZ() == s2.papi$getStartZ()
                        && s1.papi$getEndX() == s2.papi$getEndX()
                        && s1.papi$getEndY() == s2.papi$getEndY()
                        && s1.papi$getEndZ() == s2.papi$getEndZ();
            }
            return false;
        }
    }

    private static final class VoxelShapeStrategy implements Hash.Strategy<VoxelShape> {
        static final VoxelShapeStrategy INSTANCE = new VoxelShapeStrategy();

        @Override
        public int hashCode(VoxelShape s) {
            if (s == null) {
                return 0;
            }
            Class<?> cls = s.getClass();
            DiscreteVoxelShape d = ((VoxelShapeMemoryAccessor) s).papi$getShape();
            int h = 31 * cls.hashCode() + System.identityHashCode(d);
            if (cls == ArrayVoxelShape.class) {
                ArrayVoxelShapeAccessor arr = (ArrayVoxelShapeAccessor) s;
                h = 31 * h + System.identityHashCode(arr.papi$getXs());
                h = 31 * h + System.identityHashCode(arr.papi$getYs());
                h = 31 * h + System.identityHashCode(arr.papi$getZs());
            } else if (cls == SliceShape.class) {
                SliceShapeAccessor sl = (SliceShapeAccessor) s;
                h = 31 * h + sl.papi$getAxis().ordinal();
                h = 31 * h + System.identityHashCode(sl.papi$getDelegate());
            }
            return h;
        }

        @Override
        public boolean equals(VoxelShape a, VoxelShape b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null || a.getClass() != b.getClass()) {
                return false;
            }
            DiscreteVoxelShape d1 = ((VoxelShapeMemoryAccessor) a).papi$getShape();
            DiscreteVoxelShape d2 = ((VoxelShapeMemoryAccessor) b).papi$getShape();
            if (d1 != d2 && !DiscreteShapeStrategy.INSTANCE.equals(d1, d2)) {
                return false;
            }
            Class<?> cls = a.getClass();
            if (cls == CubeVoxelShape.class) {
                return true;
            }
            if (cls == ArrayVoxelShape.class) {
                ArrayVoxelShapeAccessor a1 = (ArrayVoxelShapeAccessor) a;
                ArrayVoxelShapeAccessor a2 = (ArrayVoxelShapeAccessor) b;
                return a1.papi$getXs() == a2.papi$getXs()
                        && a1.papi$getYs() == a2.papi$getYs()
                        && a1.papi$getZs() == a2.papi$getZs();
            }
            if (cls == SliceShape.class) {
                SliceShapeAccessor s1 = (SliceShapeAccessor) a;
                SliceShapeAccessor s2 = (SliceShapeAccessor) b;
                return s1.papi$getAxis() == s2.papi$getAxis()
                        && s1.papi$getDelegate() == s2.papi$getDelegate();
            }
            return false;
        }
    }

    private static final class FaceArrayStrategy implements Hash.Strategy<VoxelShape[]> {
        static final FaceArrayStrategy INSTANCE = new FaceArrayStrategy();

        @Override
        public int hashCode(VoxelShape[] o) {
            if (o == null) {
                return 0;
            }
            int h = 1;
            for (int i = 0; i < 6; i++) {
                h = 31 * h + System.identityHashCode(o[i]);
            }
            return h;
        }

        @Override
        public boolean equals(VoxelShape[] a, VoxelShape[] b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null || a.length != 6 || b.length != 6) {
                return false;
            }
            for (int i = 0; i < 6; i++) {
                if (a[i] != b[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
