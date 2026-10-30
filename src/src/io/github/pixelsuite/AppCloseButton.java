package io.github.pixelsuite;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
final class AppCloseButton {
 private AppCloseButton() {}
 static void style(TextView button) {
  button.setGravity(Gravity.CENTER);
  button.setIncludeFontPadding(false);
  button.setTextSize(24);
  button.setBackground(new ThemeGlass(button.getContext(),24,ThemeGlass.SOCIAL));
  button.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  button.setContentDescription("Close");
 }
}
