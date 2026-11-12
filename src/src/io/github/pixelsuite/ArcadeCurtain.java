package io.github.pixelsuite;

import android.graphics.RectF;

final class ArcadeCurtain {
    static final float MORPH_MS = 360, EXTRAS_MS = 220, LEAVE_MS = 190;

    static RectF scene(int game, float W, float H, float d, float topInset, RectF out) {
        float w = Math.min(W - 40 * d, 420 * d);
        if (game == ArcadePreview.STACK) { float h = Math.min(H * .38f, w * .82f); out.set(W / 2f - w / 2, H * .4f - h / 2, W / 2f + w / 2, H * .4f + h / 2); }
        else { float h = Math.min(H * .3f, w * .74f), mid = topInset + 14 * d + h / 2; out.set(W / 2f - w / 2, mid - h / 2, W / 2f + w / 2, mid + h / 2); }
        return out;
    }

    static float titleY(int game, float W, float H, float d, float topInset, RectF scene) {
        return scene.bottom + (game == ArcadePreview.STACK ? 50 : 40) * d;
    }
}
