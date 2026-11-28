package io.github.pixelsuite;

import android.view.View;
import android.widget.LinearLayout;

final class ChipFit {
    private ChipFit() { }

    static void fit(final LinearLayout line, final int pad, final int gap) {
        final View holder = (View) line.getParent();
        final float d = line.getResources().getDisplayMetrics().density;
        holder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int l0, int t0, int r0, int b0) {
                int room = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight() - line.getPaddingLeft() - line.getPaddingRight();
                int n = line.getChildCount();
                if (room <= 0 || n == 0) return;
                v.removeOnLayoutChangeListener(this);
                int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), words = 0;
                for (int i = 0; i < n; i++) {
                    View c = line.getChildAt(i);
                    c.measure(spec, spec);
                    words += c.getMeasuredWidth() - c.getPaddingLeft() - c.getPaddingRight();
                }
                int p = pad, g = gap, minPad = Math.round(7 * d), minGap = Math.round(4 * d);
                while (words + n * 2 * p + (n - 1) * g > room && (p > minPad || g > minGap)) {
                    if (p > minPad) p--; else g--;
                }
                if (words + n * 2 * p + (n - 1) * g > room) return;
                for (int i = 0; i < n; i++) {
                    View c = line.getChildAt(i);
                    c.setPadding(p, c.getPaddingTop(), p, c.getPaddingBottom());
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
                    lp.setMarginEnd(i == n - 1 ? 0 : g);
                    c.setLayoutParams(lp);
                }
                line.requestLayout();
            }
        });
    }
}
