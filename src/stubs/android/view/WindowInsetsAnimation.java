package android.view;
import java.util.List;
public class WindowInsetsAnimation {
    public abstract static class Callback {
        public Callback(int mode){}
        public abstract WindowInsets onProgress(WindowInsets in,List<WindowInsetsAnimation> running);
        public void onPrepare(WindowInsetsAnimation animation){}
        public void onEnd(WindowInsetsAnimation animation){}
    }
}
