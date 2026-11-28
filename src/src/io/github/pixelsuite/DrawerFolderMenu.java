package io.github.pixelsuite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Toast;

final class DrawerFolderMenu {
    private static PopupWindow open;

    private DrawerFolderMenu() {}

    private static Object holder;

    static void show(final View icon) {
        final Object info = icon.getTag();
        if (!DrawerFolders.isFolder(info) || DrawerFolders.record(info) == null) { dismiss(); return; }
        present(icon, info, null);
    }

    static void showHome(final View icon, final Object container) {
        final Object info = icon == null ? null : icon.getTag();
        if (info == null || DrawerFolders.isFolder(info)) return;
        present(icon, info, container);
    }

    static void closedWith(Object container) {
        if (container != null && container == holder) { holder = null; dismiss(); }
    }

    static void dragStarted() {
        if (holder == null || open == null) return;
        holder = null; dismiss();
    }

    private static void present(final View icon, final Object info, final Object container) {
        dismiss();
        Context c = icon.getContext();
        final LinearLayout card = new LinearLayout(c); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, FrostedSurface.dp(c, 6), 0, FrostedSurface.dp(c, 6));
        ScrollView menu = new ScrollView(c); menu.addView(card);
        menu.setBackground(FrostedSurface.windowDrawable(c, 20)); menu.setClipToOutline(true);
        menu.setVerticalScrollBarEnabled(false); menu.setOverScrollMode(View.OVER_SCROLL_NEVER);
        menu.setElevation(FrostedSurface.dp(c, 8));
        if (container == null) fill(card, info); else fillHome(card, info, icon);
        final FrameLayout root = new FrameLayout(c);
        final FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(menu, place);
        final PopupWindow window = new PopupWindow(root, FrostedSurface.dp(c, 164), -2, false);
        window.setBackgroundDrawable(new ColorDrawable(0)); window.setOutsideTouchable(true);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        root.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dismiss(); } });

        Rect frame = new Rect(); icon.getWindowVisibleDisplayFrame(frame);
        Rect visible = new Rect();
        if (!icon.getGlobalVisibleRect(visible)) return;
        int margin = FrostedSurface.dp(c, 12), gap = FrostedSurface.dp(c, 6);
        int width = FolderOptions.fitWidth(card, FrostedSurface.dp(c, 164 * Math.max(1f, c.getResources().getConfiguration().fontScale)), frame.width() - 2 * margin);
        int row = FrostedSurface.dp(c, 48), edge = card.getPaddingTop() + card.getPaddingBottom();
        int full = edge, shown = edge;
        for (int i = 0; i < card.getChildCount(); i++) {
            int h = card.getChildAt(i).getLayoutParams().height;
            full += row; shown += h >= 0 ? h : row;
        }
        int left = Math.max(frame.left + margin, Math.min(visible.centerX() - width / 2, frame.right - width - margin));
        int below = frame.bottom - margin - (visible.bottom + gap), above = visible.top - gap - (frame.top + margin);
        boolean up = below < full && (above >= full || (below < shown && above > below));
        int height = Math.min(full, Math.max(up ? above : below, shown)), top;
        if (up) { top = visible.top - gap - height; place.gravity = Gravity.BOTTOM; }
        else top = visible.bottom + gap;
        window.setWidth(width); window.setHeight(height);
        final View.OnAttachStateChangeListener lifecycle = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { }
            public void onViewDetachedFromWindow(View v) { if (open == window) dismiss(); }
        };
        icon.addOnAttachStateChangeListener(lifecycle);
        window.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                icon.removeOnAttachStateChangeListener(lifecycle); if (open == window) open = null;
                if (container != null && holder == container) { holder = null; try { LauncherFeatures.call(container, "close", true); } catch (Throwable ignored) { } }
            }
        });
        window.showAtLocation(icon.getRootView(), Gravity.TOP | Gravity.LEFT, left, top);
        FrostedSurface.popupWindow(menu, 20);
        menu.setAlpha(0); menu.animate().alpha(1).setDuration(150).start();
        open = window; holder = container;
    }

    private static void fillHome(final LinearLayout card, final Object info, final View icon) {
        card.removeAllViews();
        FolderOptions.row(card, "Rename", 1, false, new Runnable() { public void run() { dismiss(); FolderSort.homeRename(info); } });
        FolderOptions.sortGroup(card, FolderSort.homeMode(info) == FolderSort.AUTO, new Runnable() { public void run() {
            dismiss();
            if (FolderSort.homeMode(info) == FolderSort.AUTO) return;
            confirm("Sort apps?", "Sort the apps in “" + title(info) + "” from A to Z?", new Runnable() { public void run() { FolderSort.sortHome(info); } });
        }}, new Runnable() { public void run() {
            dismiss();
            if (FolderSort.homeMode(info) == FolderSort.AUTO) FolderSort.setHomeMode(info, FolderSort.CUSTOM);
        }});
        PrivacyFolders.addDrawerRow(card, info, new Runnable() { public void run() { dismiss(); } });
        FolderOptions.row(card, "Remove", 8, false, new Runnable() { public void run() { dismiss(); removeHome(info, icon); } });
        if (BadgeClear.has(info)) {
            FolderOptions.row(card, "Clear badge", 16, false, new Runnable() { public void run() { dismiss(); BadgeClear.clear(info); } });
            View badge = card.getChildAt(card.getChildCount() - 1);
            card.removeView(badge); card.addView(badge, 0);
        }
    }

    private static String title(Object info) {
        try { Object t = LauncherFeatures.get(info, "title"); if (t != null && t.toString().length() > 0) return t.toString(); } catch (Throwable ignored) { }
        return I18n.t("Folder");
    }

    private static void removeHome(final Object info, final View icon) {
        confirm("Remove folder?", "Remove “" + title(info) + "”? Its apps go back to the app drawer. No apps are uninstalled.", new Runnable() {
            public void run() {
                final Activity a = LauncherFeatures.activity.get();
                if (a == null) return;
                Runnable go = new Runnable() { public void run() { if (!MultiDrag.remove(a, icon, info, "Pixel Suite: folder menu")) Toast.makeText(a, I18n.cs("Could not remove the folder"), Toast.LENGTH_SHORT).show(); } };
                if (PrivacyGuard.ask(a, info, go)) return;
                go.run();
            }
        });
    }

    private static void fill(final LinearLayout card, final Object info) {
        card.removeAllViews();
        final LauncherData.Folder f = DrawerFolders.record(info);
        final int id = f == null ? 0 : f.id;
        FolderOptions.row(card, "Rename", 1, false, new Runnable() { public void run() { dismiss(); rename(info); } });
        FolderOptions.sortGroup(card, f != null && f.sort == FolderSort.AUTO, new Runnable() { public void run() {
            dismiss(); LauncherData.Folder now = DrawerFolders.record(info);
            if (now != null && now.sort != FolderSort.AUTO) sort(info);
        }}, new Runnable() { public void run() {
            dismiss(); LauncherData.Folder now = DrawerFolders.record(info);
            if (now != null && now.sort == FolderSort.AUTO) FolderSort.drawerCustom(id);
        }});
        PrivacyFolders.addDrawerRow(card, info, new Runnable() { public void run() { dismiss(); } });
        FolderOptions.row(card, "Remove", 8, false, new Runnable() { public void run() { dismiss(); remove(info); } });
        if (BadgeClear.has(info)) {
            FolderOptions.row(card, "Clear badge", 16, false, new Runnable() { public void run() { dismiss(); BadgeClear.clear(info); } });
            View badge = card.getChildAt(card.getChildCount() - 1);
            card.removeView(badge); card.addView(badge, 0);
        }
    }

    static void dismiss() {
        PopupWindow window = open; open = null;
        if (window != null) try { window.dismiss(); } catch (Throwable ignored) { }
    }

    static void rename(final Object info) {
        final LauncherData.Folder f = DrawerFolders.record(info); final Activity a = LauncherFeatures.activity.get();
        if (f == null || a == null) return;
        final EditText edit = new EditText(a); edit.setSingleLine(true); edit.setText(I18n.cs(f.name)); edit.selectAll();
        edit.setPadding(FrostedSurface.dp(a, 20), FrostedSurface.dp(a, 10), FrostedSurface.dp(a, 20), FrostedSurface.dp(a, 10)); edit.setTextSize(16);
        final AlertDialog dialog = new AlertDialog.Builder(a).setTitle(I18n.cs("Rename folder")).setView(edit)
                .setNegativeButton(I18n.cs("Cancel"), null).setPositiveButton(I18n.cs("Save"), null).create();
        FrostedSurface.show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View button) {
                String name = edit.getText().toString().trim();
                if (name.length() == 0 || name.length() > 80) { edit.setError(I18n.cs("Use a name between 1 and 80 characters")); return; }
                if (DrawerFolders.renameTo(f.id, name)) dialog.dismiss();
                else Toast.makeText(a, I18n.cs("Could not save the name"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    static void sort(final Object info) {
        final LauncherData.Folder f = DrawerFolders.record(info); if (f == null) return;
        confirm("Sort apps?", "Sort the apps in “" + f.name + "” from A to Z?", new Runnable() {
            public void run() { DrawerFolders.sortFolder(f.id); }
        });
    }

    static void remove(final Object info) {
        final LauncherData.Folder f = DrawerFolders.record(info); if (f == null) return;
        confirm("Remove folder?", "Remove “" + f.name + "”? Its apps go back to the app drawer. No apps are uninstalled.", new Runnable() {
            public void run() {
                android.app.Activity a = LauncherFeatures.activity.get();
                final String lockKey = a == null ? null : PrivacyBadges.lockedKey(a, info);
                if (a != null && PrivacyGuard.ask(a, info, new Runnable() { public void run() { DrawerFolders.removeFolder(info, lockKey); } })) return;
                DrawerFolders.removeFolder(info, null);
            }
        });
    }

    static void confirm(String title, String message, final Runnable yes) {
        Activity a = LauncherFeatures.activity.get(); if (a == null) return;
        FrostedSurface.show(new AlertDialog.Builder(a).setTitle(I18n.cs(title)).setMessage(I18n.cs(message))
                .setNegativeButton(I18n.cs("No"), null)
                .setPositiveButton(I18n.cs("Yes"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) { yes.run(); } })
                .create());
    }
}
