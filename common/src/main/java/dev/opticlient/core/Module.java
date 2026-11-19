package dev.opticlient.core;

import java.util.Collections;
import java.util.List;

/** HUD에 그려지는 하나의 요소. */
public abstract class Module {
    public static final int PAD = 3;

    public final String id;
    public final String name;
    final int defX;
    final int defY;
    final boolean defEnabled;

    /** 메뉴 카드 정보 */
    public String category = "HUD";
    public String desc = "";
    public String[] icon = Icons.GRID;

    /** 마지막으로 그린 크기 (HUD 편집기의 클릭 판정용) */
    public int lastW;
    public int lastH;
    /** 크기 배율을 적용한 화면상 크기 */
    public int boxW;
    public int boxH;

    protected Module(String id, String name, boolean defEnabled, int defX, int defY) {
        this.id = id;
        this.name = name;
        this.defEnabled = defEnabled;
        this.defX = defX;
        this.defY = defY;
    }

    /** 이 모듈만의 켜고 끄는 옵션 (메뉴 톱니에서 바꿈) */
    public static final class Option {
        public final String key;
        public final String label;
        public final boolean def;

        public Option(String key, String label, boolean def) {
            this.key = key;
            this.label = label;
            this.def = def;
        }
    }

    /** 모듈별 옵션 목록 (없으면 빈 목록) */
    public List<Option> options() {
        return Collections.emptyList();
    }

    /** 이름 글자("FPS" 등)를 숨길 수 있는 모듈인지 */
    public boolean hasLabel() {
        return false;
    }

    /** 옵션 값 */
    public boolean opt(String key) {
        HudConfig.ModuleState s = OptiCore.config.modules.get(id);
        if (s != null && s.opts != null && s.opts.containsKey(key)) return Boolean.TRUE.equals(s.opts.get(key));
        for (Option o : options()) if (o.key.equals(key)) return o.def;
        return false;
    }

    Module meta(String category, String desc, String[] icon) {
        this.category = category;
        this.desc = desc;
        this.icon = icon;
        return this;
    }

    /** x, y 위치에 그리고 lastW/lastH 를 갱신한다. editing 이면 데이터가 없어도 미리보기를 그린다. */
    public abstract void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing);

    // ---------- 공통 그리기 도우미 ----------

    /**
     * "라벨: 값" 한 줄을 스타일에 맞게 그리고 너비를 돌려준다.
     * 모던은 "라벨 값"(회색/흰색), 클래식·미니멀은 "라벨: 값"(강조색/흰색).
     */
    protected static int line(HudRenderer r, HudConfig cfg, String s, int x, int y, boolean draw) {
        int idx = s.indexOf(": ");
        if (idx > 0 && Theme.hideLabel) {
            // 이름 숨김: 값만
            s = s.substring(idx + 2);
            idx = -1;
        }
        if (idx <= 0) {
            if (draw) r.text(s, x, y, cfg.textColor, cfg.shadow);
            return r.width(s);
        }
        String label = Theme.modern() ? s.substring(0, idx) + " " : s.substring(0, idx + 1);
        String value = Theme.modern() ? s.substring(idx + 2) : s.substring(idx + 1);
        int lw = r.width(label);
        if (draw) {
            r.text(label, x, y, Theme.label(x, y), cfg.shadow);
            r.text(value, x + lw, y, cfg.textColor, cfg.shadow);
        }
        return lw + r.width(value);
    }

    /** 텍스트 줄 여러 개를 배경 상자와 함께 그리는 기본 모듈. */
    public abstract static class TextModule extends Module {
        protected TextModule(String id, String name, boolean defEnabled, int defX, int defY) {
            super(id, name, defEnabled, defX, defY);
        }

        protected abstract List<String> lines(GameInfo g);

        protected List<String> preview() {
            return Collections.singletonList(name);
        }

        @Override
        public boolean hasLabel() {
            return true;
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            List<String> ls = lines(g);
            if (ls.isEmpty()) {
                if (!editing) {
                    lastW = lastH = 0;
                    return;
                }
                ls = preview();
            }
            int lh = r.lineHeight();
            int w = 0;
            for (String s : ls) w = Math.max(w, line(r, cfg, s, 0, 0, false));
            w += Theme.padLeft() + Theme.padRight();
            if (Theme.modern()) w = Math.max(w, 62); // 세로로 쌓였을 때 폭이 들쭉날쭉하지 않게
            int h = ls.size() * lh + Theme.padY() * 2 - 1;
            Theme.panel(r, x, y, w, h);
            for (int i = 0; i < ls.size(); i++) line(r, cfg, ls.get(i), x + Theme.padLeft(), y + Theme.padY() + i * lh, true);
            lastW = w;
            lastH = h;
        }
    }
}
