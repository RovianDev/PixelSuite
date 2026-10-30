package io.github.pixelsuite;

final class SectionColors {
    private SectionColors() {}
    static int family(String page,String icon){
        if("themes".equals(page)||"doodles".equals(page)||"launcher".equals(page)||"drawer".equals(page)||"recents".equals(page))return 3;
        if("do_not_annoy".equals(page)||"privacy".equals(page)||"blockers".equals(page)||"store_blocker".equals(page)||"ota_blocker".equals(page))return 4;
        if("buttons".equals(page)||"gestures".equals(page))return 2;
        if("files".equals(page)||"share_menu".equals(page))return 5;
        if("miscellaneous".equals(page))return 0;
        if("chrome".equals(page)||"google_news".equals(page)||"google_apps".equals(page)||"google_app".equals(page)||"google_dialer".equals(page)||"pixel_watch".equals(page))return 1;
        if(icon==null)return 1;
        if(icon.contains("themes")||icon.contains("doodles")||icon.contains("launcher"))return 3;
        if(icon.contains("do_not_annoy")||icon.contains("privacy")||icon.contains("store_blocker")||icon.contains("update_blocker"))return 4;
        if(icon.contains("buttons")||icon.contains("gestures"))return 2;
        if(icon.contains("files")||icon.contains("share"))return 5;
        if(icon.contains("miscellaneous"))return 0;
        return 1;
    }
    static int background(int family,boolean dark){
        final int[] light={0xffb6edff,0xffd1e3ff,0xffffd4ed,0xffffdcbf,0xffccebd0,0xffffe59a};
        final int[] night={0xff4cc8ed,0xffa4c8ff,0xffffaee0,0xffffb77d,0xffa1d7a7,0xffffd452};
        return (dark?night:light)[family];
    }
    static int ink(int family){return new int[]{0xff004b60,0xff123f75,0xff78004d,0xff763e0e,0xff245333,0xff684b00}[family];}
}
