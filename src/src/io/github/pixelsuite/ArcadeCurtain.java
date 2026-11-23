package io.github.pixelsuite;

import android.graphics.RectF;

final class ArcadeCurtain {
    static final float MORPH_MS = 360, EXTRAS_MS = 220, LEAVE_MS = 190;

    static boolean land(float W, float H) { return W > H * 1.05f; }

    static RectF scene(int game, float W, float H, float d, float topInset, RectF out) {
        if (land(W, H)) {
            float w = Math.min(W * .42f, 440 * d), h = Math.min(w * (game == ArcadePreview.STACK ? .82f : .74f), H - topInset - 176 * d);
            h = Math.max(h, 96 * d);
            float cx = game == ArcadePreview.STACK ? W / 2f : W * .27f, top = topInset + 16 * d;
            out.set(cx - w / 2, top, cx + w / 2, top + h);
            return out;
        }
        float w = Math.min(W - 40 * d, 420 * d);
        if (game == ArcadePreview.STACK) { float h = Math.min(H * .38f, w * .82f); out.set(W / 2f - w / 2, H * .4f - h / 2, W / 2f + w / 2, H * .4f + h / 2); }
        else { float h = Math.min(H * .3f, w * .74f), mid = topInset + 14 * d + h / 2; out.set(W / 2f - w / 2, mid - h / 2, W / 2f + w / 2, mid + h / 2); }
        return out;
    }

    static float titleY(int game, float W, float H, float d, float topInset, RectF scene) {
        if (land(W, H)) return scene.bottom + 38 * d;
        return scene.bottom + (game == ArcadePreview.STACK ? 50 : 40) * d;
    }

    static float titleX(float W, float H, RectF scene) { return land(W, H) ? scene.centerX() : W / 2f; }

    static float footY(float W, float H, float d, float bottomInset) { return land(W, H) ? H - 22 * d - bottomInset * .5f : H - 64 * d - bottomInset * .5f; }
}
