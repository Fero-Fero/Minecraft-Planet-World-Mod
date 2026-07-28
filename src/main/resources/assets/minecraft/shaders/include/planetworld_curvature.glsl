#version 150

vec3 planetworld_apply_curvature(vec3 pos, float curvature, float wrapHalf) {
    if (curvature == 0.0) {
        return pos;
    }
    float dx = pos.x;
    float dz = pos.z;
    if (wrapHalf > 0.0) {
        float width = wrapHalf * 2.0;
        dx = mod(dx + wrapHalf, width);
        if (dx < 0.0) dx += width;
        dx -= wrapHalf;
        dz = mod(dz + wrapHalf, width);
        if (dz < 0.0) dz += width;
        dz -= wrapHalf;
    }
    pos.y -= (dx * dx + dz * dz) * curvature;
    return pos;
}
