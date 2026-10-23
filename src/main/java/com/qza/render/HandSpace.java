package com.qza.render;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class HandSpace {
    public static final Matrix4f HELD = new Matrix4f()
            .translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
            .rotateXYZ(0f, rad(-90f), rad(25f))
            .scale(0.68f)
            .translate(-0.5f, -0.5f, -0.5f);
    public static final Vector3f FACE = new Vector3f(-1f, 0f, 0f);
    public static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    public static final Vector3f GRIP = texel(3.2f, 13.5f);
    public static final Vector3f HANDLE = direction(1f, 1f);
    public static final float PIXEL = 0.68f / 16f;
    private static final float KARAMBIT_TURN = 165f;
    private static final Vector3f KARAMBIT_LIFT = new Vector3f(0f, 0.3f, -0.06f);
    public static final Matrix4f HELD_TP = new Matrix4f()
            .translate(0f, 4f / 16f, 0.5f / 16f)
            .rotateXYZ(0f, rad(-90f), rad(55f))
            .scale(0.85f)
            .translate(-0.5f, -0.5f, -0.5f);
    public static final Vector3f KARAMBIT_HOLD = texel(5.75f, 10.25f);
    public static final Matrix4f KARAMBIT_REVERSE = karambitGrip(1f);
    public static final Matrix4f KARAMBIT_REVERSE_TP = flipInPlane(HELD_TP, 180f, new Vector3f(),
            5.75f, 10.25f, 2.6f, 12.5f);

    private HandSpace() {
    }

    public static boolean thirdPerson(ItemDisplayContext context) {
        return context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    public static Matrix4f karambitGrip(float amount) {
        return flipInPlane(HELD, KARAMBIT_TURN * amount, new Vector3f(KARAMBIT_LIFT).mul(amount),
                5.75f, 10.25f, 5.75f, 10.25f);
    }

    private static Matrix4f flipInPlane(Matrix4f held, float degrees, Vector3f lift, float fromX, float fromY,
                                        float toX, float toY) {
        return new Matrix4f()
                .translate(lift)
                .mul(held)
                .translate(toX / 16f, 1f - toY / 16f, 0.5f)
                .rotateZ(rad(degrees))
                .translate(-fromX / 16f, -(1f - fromY / 16f), -0.5f)
                .mul(held.invert(new Matrix4f()));
    }

    public static boolean firstPerson(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    public static Vector3f texel(float x, float y) {
        return HELD.transformPosition(x / 16f, 1f - y / 16f, 0.5f, new Vector3f());
    }

    public static Vector3f direction(float x, float y) {
        return HELD.transformDirection(x, y, 0f, new Vector3f()).normalize();
    }

    public static Matrix4f side(Matrix4f m, boolean right) {
        return right ? m : new Matrix4f().scale(-1f, 1f, 1f).mul(m).scale(-1f, 1f, 1f);
    }

    public static Matrix4f around(Vector3fc pivot, float degrees, Vector3fc axis) {
        return new Matrix4f()
                .translate(pivot)
                .rotate(rad(degrees), axis)
                .translate(-pivot.x(), -pivot.y(), -pivot.z());
    }

    public static Matrix4f around(Vector3fc pivot, float scale) {
        return new Matrix4f()
                .translate(pivot)
                .scale(scale)
                .translate(-pivot.x(), -pivot.y(), -pivot.z());
    }

    public static Matrix4f stretch(Vector3fc from, Vector3fc axis, float amount) {
        float k = amount - 1f;
        Matrix4f scale = new Matrix4f(
                1f + k * axis.x() * axis.x(), k * axis.x() * axis.y(), k * axis.x() * axis.z(), 0f,
                k * axis.y() * axis.x(), 1f + k * axis.y() * axis.y(), k * axis.y() * axis.z(), 0f,
                k * axis.z() * axis.x(), k * axis.z() * axis.y(), 1f + k * axis.z() * axis.z(), 0f,
                0f, 0f, 0f, 1f);
        return new Matrix4f()
                .translate(from)
                .mul(scale)
                .translate(-from.x(), -from.y(), -from.z());
    }

    public static float rad(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }
}
