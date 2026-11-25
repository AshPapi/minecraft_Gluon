package net.ashpapi.papioptimization.math;

public final class FastMath {
    private static final double PI = Math.PI;
    private static final double HALF_PI = Math.PI * 0.5;
    private static final double TWO_PI_HI = 0x1.921fb54442d18p+2;
    private static final double TWO_PI_LO = 0x1.1a62633145c07p-52;
    private static final double INV_TWO_PI = 1.0 / (2.0 * Math.PI);

    private static final double S1 = -0.1666665710;
    private static final double S2 = 0.0083330751;
    private static final double S3 = -0.0001984126;

    private FastMath() {
    }

    public static float sin(double rad) {
        if (!Double.isFinite(rad) || Math.abs(rad) > 1.0E9) {
            return (float) Math.sin(rad);
        }
        double k = Math.rint(rad * INV_TWO_PI);
        rad = Math.fma(-k, TWO_PI_HI, rad);
        rad = Math.fma(-k, TWO_PI_LO, rad);

        if (rad > HALF_PI) {
            rad = PI - rad;
        } else if (rad < -HALF_PI) {
            rad = -PI - rad;
        }

        double rad2 = rad * rad;
        double poly = Math.fma(rad2, S3, S2);
        poly = Math.fma(rad2, poly, S1);
        poly = Math.fma(rad2, poly, 1.0);
        return (float) (rad * poly) + 0.0F;
    }

    public static float cos(double rad) {
        if (!Double.isFinite(rad) || Math.abs(rad) > 1.0E9) {
            return (float) Math.cos(rad);
        }
        double k = Math.rint(rad * INV_TWO_PI);
        rad = Math.fma(-k, TWO_PI_HI, rad);
        rad = Math.fma(-k, TWO_PI_LO, rad);
        rad += HALF_PI;

        if (rad > HALF_PI) {
            rad = PI - rad;
        } else if (rad < -HALF_PI) {
            rad = -PI - rad;
        }

        double rad2 = rad * rad;
        double poly = Math.fma(rad2, S3, S2);
        poly = Math.fma(rad2, poly, S1);
        poly = Math.fma(rad2, poly, 1.0);
        return (float) (rad * poly) + 0.0F;
    }

    public static double atan2(double y, double x) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.NaN;
        }
        if (Double.isInfinite(x) || Double.isInfinite(y)) {
            return Math.atan2(y, x);
        }
        if (x == 0.0 && y == 0.0) {
            return 0.0;
        }

        double ax = Math.abs(x);
        double ay = Math.abs(y);
        boolean steep = ay > ax;

        double z = steep ? (ax / ay) : (ay / ax);
        double z2 = z * z;

        double poly = Math.fma(z2, 0.0208351, -0.0851330);
        poly = Math.fma(z2, poly, 0.1801410);
        poly = Math.fma(z2, poly, -0.3302995);
        poly = Math.fma(z2, poly, 0.9998660);
        double theta = z * poly;

        if (steep) {
            theta = (Math.PI * 0.5) - theta;
        }
        if (x < 0.0) {
            theta = Math.PI - theta;
        }
        if (y < 0.0) {
            theta = -theta;
        }
        return theta;
    }

    public static double invSqrt(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        double halfX = 0.5 * x;
        long bits = Double.doubleToRawLongBits(x);
        bits = 0x5fe6eb50c7b537a9L - (bits >> 1);
        double y = Double.longBitsToDouble(bits);
        y = y * (1.5 - halfX * y * y);
        y = y * (1.5 - halfX * y * y);
        return y;
    }

    public static boolean isWithinDistance(double dx, double dy, double dz, double maxDist) {
        if (Math.abs(dx) > maxDist || Math.abs(dy) > maxDist || Math.abs(dz) > maxDist) {
            return false;
        }
        return (dx * dx + dy * dy + dz * dz) <= (maxDist * maxDist);
    }

    public static float wrapDegrees(float f) {
        if (f >= -180.0F && f < 180.0F) {
            return f;
        }
        if (!Float.isFinite(f)) {
            return Float.NaN;
        }
        if (Math.abs(f) > 1.0E7F) {
            float r = f % 360.0F;
            if (r >= 180.0F) {
                r -= 360.0F;
            }
            if (r < -180.0F) {
                r += 360.0F;
            }
            return r;
        }
        double d = f;
        float res = (float) (d - Math.floor((d + 180.0) * (1.0 / 360.0)) * 360.0);
        if (res < -180.0F) {
            res += 360.0F;
        } else if (res >= 180.0F) {
            res -= 360.0F;
        }
        return res;
    }

    public static double wrapDegrees(double d) {
        if (d >= -180.0 && d < 180.0) {
            return d;
        }
        if (!Double.isFinite(d)) {
            return Double.NaN;
        }
        if (Math.abs(d) > 1.0E12) {
            double r = d % 360.0;
            if (r >= 180.0) {
                r -= 360.0;
            }
            if (r < -180.0) {
                r += 360.0;
            }
            return r;
        }
        double res = d - Math.floor((d + 180.0) * (1.0 / 360.0)) * 360.0;
        if (res < -180.0) {
            res += 360.0;
        } else if (res >= 180.0) {
            res -= 360.0;
        }
        return res;
    }

    public static float rotLerp(float delta, float start, float end) {
        if (start == end || delta == 0.0F) {
            return start;
        }
        if (delta == 1.0F) {
            return start + wrapDegrees(end - start);
        }
        return start + delta * wrapDegrees(end - start);
    }

    public static double rotLerp(double delta, double start, double end) {
        if (start == end || delta == 0.0) {
            return start;
        }
        if (delta == 1.0) {
            return start + wrapDegrees(end - start);
        }
        return start + delta * wrapDegrees(end - start);
    }

    public static float rotLerpRad(float delta, float start, float end) {
        if (start == end || delta == 0.0F) {
            return start;
        }
        double diff = (double) end - (double) start;
        if (!Double.isFinite(diff)) {
            return Float.NaN;
        }
        if (Math.abs(diff) > 1.0E7) {
            diff %= (Math.PI * 2.0);
            if (diff >= Math.PI) {
                diff -= Math.PI * 2.0;
            } else if (diff < -Math.PI) {
                diff += Math.PI * 2.0;
            }
        } else if (diff < -Math.PI || diff >= Math.PI) {
            diff -= Math.floor((diff + Math.PI) * (1.0 / (Math.PI * 2.0))) * (Math.PI * 2.0);
            if (diff < -Math.PI) {
                diff += Math.PI * 2.0;
            } else if (diff >= Math.PI) {
                diff -= Math.PI * 2.0;
            }
        }
        return start + delta * (float) diff;
    }
}
