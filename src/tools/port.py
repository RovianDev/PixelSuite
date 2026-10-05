"""Shared converter: classic Xposed API -> the Xp shim (libxposed API 102)."""
import re

def body(src, classname):
    i = src.index('class ' + classname)
    i = src.index('{', i) + 1
    j = src.rindex('}')
    return src[i:j]

def to_shim(s):
    s = re.sub(r'import de\.robv\.android\.xposed\.[^\n]*\n', '', s)
    s = s.replace('XposedBridge.hookAllMethods', 'Xp.hookAllMethods')
    s = s.replace('XposedBridge.hookAllConstructors', 'Xp.hookAllConstructors')
    s = s.replace('XposedBridge.hookMethod', 'Xp.hookMethod')
    s = s.replace('XposedBridge.log', 'Xp.log')
    s = s.replace('XposedHelpers.', 'Xp.')
    s = s.replace('XC_MethodHook.MethodHookParam', 'Xp.Param')
    s = s.replace('MethodHookParam', 'Xp.Param')
    s = s.replace('XC_MethodHook', 'Xp.Callback')
    return s
