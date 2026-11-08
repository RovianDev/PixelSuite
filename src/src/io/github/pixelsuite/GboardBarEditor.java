package io.github.pixelsuite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GboardBarEditor {
    private GboardBarEditor() {}

    static final String[] IDS = {"clipboard", "voice", "jarvis", "translate", "smiley", "gif_search", "sticker", "theme_setting", "settings", "textediting", "keyboard_resize", "one_handed", "floating_keyboard", "language_switcher", "share", "ocr", "pixelsuite_undo", "pixelsuite_redo", "pixelsuite_select", "pixelsuite_select_all", "pixelsuite_cut", "pixelsuite_copy", "pixelsuite_paste"};
    static final String[] LABELS = {"Clipboard", "Voice typing", "Writing tools", "Translate", "Emoji", "GIF", "Stickers", "Theme", "Settings", "Text editing", "Resize", "One-handed", "Floating", "Next language", "Share", "Scan text", "Undo", "Redo", "Quick Select", "Select all", "Cut", "Copy", "Paste"};
    static final int MAX = 7;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    interface Saver { void save(List<String> order, boolean hideMenu, boolean hideRight, boolean rows, boolean rowsTop); }

    static final Map<String, String> GBOARD = new LinkedHashMap<String, String>();

    private static boolean internal(String id) {
        return id.startsWith("pixelsuite_") || id.startsWith("signboard") || id.startsWith("widget_") || id.equals("more_access_points") || id.equals("LEARNING_CENTER_BUTTON_ID")
                || id.equals("editor_info") || id.equals("flag_editor") || id.equals("quality_bug_report") || id.equals("jetson_feedback") || id.equals("on_demand_feature_split")
                || id.equals("handwriting_promo") || id.equals("muse_toggle_playground_ap") || id.equals("undo_cooperative") || id.equals("emoji_row") || id.equals("keyboard_mode");
    }

    static List<String> ids() {
        List<String> out = new ArrayList<String>(Arrays.asList(IDS));
        synchronized (GBOARD) { for (String id : GBOARD.keySet()) if (!out.contains(id) && !internal(id)) out.add(id); }
        return organized(out);
    }

    static final String[] ORDER = {
            "pixelsuite_undo", "pixelsuite_redo", "undo", "pixelsuite_select", "pixelsuite_select_all", "pixelsuite_cut", "pixelsuite_copy", "pixelsuite_paste", "textediting", "clipboard",
            "voice", "seamless_voice_typing", "jarvis", "translate", "autofill", "ocr", "language_switcher",
            "smiley", "emoticon", "gif_search", "sticker", "rich_symbol", "emojify_plus",
            "keyboard_resize", "one_handed", "floating_keyboard", "split",
            "theme_setting", "settings", "share"};

    static List<String> organized(List<String> ids) {
        final List<String> known = Arrays.asList(ORDER);
        final List<String> in = new ArrayList<String>(ids);
        List<String> out = new ArrayList<String>(in);
        java.util.Collections.sort(out, new java.util.Comparator<String>() {
            public int compare(String x, String y) {
                int a = known.indexOf(x), b = known.indexOf(y);
                if (a < 0) a = known.size() + in.indexOf(x);
                if (b < 0) b = known.size() + in.indexOf(y);
                return a < b ? -1 : a > b ? 1 : 0;
            }
        });
        return out;
    }

    static String explain(String id) { return I18n.t(explain0(id)); }

    static String explain0(String id) {
        switch (id) {
            case "clipboard": return "Your copied text and pinned clips";
            case "voice": case "seamless_voice_typing": return "Type by speaking";
            case "jarvis": return "Rewrite or polish what you wrote";
            case "translate": return "Translate as you type";
            case "smiley": return "Emoji, emoticons and symbols";
            case "gif_search": return "Search and send GIFs";
            case "sticker": return "Search and send stickers";
            case "theme_setting": return "Change how Gboard looks";
            case "settings": return "Open Gboard's settings";
            case "textediting": return "Arrow keys and selection tools";
            case "keyboard_resize": return "Make the keyboard bigger or smaller";
            case "one_handed": return "Shrink the keyboard to one side";
            case "floating_keyboard": return "A keyboard you can move anywhere";
            case "language_switcher": return "Switch to your next language";
            case "share": return "Share Gboard with others";
            case "ocr": return "Type text from your camera";
            case "autofill": return "Fill in saved passwords and details";
            case "pixelsuite_undo": return "Undo your last change";
            case "pixelsuite_redo": return "Bring back what you undid";
            case "pixelsuite_select": return "Tap to select a word, tap again to clear. Hold to select all";
            case "pixelsuite_select_all": return "Select all the text";
            case "pixelsuite_cut": return "Cut the selected text";
            case "pixelsuite_copy": return "Copy the selected text";
            case "pixelsuite_paste": return "Paste what you copied";
            default: return "A Gboard shortcut";
        }
    }

    static void gboardList(String text) {
        synchronized (GBOARD) {
            GBOARD.clear();
            if (text == null) return;
            for (String line : text.split("\n")) {
                int t = line.indexOf('\t');
                String id = (t < 0 ? line : line.substring(0, t)).trim();
                if (!id.matches("[A-Za-z0-9_]+")) continue;
                GBOARD.put(id, t < 0 ? "" : line.substring(t + 1).trim());
            }
        }
    }

    static String label(String id) { return I18n.t(label0(id)); }

    static String label0(String id) {
        int i = Arrays.asList(IDS).indexOf(id);
        if (i >= 0) return LABELS[i];
        String g;
        synchronized (GBOARD) { g = GBOARD.get(id); }
        if (g != null && !g.isEmpty()) return g;
        String n = id.replace('_', ' ');
        return n.isEmpty() ? id : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    static void open(Activity a, List<String> chosen, boolean hideMenu, boolean hideRight, final boolean rows, final boolean rowsTop, boolean dark, int bg, int cardColor, int text, int muted, int accent, final Saver saver) {
        final float d = a.getResources().getDisplayMetrics().density;
        final boolean black = dark && SuperDark.inEffect(a);
        final PageDialog dialog = new PageDialog(a, dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        Window w = dialog.getWindow();
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout overlay = new FrameLayout(a);
        overlay.setClipChildren(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        final Editor ed = new Editor(a, chosen, dark, black, cardColor, text, muted, accent, bg, overlay);
        ed.preview.menuOn = !hideMenu;
        ed.preview.rightOn = !hideRight;
        ed.preview.rowsOn = rows;
        ed.preview.rowsTop = rowsTop;
        ed.preview.rowsS.set(rows ? 1 : 0);
        ed.preview.topS.set(rowsTop ? 1 : 0);

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Math.round(8 * d), Math.round(12 * d), Math.round(12 * d), Math.round(4 * d));
        TextView back = new TextView(a);
        back.setText(I18n.cs("←"));
        back.setTextSize(26);
        back.setTextColor(text);
        back.setPadding(Math.round(12 * d), 0, Math.round(16 * d), 0);
        back.setContentDescription(I18n.cs("Back"));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dialog.close(); } });
        top.addView(back);
        TextView title = new TextView(a);
        title.setText(I18n.cs("Edit toolbar shortcuts"));
        title.setTextSize(22);
        title.setTextColor(text);
        title.setTypeface(I18n.medium());
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = new TextView(a);
        save.setText(I18n.cs("Save"));
        save.setTextSize(16);
        save.setTextColor(accent);
        save.setTypeface(I18n.medium());
        save.setPadding(Math.round(16 * d), Math.round(10 * d), Math.round(16 * d), Math.round(10 * d));
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (ed.top.isEmpty()) { android.widget.Toast.makeText(v.getContext(), I18n.cs("Put at least one shortcut on the toolbar"), android.widget.Toast.LENGTH_SHORT).show(); return; }
            dialog.close();
            saver.save(new ArrayList<String>(ed.top), !ed.preview.menuOn, !ed.preview.rightOn, ed.preview.rowsOn, ed.preview.rowsTop);
        }});
        top.addView(save);
        content.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Math.round(16 * d), Math.round(8 * d), Math.round(16 * d), Math.round(10 * d));
        header.addView(ed.preview, new LinearLayout.LayoutParams(-1, ed.preview.wantHeight()));
        LinearLayout chips = new LinearLayout(a);
        chips.setGravity(Gravity.CENTER);
        final Chip menuChip = new Chip(a, "menu", "Menu button", ed.preview.menuOn, dark, text, muted, accent);
        final Chip rightChip = new Chip(a, "smiley", "Emoji", ed.preview.rightOn, dark, text, muted, accent);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, Math.round(40 * d));
        cp.rightMargin = Math.round(10 * d);
        chips.addView(menuChip, cp);
        chips.addView(rightChip, new LinearLayout.LayoutParams(-2, Math.round(40 * d)));
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(-1, -2);
        chipsLp.topMargin = Math.round(12 * d);
        header.addView(chips, chipsLp);
        final int cardBg = cardColor;
        LinearLayout rowsCard = new LinearLayout(a);
        rowsCard.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable rc = new android.graphics.drawable.GradientDrawable();
        rc.setColor(cardBg);
        rc.setCornerRadius(18 * d);
        if (black) rc.setStroke(Math.max(1, Math.round(d)), 0xFF2C2C2E);
        rowsCard.setBackground(rc);
        final Toggle together = new Toggle(a, "Suggestions and shortcuts together", "Both show at once, in two rows (portrait)", rows, dark, text, muted, accent);
        final Toggle onTop = new Toggle(a, topTitle(rowsTop), topSummary(rowsTop), rowsTop, dark, text, muted, accent);
        onTop.enable(rows);
        rowsCard.addView(together, new LinearLayout.LayoutParams(-1, Math.round(60 * d)));
        View rowsLine = new View(a);
        rowsLine.setBackgroundColor(dark ? 0x1AFFFFFF : 0x12000000);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(d)));
        rl.leftMargin = rl.rightMargin = Math.round(16 * d);
        rowsCard.addView(rowsLine, rl);
        rowsCard.addView(onTop, new LinearLayout.LayoutParams(-1, Math.round(60 * d)));
        together.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            ed.preview.rowsOn = !ed.preview.rowsOn;
            together.set(ed.preview.rowsOn);
            onTop.enable(ed.preview.rowsOn);
            try { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
            ed.preview.rowsChanged();
        }});
        onTop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!ed.preview.rowsOn) return;
            ed.preview.rowsTop = !ed.preview.rowsTop;
            onTop.set(ed.preview.rowsTop);
            onTop.retext(topTitle(ed.preview.rowsTop), topSummary(ed.preview.rowsTop));
            try { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
            ed.preview.rowsChanged();
        }});
        LinearLayout.LayoutParams rowsLp = new LinearLayout.LayoutParams(-1, -2);
        rowsLp.topMargin = Math.round(12 * d);
        header.addView(rowsCard, rowsLp);
        TextView tip = new TextView(a);
        tip.setText(I18n.cs("Hold a shortcut and drag it onto the toolbar, along it to reorder, or off to remove it. Tap one to see what it does."));
        tip.setTextSize(13);
        tip.setTextColor(muted);
        tip.setLineSpacing(2 * d, 1);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(-1, -2);
        tipLp.topMargin = Math.round(8 * d);
        header.addView(tip, tipLp);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(a);
        divider.setBackgroundColor(dark ? 0x22FFFFFF : 0x1A000000);
        content.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(d))));

        View.OnClickListener side = new View.OnClickListener() { public void onClick(View v) {
            boolean menu = v == menuChip || (v == ed.preview && ed.preview.lastTapMenu);
            if (menu) { ed.preview.menuOn = !ed.preview.menuOn; menuChip.set(ed.preview.menuOn); }
            else { ed.preview.rightOn = !ed.preview.rightOn; rightChip.set(ed.preview.rightOn); }
            try { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) { }
            ed.preview.refresh();
        }};
        menuChip.setOnClickListener(side);
        rightChip.setOnClickListener(side);
        ed.preview.sideTap = side;

        ed.grid.setPadding(Math.round(16 * d), Math.round(14 * d), Math.round(16 * d), Math.round(12 * d));
        content.addView(ed.grid, new LinearLayout.LayoutParams(-1, 0, 1));
        ed.preview.refresh();
        ed.preview.settle();
        dialog.setContentView(root);
        if (w != null) {
            w.setLayout(-1, -1);
            w.setBackgroundDrawable(new ColorDrawable(bg));
            w.setStatusBarColor(bg);
            w.setNavigationBarColor(bg);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            if (!dark) w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x10);
        }
        dialog.animate(root, bg);
        dialog.show();
    }

    static final class Editor {
        final Context c;
        final float d;
        final List<String> top = new ArrayList<String>(), more = new ArrayList<String>();
        final Preview preview;
        final Grid grid;
        final FrameLayout overlay;
        final int card, cardLifted, text, muted, accent;
        final boolean dark, black;
        String dragId;
        boolean fromBar;
        Ghost ghost;
        float grabX, grabY;

        Editor(Context c, List<String> chosen, boolean dark, boolean black, int cardColor, int text, int muted, int accent, int page, FrameLayout overlay) {
            this.c = c;
            this.d = c.getResources().getDisplayMetrics().density;
            this.dark = dark; this.black = black; this.text = text; this.muted = muted; this.accent = accent; this.overlay = overlay;
            card = cardColor != 0 ? cardColor : (dark ? 0xFF1C1C1E : 0xFFF1F2F6);
            cardLifted = dark ? Grid.mix(card, 0xFFFFFFFF, 0.12f) : 0xFFFFFFFF;
            List<String> all = ids();
            for (String id : chosen) if (all.contains(id) && !top.contains(id) && top.size() < MAX) top.add(id);
            for (String id : all) if (!top.contains(id)) more.add(id);
            preview = new Preview(c, this, dark, black, accent);
            grid = new Grid(c, this, all, page);
        }

        boolean full() { return top.size() >= MAX; }

        void haptic(View v, int kind) { try { v.performHapticFeedback(kind); } catch (Throwable ignored) { } }

        private View tip;
        private final Runnable tipOff = new Runnable() { public void run() { hideTip(); } };

        void explain(String id, float cx, float topY, float bottomY) {
            hideTipNow();
            String name = GboardBarEditor.label(id), what = GboardBarEditor.explain(id);
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Math.round(14 * d), Math.round(10 * d), Math.round(14 * d), Math.round(11 * d));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(black ? SuperDarkPalette.DIALOG : dark ? Grid.mix(card, 0xFFFFFFFF, 0.12f) : 0xFFFFFFFF);
            bg.setCornerRadius(16 * d);
            bg.setStroke(Math.max(1, Math.round(d)), black ? 0xFF2C2C2E : dark ? 0x24FFFFFF : 0x14000000);
            box.setBackground(bg);
            if (!dark) box.setElevation(8 * d);
            TextView t1 = new TextView(c);
            t1.setText(I18n.cs(name));
            t1.setTextSize(14);
            t1.setTextColor(text);
            t1.setTypeface(I18n.medium());
            box.addView(t1);
            TextView t2 = new TextView(c);
            t2.setText(I18n.cs(what));
            t2.setTextSize(13);
            t2.setTextColor(muted);
            t2.setLineSpacing(1.5f * d, 1);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-2, -2);
            lp2.topMargin = Math.round(2 * d);
            box.addView(t2, lp2);
            int W = overlay.getWidth(), H = overlay.getHeight(), m = Math.round(16 * d);
            int maxW = Math.max(Math.round(120 * d), Math.min(W - 2 * m, Math.round(280 * d)));
            box.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int bw = box.getMeasuredWidth(), bh = box.getMeasuredHeight();
            int[] o = origin();
            float x = Math.max(m, Math.min(W - m - bw, cx - o[0] - bw / 2f));
            float above = topY - o[1] - bh - 8 * d, below = bottomY - o[1] + 8 * d;
            boolean up = above >= 8 * d;
            float y = up ? above : Math.min(below, H - bh - 8 * d);
            overlay.addView(box, new FrameLayout.LayoutParams(bw, bh));
            box.setX(x); box.setY(y);
            box.setPivotX(Math.max(0, Math.min(bw, cx - o[0] - x)));
            box.setPivotY(up ? bh : 0);
            box.setAlpha(0f); box.setScaleX(0.94f); box.setScaleY(0.94f);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EASE).start();
            tip = box;
            overlay.postDelayed(tipOff, TIP_MS);
            try { overlay.announceForAccessibility(name + ". " + what); } catch (Throwable ignored) { }
        }

        static final long TIP_MS = 3000;

        void hideTip() {
            overlay.removeCallbacks(tipOff);
            final View t = tip;
            tip = null;
            if (t == null) return;
            t.animate().cancel();
            t.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(140).setInterpolator(EASE)
                    .withEndAction(new Runnable() { public void run() { overlay.removeView(t); } }).start();
        }

        private void hideTipNow() {
            overlay.removeCallbacks(tipOff);
            if (tip != null) { tip.animate().cancel(); overlay.removeView(tip); tip = null; }
        }

        void explainTile(Grid.Tile t) {
            int[] at = new int[2]; t.getLocationOnScreen(at);
            haptic(t, HapticFeedbackConstants.CLOCK_TICK);
            explain(t.id, at[0] + t.getWidth() / 2f, at[1], at[1] + t.getHeight());
        }

        void explainBar(String id) {
            int i = top.indexOf(id);
            if (i < 0) return;
            float[] p = preview.slotScreen(i);
            float r = 18 * d;
            haptic(preview, HapticFeedbackConstants.CLOCK_TICK);
            explain(id, p[0], p[1] - r, p[1] + r);
        }

        void putBack(String id) {
            more.remove(id);
            List<String> all = ids();
            int want = all.indexOf(id), at = more.size();
            for (int i = 0; i < more.size(); i++) if (all.indexOf(more.get(i)) > want) { at = i; break; }
            more.add(at, id);
        }

        void changed() { preview.refresh(); grid.relayout(true); grid.hint(); }

        private int[] origin() { int[] o = new int[2]; overlay.getLocationOnScreen(o); return o; }

        void liftFromGrid(Grid.Tile t, float rx, float ry) {
            hideTip();
            dragId = t.id; fromBar = false;
            int[] at = new int[2]; t.getLocationOnScreen(at);
            int[] o = origin();
            ghost = new Ghost(c, this, t.id);
            overlay.addView(ghost, new FrameLayout.LayoutParams(t.getWidth(), t.getHeight()));
            ghost.setX(at[0] - o[0]); ghost.setY(at[1] - o[1]);
            grabX = rx - at[0]; grabY = ry - at[1];
            ghost.animate().scaleX(1.07f).scaleY(1.07f).setDuration(160).setInterpolator(EASE).start();
            t.animate().alpha(0.25f).setDuration(160).start();
            haptic(t, HapticFeedbackConstants.LONG_PRESS);
        }

        void liftFromBar(String id, float rx, float ry) {
            hideTip();
            dragId = id; fromBar = true;
            int[] o = origin();
            ghost = new Ghost(c, this, id);
            int gw = grid.tileW > 0 ? grid.tileW : Math.round(88 * d), gh = grid.tileH > 0 ? grid.tileH : Math.round(88 * d);
            overlay.addView(ghost, new FrameLayout.LayoutParams(gw, gh));
            grabX = gw / 2f; grabY = gh / 2f;
            ghost.setX(rx - o[0] - grabX); ghost.setY(ry - o[1] - grabY);
            ghost.setScaleX(0.45f); ghost.setScaleY(0.45f);
            ghost.animate().scaleX(0.62f).scaleY(0.62f).setDuration(160).setInterpolator(EASE).start();
            preview.hover(id, top.indexOf(id), false);
            haptic(preview, HapticFeedbackConstants.LONG_PRESS);
        }

        boolean replaceMode() { return !fromBar && full(); }

        void drag(float rx, float ry) {
            if (ghost == null) return;
            int[] o = origin();
            ghost.setX(rx - o[0] - grabX); ghost.setY(ry - o[1] - grabY);
            if (preview.isUnder(rx, ry)) {
                int idx = preview.insertIndex(rx, dragId, replaceMode());
                if (idx != preview.hoverIdx) haptic(preview, HapticFeedbackConstants.CLOCK_TICK);
                preview.hover(dragId, idx, replaceMode());
                ghost.target(0.62f);
            } else {
                preview.hover(fromBar ? dragId : null, -1, false);
                ghost.target(1.07f);
                grid.edgeTurn(rx, ry);
            }
        }

        void drop(float rx, float ry) {
            final String id = dragId;
            final Ghost g = ghost;
            dragId = null; ghost = null;
            if (id == null || g == null) return;
            int[] o = origin();
            if (preview.isUnder(rx, ry)) {
                int idx = preview.insertIndex(rx, id, !fromBar && full());
                if (fromBar) { top.remove(id); top.add(Math.min(idx, top.size()), id); }
                else if (full()) { int at = Math.min(idx, top.size() - 1); String out = top.set(at, id); more.remove(id); putBack(out); grid.appear(out); }
                else { top.add(Math.min(idx, top.size()), id); more.remove(id); }
                preview.hover(null, -1, false);
                preview.refresh();
                preview.popIn(id);
                float[] p = preview.slotScreen(top.indexOf(id));
                g.animate().x(p[0] - o[0] - g.getWidth() / 2f).y(p[1] - o[1] - g.getHeight() / 2f).scaleX(0.36f).scaleY(0.36f).alpha(0f).setDuration(220).setInterpolator(EASE)
                        .withEndAction(new Runnable() { public void run() { overlay.removeView(g); } }).start();
                haptic(preview, HapticFeedbackConstants.CLOCK_TICK);
            } else {
                if (fromBar) { top.remove(id); putBack(id); grid.toPage(Math.max(0, more.indexOf(id)) / Math.max(1, grid.perPage())); }
                preview.hover(null, -1, false);
                preview.refresh();
                grid.relayout(true);
                float[] p = grid.slotScreen(id);
                final Grid.Tile t = grid.tile(id);
                if (t != null && fromBar) { t.setAlpha(0f); t.setVisibility(View.VISIBLE); }
                g.animate().x(p[0] - o[0]).y(p[1] - o[1]).scaleX(1f).scaleY(1f).alpha(1f).setDuration(240).setInterpolator(EASE)
                        .withEndAction(new Runnable() { public void run() { if (t != null) t.setAlpha(1f); overlay.removeView(g); } }).start();
            }
            grid.hint();
            grid.relayout(true);
        }
    }

    static final class Ghost extends View {
        final Editor ed;
        final String id;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG);
        float targetScale = 1.07f;

        Ghost(Context c, Editor ed, String id) {
            super(c);
            this.ed = ed; this.id = id;
            float d = ed.d;
            ink.setStyle(Paint.Style.STROKE); ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
            word.setTextAlign(Paint.Align.CENTER); word.setTextSize(13.5f * d); word.setTypeface(I18n.medium());
            setElevation(14 * d);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * Ghost.this.ed.d); }
            });
        }

        void target(float s) {
            if (Math.abs(targetScale - s) < 0.01f) return;
            targetScale = s;
            animate().scaleX(s).scaleY(s).setDuration(180).setInterpolator(EASE).start();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = ed.d, r = 18 * d;
            fill.setColor(ed.cardLifted);
            c.drawRoundRect(new RectF(0, 0, w, h), r, r, fill);
            c.save();
            float size = 26 * d;
            c.translate(w / 2f - size / 2f, h * 0.2f);
            c.scale(size / 24f, size / 24f);
            Paint p = new Paint(ink); p.setStrokeWidth(1.9f); p.setColor(ed.accent);
            Icons.draw(c, id, p);
            c.restore();
            word.setColor(ed.text);
            String name = GboardBarEditor.label(id);
            float y = h * 0.8f;
            float size0 = word.getTextSize();
            if (word.measureText(name) > w - 12 * d && name.indexOf(' ') > 0) {
                int sp = name.indexOf(' ');
                I18n.fit(word, w - 8 * d, name.substring(0, sp), name.substring(sp + 1));
                c.drawText(name.substring(0, sp), w / 2f, y - 8 * d, word);
                c.drawText(name.substring(sp + 1), w / 2f, y + 8 * d, word);
            } else { I18n.fit(word, w - 8 * d, name); c.drawText(name, w / 2f, y, word); }
            word.setTextSize(size0);
        }
    }

    static final class Preview extends View {
        final Editor ed;
        final float d;
        final int panel, ink, accent, stroke;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final Map<String, Item> items = new LinkedHashMap<String, Item>();
        final Item menu = new Item(), right = new Item();
        boolean menuOn = true, rightOn = true, lastTapMenu;
        boolean rowsOn, rowsTop;
        final Spring rowsS = new Spring(), topS = new Spring();
        final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int wordInk, sep;
        View.OnClickListener sideTap;
        long lastFrame;
        String hoverId;
        int hoverIdx = -1;
        boolean hoverReplace;
        float downX, downY;
        String downItem;
        boolean moved, lifted;
        final Runnable hold = new Runnable() { public void run() { if (downItem != null && !moved) { lifted = true; ed.liftFromBar(downItem, lastRawX, lastRawY); } } };
        float lastRawX, lastRawY;

        static final class Item {
            final Spring x = new Spring(), s = new Spring(), a = new Spring();
            boolean dying, fresh = true;
        }

        Preview(Context c, Editor ed, boolean dark, boolean black, int accent) {
            super(c);
            this.ed = ed;
            d = c.getResources().getDisplayMetrics().density;
            this.accent = accent;
            panel = !dark ? 0xFFEEF0F4 : black ? 0xFF000000 : 0xFF232325;
            ink = !dark ? 0xFF3C4043 : 0xFFE6E6E8;
            stroke = !dark ? 0x14000000 : black ? 0xFF2C2C2E : 0x1FFFFFFF;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            shadow.setColor(!dark ? 0x16000000 : 0x00000000);
            wordInk = !dark ? 0xFF3C4043 : 0xFFE6E6E8;
            sep = !dark ? 0x1F000000 : 0x26FFFFFF;
            words.setTextSize(15 * d);
            words.setTextAlign(Paint.Align.CENTER);
            setContentDescription(I18n.cs("Gboard's toolbar: drag shortcuts here"));
            setClickable(true);
            menu.a.set(1); menu.s.set(1); right.a.set(1); right.s.set(1);
        }

        float pad() { return 6 * d; }
        float barH() { return 64 * d; }
        float sugH() { return 44 * d; }
        int wantHeight() { return Math.round(barH() + sugH() * Math.max(0f, Math.min(1f, rowsS.v))); }
        float barTop() { float t = Math.max(0f, Math.min(1f, topS.v)); return sugH() * Math.max(0f, Math.min(1f, rowsS.v)) * (1 - t); }
        float barCy() { return barTop() + barH() / 2f; }
        boolean inBar(float y) { return y >= barTop() - 4 * d && y <= barTop() + barH() + 4 * d; }
        void rowsChanged() {
            rowsS.t = rowsOn ? 1 : 0;
            topS.t = rowsTop ? 1 : 0;
            setContentDescription(I18n.cs(rowsOn ? (rowsTop ? "Gboard's toolbar, shortcuts above the suggestions: drag shortcuts here" : "Gboard's toolbar, shortcuts below the suggestions: drag shortcuts here") : "Gboard's toolbar: drag shortcuts here"));
            lastFrame = 0;
            postInvalidateOnAnimation();
        }
        final Runnable resize = new Runnable() { public void run() {
            ViewGroup.LayoutParams lp = getLayoutParams();
            int want = wantHeight();
            if (lp != null && lp.height != want) { lp.height = want; setLayoutParams(lp); }
        }};
        float usable() { return (getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels - 32 * d) - 2 * pad(); }
        float side() { return usable() / 9f; }
        float left() { return pad() + (menuOn ? side() : 0); }
        float span() { return pad() + usable() - (rightOn ? side() : 0) - left(); }
        float scale() { float room = span() / (usable() - 2 * side()); return Math.max(1f, Math.min(1.18f, 1f + (room - 1f) * 0.62f)); }

        List<String> shown() {
            List<String> disp = new ArrayList<String>(ed.top);
            if (hoverId != null) disp.remove(hoverId);
            if (hoverIdx >= 0 && !hoverReplace) disp.add(Math.min(hoverIdx, disp.size()), null);
            return disp;
        }

        void hover(String id, int idx, boolean replace) {
            if (TextUtilsEq.eq(hoverId, id) && hoverIdx == idx && hoverReplace == replace) return;
            hoverId = id; hoverIdx = idx; hoverReplace = replace;
            refresh();
        }

        int insertIndex(float rawX, String id, boolean replace) {
            int[] at = new int[2]; getLocationOnScreen(at);
            float x = rawX - at[0] - left();
            List<String> rest = new ArrayList<String>(ed.top);
            rest.remove(id);
            int n = replace ? MAX : rest.size() + 1;
            int idx = (int) Math.floor(x / (span() / Math.max(1, n)));
            return Math.max(0, Math.min(replace ? MAX - 1 : rest.size(), idx));
        }

        boolean isUnder(float rx, float ry) {
            int[] at = new int[2]; getLocationOnScreen(at);
            return ry > at[1] - 28 * d && ry < at[1] + getHeight() + 44 * d && rx > at[0] - 16 * d && rx < at[0] + getWidth() + 16 * d;
        }

        float[] slotScreen(int index) {
            int[] at = new int[2]; getLocationOnScreen(at);
            int n = Math.max(1, ed.top.size());
            return new float[] { at[0] + left() + span() / n * (Math.max(0, index) + 0.5f), at[1] + barCy() };
        }

        void popIn(String id) {
            Item it = items.get(id);
            if (it != null) { it.s.v = 0.4f; it.s.vel = 0; }
            postInvalidateOnAnimation();
        }

        void refresh() {
            List<String> disp = shown();
            int n = Math.max(1, disp.size());
            float slot = span() / n, sc = scale(), l = left();
            int replaceAt = hoverIdx >= 0 && hoverReplace ? Math.min(hoverIdx, disp.size() - 1) : -1;
            for (Item it : items.values()) { it.dying = true; it.a.t = 0; it.s.t = 0.55f; }
            for (int i = 0; i < disp.size(); i++) {
                String id = disp.get(i);
                if (id == null) continue;
                Item it = items.get(id);
                float tx = l + slot * (i + 0.5f);
                if (it == null || it.a.v < 0.02f) {
                    if (it == null) { it = new Item(); items.put(id, it); }
                    it.x.set(tx); it.s.set(0.55f); it.a.set(0);
                }
                it.dying = false;
                it.x.t = tx;
                it.s.t = i == replaceAt ? sc * 0.62f : sc;
                it.a.t = i == replaceAt ? 0.35f : 1f;
            }
            float pd = pad(), sd = side();
            menu.x.t = pd + sd / 2f; menu.a.t = menuOn ? 1 : 0; menu.s.t = menuOn ? 1 : 0.55f;
            right.x.t = pd + usable() - sd / 2f; right.a.t = rightOn ? 1 : 0; right.s.t = rightOn ? 1 : 0.55f;
            if (menu.fresh) { menu.x.set(menu.x.t); menu.fresh = false; }
            if (right.fresh) { right.x.set(right.x.t); right.fresh = false; }
            lastFrame = 0;
            postInvalidateOnAnimation();
        }

        void settle() {
            for (java.util.Iterator<Map.Entry<String, Item>> i = items.entrySet().iterator(); i.hasNext(); ) {
                Item it = i.next().getValue();
                if (it.dying) { i.remove(); continue; }
                it.x.set(it.x.t); it.s.set(it.s.t); it.a.set(it.a.t);
            }
            menu.a.set(menu.a.t); menu.s.set(menu.s.t); right.a.set(right.a.t); right.s.set(right.s.t);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            boolean first = ow == 0;
            refresh();
            if (first) settle();
        }

        String itemAt(float x) {
            List<String> disp = new ArrayList<String>(ed.top);
            if (disp.isEmpty()) return null;
            float slot = span() / disp.size(), rel = x - left();
            if (rel < 0 || rel > span()) return null;
            return disp.get(Math.max(0, Math.min(disp.size() - 1, (int) (rel / slot))));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            lastRawX = e.getRawX(); lastRawY = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); moved = false; lifted = false;
                    downItem = inBar(e.getY()) ? itemAt(e.getX()) : null;
                    if (downItem != null) postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (lifted) { ed.drag(e.getRawX(), e.getRawY()); return true; }
                    if (Math.hypot(e.getX() - downX, e.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        moved = true; removeCallbacks(hold); ed.hideTip();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(hold);
                    if (lifted) { lifted = false; ed.drop(e.getRawX(), e.getRawY()); return true; }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved && inBar(downY)) {
                        float sd = side();
                        if (downX < pad() + sd * 1.1f && menuOn && sideTap != null) { lastTapMenu = true; sideTap.onClick(this); }
                        else if (downX > getWidth() - pad() - sd * 1.1f && rightOn && sideTap != null) { lastTapMenu = false; sideTap.onClick(this); }
                        else if (downItem != null) ed.explainBar(downItem);
                    }
                    return true;
            }
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 1f / 60f : Math.min(1f / 30f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            boolean moving = false;
            float W = getWidth(), H = getHeight(), r = 20 * d;
            box.set(0, 0, W, H);
            if (shadow.getColor() != 0) { RectF sh = new RectF(box); sh.offset(0, 1.5f * d); c.drawRoundRect(sh, r, r, shadow); }
            fill.setColor(panel);
            c.drawRoundRect(box, r, r, fill);
            line.setColor(hoverIdx >= 0 ? accent : stroke);
            line.setStrokeWidth(hoverIdx >= 0 ? 1.5f * d : Math.max(1f, d));
            RectF st = new RectF(box); st.inset(0.75f * d, 0.75f * d);
            c.drawRoundRect(st, r, r, line);
            moving |= rowsS.step(dt, 300f, 34f, 0.003f);
            moving |= topS.step(dt, 300f, 34f, 0.003f);
            if (getLayoutParams() != null && getLayoutParams().height != wantHeight()) { removeCallbacks(resize); post(resize); moving = true; }
            float rv = Math.max(0f, Math.min(1f, rowsS.v));
            if (rv > 0.01f) {
                float t = Math.max(0f, Math.min(1f, topS.v)), sh = sugH() * rv, sy = t * barH();
                c.save();
                c.clipRect(0, sy, W, sy + sh);
                String[] w3 = I18n.all(new String[] { "Hello", "Thanks", "Okay" });
                Paint.FontMetrics fm = words.getFontMetrics();
                float wy = sy + sugH() / 2f - (fm.ascent + fm.descent) / 2f - (1 - rv) * 6 * d * (t > 0.5f ? 1 : -1);
                for (int i = 0; i < 3; i++) {
                    words.setColor(Color.argb(Math.round(Color.alpha(wordInk) * rv), Color.red(wordInk), Color.green(wordInk), Color.blue(wordInk)));
                    words.setTypeface(i == 1 ? I18n.medium() : Typeface.DEFAULT);
                    c.drawText(w3[i], W / 6f * (2 * i + 1), wy, words);
                }
                fill.setColor(Color.argb(Math.round(Color.alpha(sep) * rv), Color.red(sep), Color.green(sep), Color.blue(sep)));
                for (int i = 1; i < 3; i++) c.drawRect(W / 3f * i - 0.5f * d, sy + sh * 0.3f, W / 3f * i + 0.5f * d, sy + sh * 0.7f, fill);
                float ly = t > 0.5f ? sy : sy + sh;
                c.restore();
                fill.setColor(Color.argb(Math.round(Color.alpha(sep) * rv), Color.red(sep), Color.green(sep), Color.blue(sep)));
                c.drawRect(14 * d, ly - 0.5f * d, W - 14 * d, ly + 0.5f * d, fill);
            }
            float cy = barCy(), size = 22 * d;
            moving |= step(menu, dt);
            moving |= step(right, dt);
            drawIcon(c, "menu", menu, cy, size);
            drawIcon(c, "smiley", right, cy, size);
            for (java.util.Iterator<Map.Entry<String, Item>> i = items.entrySet().iterator(); i.hasNext(); ) {
                Item it = i.next().getValue();
                moving |= step(it, dt);
                if (it.dying && it.a.v < 0.01f) i.remove();
            }
            for (Map.Entry<String, Item> en : items.entrySet()) drawIcon(c, en.getKey(), en.getValue(), cy, size);
            if (moving) postInvalidateOnAnimation(); else lastFrame = 0;
        }

        private boolean step(Item it, float dt) {
            boolean m = false;
            m |= it.x.step(dt, 340f, 30f, 0.3f);
            m |= it.s.step(dt, 420f, 26f, 0.002f);
            m |= it.a.step(dt, 300f, 34f, 0.004f);
            return m;
        }

        private void drawIcon(Canvas c, String id, Item it, float cy, float size) {
            float alpha = Math.max(0f, Math.min(1f, it.a.v));
            if (alpha <= 0.01f) return;
            float s = Math.max(0f, it.s.v);
            c.save();
            c.translate(it.x.v, cy);
            c.scale(s * size / 24f, s * size / 24f);
            c.translate(-12, -12);
            Paint p = new Paint(line);
            p.setStrokeWidth(1.9f);
            p.setColor(Color.argb(Math.round(Color.alpha(ink) * alpha), Color.red(ink), Color.green(ink), Color.blue(ink)));
            if ("menu".equals(id)) {
                Paint f = new Paint(p); f.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(4, 4, 11, 11), 2.2f, 2.2f, f); c.drawRoundRect(new RectF(13, 4, 20, 11), 2.2f, 2.2f, f);
                c.drawRoundRect(new RectF(4, 13, 11, 20), 2.2f, 2.2f, f); c.drawRoundRect(new RectF(13, 13, 20, 20), 2.2f, 2.2f, f);
            } else Icons.draw(c, id, p);
            c.restore();
        }
    }

    static final class TextUtilsEq { static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); } }

    static final class Grid extends FrameLayout {
        final Editor ed;
        final Map<String, Tile> tiles = new LinkedHashMap<String, Tile>();
        final TextView label, hintView, note;
        final float d;
        final int page;
        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        int cols = 4, rows = 3, tileW, tileH, gap, gridY, gridH, dotsY;
        float moreX;
        android.view.VelocityTracker velocity;
        android.animation.ValueAnimator pageAnim;
        long edgeSince;
        boolean viewDrag;
        float viewDownX, viewDownY, viewStartX;

        Grid(Context c, Editor ed, List<String> all, int page) {
            super(c);
            this.ed = ed; this.page = page;
            d = c.getResources().getDisplayMetrics().density;
            setClipChildren(false);
            setWillNotDraw(false);
            label = text(c, "Shortcuts", 20, ed.text, true);
            hintView = text(c, "", 14, ed.muted, false);
            note = text(c, "Drag a shortcut to the edge of the list to turn the page.", 13, ed.muted, false);
            addView(label); addView(hintView); addView(note);
            for (String id : all) { Tile t = new Tile(c, id); tiles.put(id, t); addView(t); }
            hint();
        }

        static int mix(int a, int b, float f) {
            f = Math.max(0, Math.min(1, f));
            return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f), Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f), Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
        }

        private TextView text(Context c, String s, int size, int color, boolean bold) {
            TextView t = new TextView(c);
            t.setText(I18n.cs(s)); t.setTextSize(size); t.setTextColor(color);
            if (bold) t.setTypeface(I18n.medium());
            return t;
        }

        void hint() {
            int n = ed.more.size();
            hintView.setText(I18n.cs(ed.top.size() + " of " + MAX + " on the toolbar · " + n + " more" + (pages() > 1 ? " · swipe left or right" : "")));
        }

        Tile tile(String id) { return tiles.get(id); }
        int perPage() { return rows * cols; }
        int pages() { return Math.max(1, (ed.more.size() + perPage() - 1) / perPage()); }
        float inner() { return getWidth() > 0 ? getWidth() - getPaddingLeft() - getPaddingRight() : getMeasuredWidth() - getPaddingLeft() - getPaddingRight(); }
        float stride() { return inner() + gap * 2; }
        int pageNow() { return Math.max(0, Math.min(pages() - 1, Math.round(moreX / Math.max(1f, stride())))); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            int iw = w - getPaddingLeft() - getPaddingRight();
            gap = Math.round(10 * d);
            tileW = (iw - gap * (cols - 1)) / cols;
            tileH = Math.round(86 * d);
            int at = MeasureSpec.makeMeasureSpec(iw, MeasureSpec.AT_MOST), un = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            label.measure(at, un); hintView.measure(at, un);
            note.measure(MeasureSpec.makeMeasureSpec(iw, MeasureSpec.EXACTLY), un);
            for (Tile t : tiles.values()) t.measure(MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tileH, MeasureSpec.EXACTLY));
            gridY = getPaddingTop() + label.getMeasuredHeight() + Math.round(2 * d) + hintView.getMeasuredHeight() + Math.round(14 * d);
            int bottom = h - getPaddingBottom() - note.getMeasuredHeight() - Math.round(34 * d);
            rows = Math.max(2, Math.min(6, (bottom - gridY + gap) / (tileH + gap)));
            gridH = rows * tileH + (rows - 1) * gap;
            dotsY = gridY + gridH + Math.round(16 * d);
            moreX = Math.max(0, Math.min(moreX, (pages() - 1) * (iw + gap * 2)));
            setMeasuredDimension(w, h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = getPaddingLeft(), y = getPaddingTop();
            label.layout(x, y, x + label.getMeasuredWidth(), y + label.getMeasuredHeight());
            y += label.getMeasuredHeight() + Math.round(2 * d);
            hintView.layout(x, y, x + hintView.getMeasuredWidth(), y + hintView.getMeasuredHeight());
            int ny = getHeight() - getPaddingBottom() - note.getMeasuredHeight();
            note.layout(x, ny, x + note.getMeasuredWidth(), ny + note.getMeasuredHeight());
            relayout(true);
        }

        float[] slot(String id) {
            int i = ed.more.indexOf(id);
            if (i < 0) i = 0;
            int pg = i / perPage(), in = i % perPage();
            return new float[] { getPaddingLeft() + pg * stride() - moreX + (in % cols) * (tileW + gap), gridY + (in / cols) * (tileH + gap) };
        }

        float[] slotScreen(String id) {
            float[] s = slot(id);
            int[] at = new int[2]; getLocationOnScreen(at);
            return new float[] { at[0] + s[0], at[1] + s[1] };
        }

        void appear(String id) {
            Tile t = tiles.get(id);
            if (t == null) return;
            float[] at = slot(id);
            t.animate().cancel(); t.fading = false;
            t.setX(at[0]); t.setY(at[1]);
            t.setScaleX(0.6f); t.setScaleY(0.6f); t.setAlpha(0f);
            t.setVisibility(View.VISIBLE);
            t.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(EASE).start();
        }

        private int laidPerPage = -1;
        private int laidGridH = -1, laidDotsY = -1;
        private float shownGridH = -1, shownDotsY = -1;
        private android.animation.ValueAnimator reflowAnim;

        void relayout(boolean animate) {
            boolean reflow = laidPerPage != -1 && laidPerPage != perPage();
            laidPerPage = perPage();
            if (reflow) {
                animate = false;
                final float fromH = shownGridH >= 0 ? shownGridH : laidGridH, fromD = shownDotsY >= 0 ? shownDotsY : laidDotsY;
                final float toH = gridH, toD = dotsY;
                if (reflowAnim != null) reflowAnim.cancel();
                if (fromH >= 0 && fromD >= 0 && (fromH != toH || fromD != toD)) {
                    reflowAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
                    reflowAnim.setDuration(260);
                    reflowAnim.setInterpolator(EASE);
                    reflowAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator va) {
                        float f = (Float) va.getAnimatedValue();
                        shownGridH = fromH + (toH - fromH) * f; shownDotsY = fromD + (toD - fromD) * f;
                        if (f >= 1f) { shownGridH = -1; shownDotsY = -1; }
                        invalidate();
                    }});
                    reflowAnim.start();
                }
            }
            laidGridH = gridH; laidDotsY = dotsY;
            int arriving = 0;
            for (Map.Entry<String, Tile> en : tiles.entrySet()) {
                final Tile tile = en.getValue();
                String id = en.getKey();
                if (tile.getWidth() != tileW || tile.getHeight() != tileH) tile.layout(0, 0, tileW, tileH);
                if (!ed.more.contains(id)) {
                    if (!id.equals(ed.dragId) || ed.fromBar) { tile.animate().cancel(); tile.fading = false; tile.setVisibility(View.INVISIBLE); }
                    continue;
                }
                final float[] at = slot(id);
                if (tile.fading && !reflow && android.os.SystemClock.uptimeMillis() < tile.fadeEnds) continue;
                boolean show = at[0] + tileW > -gap && at[0] < getWidth() + gap;
                boolean was = tile.getVisibility() == View.VISIBLE && tile.placed;
                boolean moves = Math.abs(tile.getX() - at[0]) > 0.5f || Math.abs(tile.getY() - at[1]) > 0.5f;
                if (reflow && was && !show) {
                    tile.animate().cancel(); tile.fading = true; tile.fadeEnds = android.os.SystemClock.uptimeMillis() + 600;
                    tile.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(190).setInterpolator(EASE).withEndAction(new Runnable() { public void run() {
                        tile.setVisibility(View.INVISIBLE); tile.setAlpha(1f); tile.setScaleX(1f); tile.setScaleY(1f); tile.setX(at[0]); tile.setY(at[1]); tile.fading = false;
                    }}).start();
                    continue;
                }
                if (reflow && was && show && moves) {
                    tile.animate().cancel(); tile.fading = true; tile.fadeEnds = android.os.SystemClock.uptimeMillis() + 600;
                    tile.animate().alpha(0f).setDuration(110).setInterpolator(EASE).withEndAction(new Runnable() { public void run() {
                        tile.setX(at[0]); tile.setY(at[1]); tile.setScaleX(0.92f); tile.setScaleY(0.92f);
                        tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(EASE).withEndAction(new Runnable() { public void run() { tile.fading = false; } }).start();
                    }}).start();
                    continue;
                }
                if (!tile.placed || tile.getVisibility() != View.VISIBLE) {
                    tile.setX(at[0]); tile.setY(at[1]); tile.placed = true;
                } else if (moves) {
                    if (animate) tile.animate().x(at[0]).y(at[1]).setDuration(260).setInterpolator(EASE).start();
                    else { tile.animate().cancel(); tile.setX(at[0]); tile.setY(at[1]); }
                }
                tile.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                if (reflow && show && !was) {
                    tile.animate().cancel(); tile.fading = true; tile.fadeEnds = android.os.SystemClock.uptimeMillis() + 600;
                    tile.setAlpha(0f); tile.setScaleX(0.92f); tile.setScaleY(0.92f);
                    tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(40 + 26 * Math.min(arriving++, 8)).setDuration(220).setInterpolator(EASE)
                            .withEndAction(new Runnable() { public void run() { tile.animate().setStartDelay(0); tile.fading = false; } }).start();
                }
            }
            invalidate();
        }

        @Override protected boolean drawChild(Canvas c, View child, long time) {
            if (child instanceof Tile) {
                c.save();
                c.clipRect(0, gridY - gap, getWidth(), gridY + (shownGridH >= 0 ? shownGridH : gridH) + gap);
                boolean r = super.drawChild(c, child, time);
                c.restore();
                return r;
            }
            return super.drawChild(c, child, time);
        }

        @Override protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);
            int n = pages();
            float pos = moreX / Math.max(1f, stride()), r = 3.5f * d, spacing = 10 * d, total = 0;
            float[] widths = new float[n];
            for (int i = 0; i < n; i++) { float f = Math.max(0f, 1f - Math.abs(pos - i)); widths[i] = 2 * r + 14 * d * f; total += widths[i]; }
            total += spacing * (n - 1);
            float x = getWidth() / 2f - total / 2f;
            for (int i = 0; i < n; i++) {
                float f = Math.max(0f, 1f - Math.abs(pos - i));
                dot.setColor(mix(Color.argb(90, Color.red(ed.muted), Color.green(ed.muted), Color.blue(ed.muted)), ed.accent, f));
                float dy = shownDotsY >= 0 ? shownDotsY : dotsY;
                c.drawRoundRect(x, dy - r, x + widths[i], dy + r, r, r, dot);
                x += widths[i] + spacing;
            }
        }

        boolean inPages(float y) { return y >= gridY - gap && y <= dotsY + 16 * d; }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (ed.dragId != null) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    viewDrag = false;
                    viewDownX = e.getX(); viewDownY = e.getY(); viewStartX = moreX;
                    if (velocity != null) velocity.clear(); else velocity = android.view.VelocityTracker.obtain();
                    velocity.addMovement(e);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (velocity != null) velocity.addMovement(e);
                    float dx = Math.abs(e.getX() - viewDownX), dy = Math.abs(e.getY() - viewDownY);
                    if (inPages(viewDownY) && dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() && dx > dy) {
                        viewDrag = true;
                        ed.hideTip();
                        if (pageAnim != null) pageAnim.cancel();
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return false;
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (!viewDrag) {
                if (a == MotionEvent.ACTION_DOWN) return inPages(e.getY());
                if (a == MotionEvent.ACTION_MOVE && inPages(viewDownY)) {
                    if (velocity != null) velocity.addMovement(e);
                    float dx = Math.abs(e.getX() - viewDownX), dy = Math.abs(e.getY() - viewDownY);
                    if (dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() && dx > dy) {
                        viewDrag = true;
                        ed.hideTip();
                        if (pageAnim != null) pageAnim.cancel();
                    }
                    return true;
                }
                return inPages(viewDownY);
            }
            if (velocity != null) velocity.addMovement(e);
            float max = (pages() - 1) * stride();
            switch (a) {
                case MotionEvent.ACTION_MOVE: {
                    float x = viewStartX - (e.getX() - viewDownX);
                    if (x < 0) x = x * 0.35f; else if (x > max) x = max + (x - max) * 0.35f;
                    moreX = x;
                    relayout(false);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    viewDrag = false;
                    int target = Math.round(moreX / Math.max(1f, stride()));
                    if (velocity != null) {
                        velocity.computeCurrentVelocity(1000);
                        float vx = velocity.getXVelocity();
                        int startPage = Math.round(viewStartX / Math.max(1f, stride()));
                        if (vx < -500 * d) target = startPage + 1; else if (vx > 500 * d) target = startPage - 1;
                    }
                    toPage(target);
                    return true;
                }
            }
            return true;
        }

        void toPage(int target) {
            target = Math.max(0, Math.min(pages() - 1, target));
            final float from = moreX, to = target * stride();
            if (pageAnim != null) pageAnim.cancel();
            if (Math.abs(from - to) < 0.5f) { moreX = to; relayout(false); return; }
            if (target != pageNow()) ed.haptic(this, HapticFeedbackConstants.CLOCK_TICK);
            pageAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            pageAnim.setDuration(300);
            pageAnim.setInterpolator(EASE);
            pageAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator va) { moreX = from + (to - from) * (Float) va.getAnimatedValue(); relayout(false); }
            });
            pageAnim.start();
        }

        void edgeTurn(float rx, float ry) {
            int[] at = new int[2]; getLocationOnScreen(at);
            float fx = rx - at[0], fy = ry - at[1];
            if (fy > gridY - gap && fy < gridY + gridH + gap && (fx < 28 * d || fx > getWidth() - 28 * d)) {
                long now = android.os.SystemClock.uptimeMillis();
                if (edgeSince == 0) edgeSince = now;
                else if (now - edgeSince > 450) { edgeSince = now + 200; toPage(pageNow() + (fx < 28 * d ? -1 : 1)); }
            } else edgeSince = 0;
        }

        final class Tile extends View {
            final String id;
            final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG);
            boolean placed, lifted; boolean fading; long fadeEnds;
            float downX, downY;
            boolean pressedDown, moved;
            float rawX, rawY;
            final Runnable longPress = new Runnable() { public void run() { if (pressedDown && !moved) { lifted = true; animate().scaleX(1f).scaleY(1f).setDuration(80).start(); ed.liftFromGrid(Tile.this, rawX, rawY); } } };

            Tile(Context c, String id) {
                super(c);
                this.id = id;
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(1.9f * d);
                ink.setStrokeCap(Paint.Cap.ROUND);
                ink.setStrokeJoin(Paint.Join.ROUND);
                word.setTextAlign(Paint.Align.CENTER);
                word.setTextSize(13.5f * d);
                word.setTypeface(I18n.medium());
                setContentDescription(I18n.cs(GboardBarEditor.label(id)));
                setClickable(true);
            }

            @Override protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight(), r = 18 * d;
                fill.setColor(ed.card);
                c.drawRoundRect(new RectF(0, 0, w, h), r, r, fill);
                ink.setColor(ed.text);
                word.setColor(ed.text);
                Paint hp = new Paint(ink); hp.setColor(ed.muted); hp.setStrokeWidth(1.4f * d);
                float hx = w - 15 * d, hy = 15 * d, hs = 5 * d, ah = 2.2f * d;
                c.drawLine(hx - hs, hy, hx + hs, hy, hp); c.drawLine(hx, hy - hs, hx, hy + hs, hp);
                c.drawLine(hx - hs, hy, hx - hs + ah, hy - ah, hp); c.drawLine(hx - hs, hy, hx - hs + ah, hy + ah, hp);
                c.drawLine(hx + hs, hy, hx + hs - ah, hy - ah, hp); c.drawLine(hx + hs, hy, hx + hs - ah, hy + ah, hp);
                c.drawLine(hx, hy - hs, hx - ah, hy - hs + ah, hp); c.drawLine(hx, hy - hs, hx + ah, hy - hs + ah, hp);
                c.drawLine(hx, hy + hs, hx - ah, hy + hs - ah, hp); c.drawLine(hx, hy + hs, hx + ah, hy + hs - ah, hp);
                c.save();
                float size = 24 * d;
                c.translate(w / 2f - size / 2f, h * 0.2f);
                c.scale(size / 24f, size / 24f);
                Paint p = new Paint(ink); p.setStrokeWidth(1.9f);
                Icons.draw(c, id, p);
                c.restore();
                String name = GboardBarEditor.label(id);
                float y = h * 0.8f;
                float size0 = word.getTextSize();
                if (word.measureText(name) > w - 12 * d && name.indexOf(' ') > 0) {
                    int sp = name.indexOf(' ');
                    I18n.fit(word, w - 8 * d, name.substring(0, sp), name.substring(sp + 1));
                    c.drawText(name.substring(0, sp), w / 2f, y - 8 * d, word);
                    c.drawText(name.substring(sp + 1), w / 2f, y + 8 * d, word);
                } else { I18n.fit(word, w - 8 * d, name); c.drawText(name, w / 2f, y, word); }
                word.setTextSize(size0);
            }

            @Override public boolean onTouchEvent(MotionEvent e) {
                rawX = e.getRawX(); rawY = e.getRawY();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); pressedDown = true; moved = false; lifted = false;
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                        animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (lifted) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); ed.drag(rawX, rawY); return true; }
                        if (Math.hypot(e.getRawX() - downX, e.getRawY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                            moved = true; removeCallbacks(longPress);
                            ed.hideTip();
                            animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                            return false;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        removeCallbacks(longPress);
                        pressedDown = false;
                        if (lifted) { lifted = false; ed.drop(rawX, rawY); return true; }
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved) ed.explainTile(this);
                        return true;
                }
                return super.onTouchEvent(e);
            }
        }
    }

    static String topTitle(boolean top) { return I18n.t(top ? "Shortcuts row on top" : "Shortcuts row on bottom"); }
    static String topSummary(boolean top) { return I18n.t(top ? "Shortcuts above the suggestions, not below" : "Shortcuts below the suggestions, not above"); }

    static final class Typist {
        static final long DEL = 10, TYPE = 19, FLY = 70, FLY_FAR = 50, LEAD = 30, CARET_IN = 60, CARET_OUT = 200;

        interface Measure {
            float x(int line, String text);
            float top(int line);
            float bottom(int line);
            float dp();
        }

        static final class Frame {
            final String[] a, b, c;
            final float[] v;
            float cx, top, bottom, alpha;
            long rest;
            Frame(int n) { a = new String[n]; b = new String[n]; c = new String[n]; v = new float[n]; }
        }

        private static final class Run {
            String[][] from, to;
            int[] line, word, keep;
            long[] fly, at, end;
            float[] hop;
            long start, total;
            boolean flyIn;
            float ox, oTop, oBottom, oAlpha;

            Frame frame(long now, Measure m) {
                int L = from.length, n = line.length, k = 0;
                Frame f = new Frame(L);
                long t = now - start;
                while (k < n && t >= end[k]) k++;
                String[][] w = new String[L][];
                for (int l = 0; l < L; l++) w[l] = from[l].clone();
                for (int j = 0; j < k; j++) w[line[j]][word[j]] = to[line[j]][word[j]];
                for (int l = 0; l < L; l++) { f.a[l] = join(w[l]); f.b[l] = ""; f.c[l] = ""; }
                if (k < n && t >= at[k]) {
                    int l = line[k], i = word[k], p = keep[k];
                    String a = from[l][i], b = to[l][i], vis, letter;
                    long e = t - at[k], dels = a.length() - p;
                    float v;
                    if (e < dels * DEL) {
                        int gone = (int) (e / DEL);
                        vis = a.substring(0, a.length() - gone - 1);
                        letter = String.valueOf(a.charAt(a.length() - gone - 1));
                        v = 1f - ease((e % DEL) / (float) DEL);
                        f.rest = e % DEL == 0 ? 0 : DEL - e % DEL;
                    } else {
                        long e2 = e - dels * DEL;
                        int typed = (int) (e2 / TYPE);
                        vis = b.substring(0, p + typed);
                        letter = String.valueOf(b.charAt(p + typed));
                        v = ease((e2 % TYPE) / (float) TYPE);
                        f.rest = e2 % TYPE == 0 ? 0 : TYPE - e2 % TYPE;
                    }
                    StringBuilder before = new StringBuilder(), after = new StringBuilder();
                    for (int j = 0; j < i; j++) before.append(w[l][j]).append(' ');
                    before.append(vis);
                    for (int j = i + 1; j < w[l].length; j++) after.append(' ').append(w[l][j]);
                    f.a[l] = before.toString(); f.b[l] = letter; f.v[l] = v; f.c[l] = after.toString();
                    float xa = m.x(l, f.a[l]);
                    f.cx = xa + (m.x(l, f.a[l] + letter) - xa) * v;
                    f.top = m.top(l); f.bottom = m.bottom(l);
                } else if (k < n) {
                    int l = line[k];
                    float x1 = m.x(l, prefix(w[l], word[k])), t1 = m.top(l), b1 = m.bottom(l), x0, t0, b0;
                    if (k > 0) { int q = line[k - 1]; x0 = m.x(q, prefix(w[q], word[k - 1])); t0 = m.top(q); b0 = m.bottom(q); }
                    else if (flyIn) { x0 = ox; t0 = oTop; b0 = oBottom; }
                    else { x0 = x1; t0 = t1; b0 = b1; }
                    float u = at[k] > fly[k] ? (t - fly[k]) / (float) (at[k] - fly[k]) : 1f, s = glide(u);
                    float arc = hop[k] * 4f * s * (1f - s);
                    f.cx = x0 + (x1 - x0) * s;
                    f.top = t0 + (t1 - t0) * s - arc;
                    f.bottom = b0 + (b1 - b0) * s - arc;
                } else if (n > 0) {
                    int l = line[n - 1];
                    f.cx = m.x(l, prefix(w[l], word[n - 1]));
                    f.top = m.top(l); f.bottom = m.bottom(l);
                } else { f.cx = ox; f.top = oTop; f.bottom = oBottom; }
                float in = n == 0 ? oAlpha : oAlpha + (1f - oAlpha) * ease(t / (float) CARET_IN);
                float out = t <= total ? 1f : 1f - ease((t - total) / (float) CARET_OUT);
                f.alpha = in * out;
                return f;
            }
        }

        private final String[] plain;
        private Run run, old;

        Typist(String... lines) { plain = lines.clone(); }

        String target(int l) { return run != null ? join(run.to[l]) : plain[l]; }

        boolean running(long now) { return run != null && now - run.start < run.total + CARET_OUT; }

        private void settle(long now) {
            if (old != null && now >= run.start) old = null;
            if (run != null && now - run.start >= run.total + CARET_OUT) {
                for (int l = 0; l < plain.length; l++) plain[l] = join(run.to[l]);
                run = old = null;
            }
        }

        Frame frame(long now, Measure m) {
            settle(now);
            if (old != null) return old.frame(now, m);
            if (run != null) return run.frame(now, m);
            Frame f = new Frame(plain.length);
            for (int l = 0; l < plain.length; l++) { f.a[l] = plain[l]; f.b[l] = ""; f.c[l] = ""; }
            return f;
        }

        void set(String[] texts, long now, boolean animate, Measure m) {
            settle(now);
            if (old != null) { run = old; old = null; }
            Run prev = run;
            boolean same = true;
            for (int l = 0; l < plain.length; l++) same &= texts[l].equals(target(l));
            if (same) return;
            int L = plain.length;
            long when = now;
            Frame f = null;
            if (prev != null) {
                f = prev.frame(now, m);
                if (animate && f.rest > 0) { when = now + f.rest; f = prev.frame(when, m); }
            }
            String[][] cur = new String[L][], next = new String[L][];
            for (int l = 0; l < L; l++) {
                String shown = f != null ? f.a[l] + (f.v[l] >= 0.5f ? f.b[l] : "") + f.c[l] : plain[l];
                cur[l] = shown.split(" ", -1);
                next[l] = texts[l].split(" ", -1);
                if (cur[l].length != next[l].length) { cur[l] = new String[] { shown }; next[l] = new String[] { texts[l] }; }
            }
            List<int[]> edits = new ArrayList<int[]>();
            for (int l = 0; l < L; l++) for (int i = 0; i < next[l].length; i++) {
                String a = cur[l][i], b = next[l][i];
                if (a.equals(b)) continue;
                int p = 0;
                while (p < a.length() && p < b.length() && a.charAt(p) == b.charAt(p)) p++;
                edits.add(new int[] { l, i, p });
            }
            boolean showing = f != null && f.alpha > 0.01f;
            if (!animate || (edits.isEmpty() && !showing)) {
                for (int l = 0; l < L; l++) plain[l] = texts[l];
                run = old = null;
                return;
            }
            Run r = new Run();
            int n = edits.size();
            r.line = new int[n]; r.word = new int[n]; r.keep = new int[n];
            r.fly = new long[n]; r.at = new long[n]; r.end = new long[n]; r.hop = new float[n];
            r.flyIn = showing;
            if (r.flyIn) { r.ox = f.cx; r.oTop = f.top; r.oBottom = f.bottom; r.oAlpha = f.alpha; }
            String[][] state = new String[L][];
            for (int l = 0; l < L; l++) state[l] = cur[l].clone();
            float px = r.ox, py = (r.oTop + r.oBottom) / 2f, dp = m.dp();
            long t = 0;
            for (int k = 0; k < n; k++) {
                int[] e = edits.get(k);
                int l = e[0], i = e[1], p = e[2];
                r.line[k] = l; r.word[k] = i; r.keep[k] = p;
                float x = m.x(l, prefix(state[l], i)), y = (m.top(l) + m.bottom(l)) / 2f;
                long d = k == 0 && !r.flyIn ? LEAD : 0;
                if (k > 0 || r.flyIn) {
                    float dist = (float) Math.hypot(x - px, y - py);
                    d = dist < 1f ? 0 : Math.round(Math.sqrt(Math.min(1f, dist / (40f * dp))) * (FLY + FLY_FAR * Math.min(1f, dist / (200f * dp))));
                    r.hop[k] = Math.min(6f * dp, dist * 0.12f);
                }
                r.fly[k] = t; t += d; r.at[k] = t;
                t += (cur[l][i].length() - p) * DEL + (next[l][i].length() - p) * TYPE;
                r.end[k] = t;
                state[l][i] = next[l][i];
                px = m.x(l, prefix(state[l], i)); py = y;
            }
            r.from = cur; r.to = next; r.total = t; r.start = when;
            old = when > now ? prev : null;
            run = r;
        }

        private static String prefix(String[] w, int i) {
            StringBuilder b = new StringBuilder();
            for (int j = 0; j <= i; j++) { if (j > 0) b.append(' '); b.append(w[j]); }
            return b.toString();
        }

        private static float ease(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3f - 2f * x); }

        private static float glide(float u) {
            u = Math.max(0f, Math.min(1f, u));
            float lo = 0f, hi = 1f, s = u;
            for (int i = 0; i < 18; i++) {
                s = (lo + hi) / 2f;
                if (bez(s, 0.3f, 0.1f) < u) lo = s; else hi = s;
            }
            return bez(s, 0f, 1f);
        }

        private static float bez(float s, float p1, float p2) {
            float r = 1f - s;
            return 3f * r * r * s * p1 + 3f * r * s * s * p2 + s * s * s;
        }

        private static String join(String[] w) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < w.length; i++) { if (i > 0) b.append(' '); b.append(w[i]); }
            return b.toString();
        }
    }

    static final class Toggle extends View {
        final float d;
        final Typist words;
        final int ink, muted, accent, trackOff;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t1 = new Paint(Paint.ANTI_ALIAS_FLAG), t2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint.FontMetrics m1, m2;
        final Typist.Measure measure = new Typist.Measure() {
            public float x(int l, String s) { return 16 * d + (l == 0 ? t1 : t2).measureText(s); }
            public float top(int l) { return base(l) + (l == 0 ? m1 : m2).ascent * 0.92f; }
            public float bottom(int l) { return base(l) + (l == 0 ? m1 : m2).descent * 0.9f; }
            public float dp() { return d; }
        };
        final Spring on = new Spring(), en = new Spring();
        long lastFrame;

        Toggle(Context c, String title, String summary, boolean value, boolean dark, int ink, int muted, int accent) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            this.words = new Typist(title, summary); this.ink = ink; this.muted = muted; this.accent = accent;
            trackOff = !dark ? 0xFFDADCE0 : 0xFF3A3A3C;
            on.set(value ? 1 : 0); en.set(1);
            t1.setTextSize(15.5f * d);
            t1.setTypeface(I18n.medium());
            t2.setTextSize(13 * d);
            m1 = t1.getFontMetrics(); m2 = t2.getFontMetrics();
            setClickable(true);
            describe();
        }

        void set(boolean v) { on.t = v ? 1 : 0; describe(); lastFrame = 0; postInvalidateOnAnimation(); }
        void enable(boolean e) { en.t = e ? 1 : 0; setEnabled(e); describe(); lastFrame = 0; postInvalidateOnAnimation(); }
        private void describe() { setContentDescription(I18n.cs(words.target(0) + ", " + (on.t > 0.5f ? "on" : "off") + (en.t < 0.5f ? ", needs Suggestions and shortcuts together" : ""))); }

        void retext(String t, String s) {
            boolean animate = true;
            try { animate = android.animation.ValueAnimator.areAnimatorsEnabled(); } catch (Throwable ignored) { }
            words.set(new String[] { t, s }, android.os.SystemClock.uptimeMillis(), animate, measure);
            describe(); lastFrame = 0; postInvalidateOnAnimation();
        }

        float base(int l) {
            float h1 = m1.descent - m1.ascent, h2 = m2.descent - m2.ascent, top = getHeight() / 2f - (h1 + h2 + 2 * d) / 2f;
            return l == 0 ? top - m1.ascent : top + h1 + 2 * d - m2.ascent;
        }

        private void line(Canvas c, Typist.Frame f, int l, Paint paint, float room) {
            float x = 16 * d, baseline = base(l);
            String a = f.a[l], b = f.b[l], rest = f.c[l];
            if (paint.measureText(a + b + rest) > room) { c.drawText(fit(words.target(l), paint, room), x, baseline, paint); return; }
            c.drawText(a, x, baseline, paint);
            float xa = x + paint.measureText(a), adv = b.isEmpty() ? 0f : x + paint.measureText(a + b) - xa, v = f.v[l];
            if (!b.isEmpty() && v > 0f) {
                int alpha = paint.getAlpha();
                float size = paint.getTextSize();
                c.save();
                c.clipRect(xa - d, baseline - 2 * size, xa + (adv + d) * v, baseline + size);
                paint.setAlpha(Math.round(alpha * v));
                c.drawText(b, xa, baseline, paint);
                paint.setAlpha(alpha);
                c.restore();
            }
            if (!rest.isEmpty()) c.drawText(rest, xa + adv * v, baseline, paint);
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 1f / 60f : Math.min(1f / 30f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            boolean moving = on.step(dt, 380f, 30f, 0.003f);
            moving |= en.step(dt, 300f, 34f, 0.004f);
            float W = getWidth(), H = getHeight(), f = Math.max(0f, Math.min(1f, on.v)), e = 0.4f + 0.6f * Math.max(0f, Math.min(1f, en.v));
            float tw = 52 * d, th = 32 * d, tx = W - 16 * d - tw, ty = H / 2f - th / 2f;
            float room = tx - 16 * d - 12 * d;
            t1.setColor(ink); t1.setAlpha(Math.round(255 * e));
            t2.setColor(muted); t2.setAlpha(Math.round(Color.alpha(muted) * e));
            long ms = android.os.SystemClock.uptimeMillis();
            Typist.Frame tf = words.frame(ms, measure);
            line(c, tf, 0, t1, room);
            line(c, tf, 1, t2, room);
            if (tf.alpha > 0f) {
                p.setColor(accent); p.setAlpha(Math.round(Color.alpha(accent) * e * Math.min(1f, tf.alpha)));
                float cx = Math.min(tf.cx, 16 * d + room) + 1.2f * d;
                c.drawRoundRect(cx, tf.top, cx + 1.6f * d, tf.bottom, 0.8f * d, 0.8f * d, p);
            }
            moving |= words.running(ms);
            int track = Chip.mixStatic(trackOff, accent, f);
            p.setColor(track); p.setAlpha(Math.round(Color.alpha(track) * e));
            c.drawRoundRect(tx, ty, tx + tw, ty + th, th / 2f, th / 2f, p);
            float kr = (10 + 2 * f) * d, kx = tx + th / 2f + (tw - th) * f;
            p.setColor(Color.WHITE); p.setAlpha(Math.round(255 * e));
            c.drawCircle(kx, H / 2f, kr, p);
            if (moving) postInvalidateOnAnimation(); else lastFrame = 0;
        }

        private static String fit(String s, Paint p, float w) {
            if (p.measureText(s) <= w) return s;
            int n = p.breakText(s, true, w - p.measureText("…"), null);
            return s.substring(0, Math.max(0, n)).trim() + "…";
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) animate().scaleX(0.985f).scaleY(0.985f).setDuration(90).start();
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(EASE).start();
            return super.onTouchEvent(e);
        }
    }

    static final class Chip extends View {
        final float d;
        final String icon, text;
        final int ink, muted, accent;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Spring on = new Spring();
        long lastFrame;

        Chip(Context c, String icon, String text, boolean shown, boolean dark, int ink, int muted, int accent) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            this.icon = icon; this.text = I18n.t(text); this.ink = ink; this.muted = muted; this.accent = accent;
            on.set(shown ? 1 : 0);
            word.setTextSize(15.5f * d);
            word.setTypeface(I18n.medium());
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            setClickable(true);
            describe();
        }

        void set(boolean shown) { on.t = shown ? 1 : 0; describe(); lastFrame = 0; postInvalidateOnAnimation(); }

        private void describe() { setContentDescription(I18n.cs(text + (on.t > 0.5f ? ", shown" : ", hidden"))); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round(word.measureText(text) + 62 * d), MeasureSpec.getSize(h));
        }

        static int mixStatic(int a, int b, float f) { return mix(a, b, f); }

        private static int mix(int a, int b, float f) {
            f = Math.max(0, Math.min(1, f));
            return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f), Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f), Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = lastFrame == 0 ? 1f / 60f : Math.min(1f / 30f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            boolean moving = on.step(dt, 300f, 34f, 0.004f);
            float f = on.v, W = getWidth(), H = getHeight(), r = H / 2f;
            int accentSoft = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent));
            fill.setColor(mix(0x00000000 | (accentSoft & 0x00FFFFFF), accentSoft, f));
            c.drawRoundRect(0, 0, W, H, r, r, fill);
            line.setStrokeWidth(Math.max(1f, d));
            line.setColor(mix(Color.argb(90, Color.red(muted), Color.green(muted), Color.blue(muted)), accent, f));
            c.drawRoundRect(0.5f * d, 0.5f * d, W - 0.5f * d, H - 0.5f * d, r, r, line);
            int col = mix(muted, ink, f);
            c.save();
            float size = 20 * d;
            c.translate(14 * d, H / 2f - size / 2f);
            c.scale(size / 24f, size / 24f);
            Paint p = new Paint(line); p.setStrokeWidth(1.9f); p.setColor(col);
            if ("menu".equals(icon)) {
                Paint q = new Paint(p); q.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(4, 4, 11, 11), 2.2f, 2.2f, q); c.drawRoundRect(new RectF(13, 4, 20, 11), 2.2f, 2.2f, q);
                c.drawRoundRect(new RectF(4, 13, 11, 20), 2.2f, 2.2f, q); c.drawRoundRect(new RectF(13, 13, 20, 20), 2.2f, 2.2f, q);
            } else Icons.draw(c, icon, p);
            if (f < 0.99f) {
                Paint sl = new Paint(p); sl.setStrokeWidth(2.2f); sl.setAlpha(Math.round(255 * (1 - f)));
                c.drawLine(3, 21, 3 + 18 * (1 - f), 21 - 18 * (1 - f), sl);
            }
            c.restore();
            word.setColor(col);
            Paint.FontMetrics fm = word.getFontMetrics();
            c.drawText(text, 42 * d, H / 2f - (fm.ascent + fm.descent) / 2f, word);
            if (moving) postInvalidateOnAnimation(); else lastFrame = 0;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(EASE).start();
            return super.onTouchEvent(e);
        }
    }

    static final class Spring {
        float v, vel, t;
        void set(float x) { v = t = x; vel = 0; }
        boolean step(float dt, float stiffness, float damping, float eps) {
            float f = -stiffness * (v - t) - damping * vel;
            vel += f * dt;
            v += vel * dt;
            if (Math.abs(v - t) < eps && Math.abs(vel) < eps * 8) { v = t; vel = 0; return false; }
            return true;
        }
    }

    static final class Icons {
        static void draw(Canvas c, String id, Paint p) {
            Path path = new Path();
            Paint fill = new Paint(p); fill.setStyle(Paint.Style.FILL);
            switch (id) {
                case "clipboard": case "pixelsuite_paste":
                    c.drawRoundRect(new RectF(5, 5, 19, 21), 2, 2, p);
                    c.drawRoundRect(new RectF(9, 3, 15, 7), 1.2f, 1.2f, p);
                    if (id.equals("clipboard")) { c.drawLine(8.5f, 12, 15.5f, 12, p); c.drawLine(8.5f, 16, 13.5f, 16, p); }
                    break;
                case "jarvis":
                    path.moveTo(6, 18); path.lineTo(15, 9); path.lineTo(17, 11); path.lineTo(8, 20); path.lineTo(5.5f, 20.5f); path.close();
                    c.drawPath(path, p);
                    star(c, 7, 7, 2.6f, fill); star(c, 18.5f, 4.5f, 1.7f, fill); star(c, 19, 16, 1.5f, fill);
                    break;
                case "translate":
                    c.drawRoundRect(new RectF(3, 4, 14, 15), 2, 2, p);
                    c.drawRoundRect(new RectF(10, 9, 21, 20), 2, 2, p);
                    Paint t = new Paint(fill); t.setTextSize(7.5f); t.setTextAlign(Paint.Align.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText("G", 8.5f, 12.2f, t); c.drawText("文", 15.5f, 17.5f, t);
                    break;
                case "smiley":
                    c.drawCircle(12, 12, 8.5f, p);
                    c.drawCircle(9, 10, 1.1f, fill); c.drawCircle(15, 10, 1.1f, fill);
                    path.moveTo(8.5f, 14); path.quadTo(12, 17.5f, 15.5f, 14); c.drawPath(path, p);
                    break;
                case "gif_search":
                    c.drawRoundRect(new RectF(3, 6, 21, 18), 2.5f, 2.5f, p);
                    Paint g = new Paint(fill); g.setTextSize(7.5f); g.setTextAlign(Paint.Align.CENTER); g.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText("GIF", 12, 14.6f, g);
                    break;
                case "sticker":
                    path.moveTo(5, 5); path.lineTo(19, 5); path.lineTo(19, 14); path.lineTo(14, 19); path.lineTo(5, 19); path.close();
                    c.drawPath(path, p);
                    c.drawCircle(9.5f, 10, 0.9f, fill); c.drawCircle(14.5f, 10, 0.9f, fill);
                    break;
                case "theme_setting":
                    path.moveTo(12, 3.5f); path.cubicTo(6.5f, 3.5f, 3.5f, 7.5f, 3.5f, 12); path.cubicTo(3.5f, 17, 7.5f, 20.5f, 11.5f, 20.5f);
                    path.cubicTo(13, 20.5f, 13.2f, 19.2f, 12.6f, 18.3f); path.cubicTo(12, 17.3f, 12.6f, 16, 14, 16); path.lineTo(16.5f, 16);
                    path.cubicTo(19, 16, 20.5f, 14.2f, 20.5f, 11.8f); path.cubicTo(20.5f, 7, 16.5f, 3.5f, 12, 3.5f);
                    c.drawPath(path, p);
                    c.drawCircle(8, 11, 1.1f, fill); c.drawCircle(10.5f, 7.5f, 1.1f, fill); c.drawCircle(14.5f, 7.5f, 1.1f, fill); c.drawCircle(17, 11, 1.1f, fill);
                    break;
                case "settings":
                    c.drawCircle(12, 12, 3, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        c.drawLine((float) (12 + 6 * Math.cos(a)), (float) (12 + 6 * Math.sin(a)), (float) (12 + 8.5 * Math.cos(a)), (float) (12 + 8.5 * Math.sin(a)), p);
                    }
                    c.drawCircle(12, 12, 6.2f, p);
                    break;
                case "textediting":
                    c.drawLine(12, 5, 12, 19, p); c.drawLine(9.5f, 5, 14.5f, 5, p); c.drawLine(9.5f, 19, 14.5f, 19, p);
                    path.moveTo(6, 9); path.lineTo(3, 12); path.lineTo(6, 15); path.moveTo(18, 9); path.lineTo(21, 12); path.lineTo(18, 15);
                    c.drawPath(path, p);
                    break;
                case "keyboard_resize": case "pixelsuite_select_all":
                    float[][] seg = { {4, 8, 4, 4, 8, 4}, {16, 4, 20, 4, 20, 8}, {20, 16, 20, 20, 16, 20}, {8, 20, 4, 20, 4, 16} };
                    for (float[] s : seg) { path.moveTo(s[0], s[1]); path.lineTo(s[2], s[3]); path.lineTo(s[4], s[5]); }
                    if (id.equals("pixelsuite_select_all")) { path.moveTo(11, 4); path.lineTo(13, 4); path.moveTo(20, 11); path.lineTo(20, 13); path.moveTo(11, 20); path.lineTo(13, 20); path.moveTo(4, 11); path.lineTo(4, 13); }
                    c.drawPath(path, p);
                    break;
                case "one_handed":
                    c.drawRoundRect(new RectF(7, 3, 17, 21), 2, 2, p);
                    c.drawRoundRect(new RectF(9.5f, 12, 17, 19), 1, 1, p);
                    break;
                case "floating_keyboard":
                    c.drawRoundRect(new RectF(3, 6, 21, 16), 2, 2, p);
                    for (int i = 0; i < 4; i++) c.drawPoint(6.5f + i * 3.7f, 9.5f, p);
                    c.drawLine(8, 13, 16, 13, p); c.drawLine(8, 19.5f, 16, 19.5f, p);
                    break;
                case "language_switcher":
                    c.drawCircle(12, 12, 8.5f, p);
                    c.drawOval(new RectF(8, 3.5f, 16, 20.5f), p);
                    c.drawLine(3.5f, 12, 20.5f, 12, p);
                    break;
                case "share":
                    c.drawCircle(17, 5.5f, 2.3f, p); c.drawCircle(6, 12, 2.3f, p); c.drawCircle(17, 18.5f, 2.3f, p);
                    c.drawLine(8, 11, 15, 6.6f, p); c.drawLine(8, 13, 15, 17.4f, p);
                    break;
                case "ocr":
                    float[][] fr = { {3, 8, 3, 3, 8, 3}, {16, 3, 21, 3, 21, 8}, {21, 16, 21, 21, 16, 21}, {8, 21, 3, 21, 3, 16} };
                    for (float[] s : fr) { path.moveTo(s[0], s[1]); path.lineTo(s[2], s[3]); path.lineTo(s[4], s[5]); }
                    path.moveTo(8, 8); path.lineTo(16, 8); path.moveTo(12, 8); path.lineTo(12, 16);
                    c.drawPath(path, p);
                    break;
                case "pixelsuite_undo": case "pixelsuite_redo":
                    if (id.equals("pixelsuite_redo")) { c.save(); c.scale(-1, 1, 12, 12); }
                    path.moveTo(9, 7); path.lineTo(5, 11); path.lineTo(9, 15);
                    path.moveTo(5, 11); path.lineTo(14, 11); path.cubicTo(17.5f, 11, 20, 13.5f, 20, 16); path.cubicTo(20, 18.5f, 17.5f, 20, 15, 20); path.lineTo(11, 20);
                    c.drawPath(path, p);
                    if (id.equals("pixelsuite_redo")) c.restore();
                    break;
                case "pixelsuite_select":
                    path.moveTo(4, 7); path.lineTo(4, 4); path.lineTo(7, 4);
                    path.moveTo(17, 4); path.lineTo(20, 4); path.lineTo(20, 7);
                    path.moveTo(20, 17); path.lineTo(20, 20); path.lineTo(17, 20);
                    path.moveTo(7, 20); path.lineTo(4, 20); path.lineTo(4, 17);
                    path.moveTo(10.5f, 8.5f); path.lineTo(13.5f, 8.5f); path.moveTo(12, 8.5f); path.lineTo(12, 15.5f); path.moveTo(10.5f, 15.5f); path.lineTo(13.5f, 15.5f);
                    c.drawPath(path, p);
                    c.drawCircle(8.6f, 12, 1.25f, fill); c.drawCircle(15.4f, 12, 1.25f, fill);
                    break;
                case "pixelsuite_cut":
                    c.drawCircle(6.5f, 17.5f, 2.6f, p); c.drawCircle(17.5f, 17.5f, 2.6f, p);
                    c.drawLine(8.4f, 15.6f, 18, 4, p); c.drawLine(15.6f, 15.6f, 6, 4, p);
                    break;
                case "pixelsuite_copy":
                    c.drawRoundRect(new RectF(8, 8, 20, 20), 2, 2, p);
                    path.moveTo(5, 16); path.lineTo(5, 6); path.cubicTo(5, 4.9f, 5.9f, 4, 7, 4); path.lineTo(16, 4);
                    c.drawPath(path, p);
                    break;
                case "voice": case "seamless_voice_typing":
                    c.drawRoundRect(new RectF(8.5f, 3, 15.5f, 14), 3.5f, 3.5f, p);
                    path.moveTo(5.5f, 11); path.cubicTo(5.5f, 15, 8.5f, 17.5f, 12, 17.5f); path.cubicTo(15.5f, 17.5f, 18.5f, 15, 18.5f, 11);
                    path.moveTo(12, 17.5f); path.lineTo(12, 21);
                    c.drawPath(path, p);
                    if (id.equals("voice")) star(c, 19.5f, 4.5f, 2.2f, fill);
                    break;
                case "chatbot":
                    path.moveTo(5, 5); path.lineTo(19, 5); path.lineTo(19, 16); path.lineTo(11, 16); path.lineTo(7, 19.5f); path.lineTo(7, 16); path.lineTo(5, 16); path.close();
                    c.drawPath(path, p);
                    star(c, 12, 10.5f, 2.6f, fill);
                    break;
                case "undo":
                    path.moveTo(9, 7); path.lineTo(5, 11); path.lineTo(9, 15);
                    path.moveTo(5, 11); path.lineTo(14, 11); path.cubicTo(17.5f, 11, 20, 13.5f, 20, 16); path.cubicTo(20, 18.5f, 17.5f, 20, 15, 20); path.lineTo(11, 20);
                    c.drawPath(path, p);
                    break;
                case "split":
                    c.drawRoundRect(new RectF(3, 7, 10.5f, 17), 1.5f, 1.5f, p); c.drawRoundRect(new RectF(13.5f, 7, 21, 17), 1.5f, 1.5f, p);
                    break;
                case "quick_settings":
                    c.drawLine(4, 8, 20, 8, p); c.drawLine(4, 16, 20, 16, p);
                    c.drawCircle(9, 8, 2.2f, fill); c.drawCircle(15, 16, 2.2f, fill);
                    break;
                case "emoticon": case "rich_symbol": {
                    Paint e = new Paint(fill); e.setTextSize(id.equals("emoticon") ? 8.5f : 13f); e.setTextAlign(Paint.Align.CENTER); e.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText(id.equals("emoticon") ? ";-)" : "Ω", 12, id.equals("emoticon") ? 15 : 16.5f, e);
                    break;
                }
                default: {
                    c.drawRoundRect(new RectF(4, 4, 20, 20), 4.5f, 4.5f, p);
                    String name = GboardBarEditor.label(id);
                    Paint e = new Paint(fill); e.setTextSize(10f); e.setTextAlign(Paint.Align.CENTER); e.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 12, 15.5f, e);
                }
            }
        }

        private static void star(Canvas c, float x, float y, float r, Paint fill) {
            Path s = new Path();
            s.moveTo(x, y - r); s.quadTo(x, y, x + r, y); s.quadTo(x, y, x, y + r); s.quadTo(x, y, x - r, y); s.quadTo(x, y, x, y - r);
            c.drawPath(s, fill);
        }
    }
}
