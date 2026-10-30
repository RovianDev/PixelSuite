package io.github.pixelsuite;

final class Safe {
    private Safe() {}

    interface Step { void run() throws Throwable; }

    static void run(Step step) {
        try { step.run(); } catch (Throwable ignored) { }
    }
}
