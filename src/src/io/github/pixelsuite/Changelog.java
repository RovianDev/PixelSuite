package io.github.pixelsuite;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;

final class Changelog {
    private Changelog() {}
    static String read(Context context) {
        I18n.init(context);
        int id = I18n.lang() == null ? 0 : context.getResources().getIdentifier("changelog_" + I18n.lang(), "raw", context.getPackageName());
        if (id == 0) id = context.getResources().getIdentifier("changelog", "raw", context.getPackageName());
        if (id == 0) return I18n.t("Release notes are unavailable in this build.");
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(id),"UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        } catch (Exception unavailable) { return I18n.t("Release notes could not be opened. Please reopen this page."); }
    }
}
