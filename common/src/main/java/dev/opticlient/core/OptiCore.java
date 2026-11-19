package dev.opticlient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

/** 모든 버전이 공유하는 상태와 설정 저장/로드. */
public final class OptiCore {
    public static final String MOD_ID = "opticlient";
    public static final CpsTracker CPS = new CpsTracker();
    public static final List<Module> MODULES = Modules.createAll();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static HudConfig config = new HudConfig();
    private static File configFile;

    /** 토글 달리기가 켜져 있는지 (키로 전환) */
    public static boolean sprintToggled = false;
    /** 줌 키를 누르고 있는지 */
    public static boolean zooming = false;
    /** 웅크리기 토글이 켜져 있는지 */
    public static boolean sneakToggled = false;
    /** 리치·콤보·타깃 계산 */
    public static final PvpTracker PVP = new PvpTracker();

    /** 퍼스펙티브(자유 시점) 중인지와 카메라 방향 */
    public static boolean freelook = false;
    public static float camYaw;
    public static float camPitch;

    /** 버전별 PvP FPS 부스트 구현 (각 모드가 넣어 준다) */
    public static PerfBoost perf;
    private static boolean boostApplied;
    private static final int LAYOUT_VERSION = 2;

    private OptiCore() {
    }

    public static void init(File configDir) {
        configFile = new File(configDir, MOD_ID + ".json");
        if (configFile.isFile()) {
            Reader rd = null;
            try {
                rd = new InputStreamReader(new FileInputStream(configFile), UTF8);
                HudConfig loaded = GSON.fromJson(rd, HudConfig.class);
                if (loaded != null) config = loaded;
            } catch (Exception e) {
                System.err.println("[OptiClient] 설정 파일을 읽지 못해 기본값을 사용합니다: " + e);
            } finally {
                closeQuietly(rd);
            }
        }
        if (config.modules == null) config.modules = new java.util.LinkedHashMap<String, HudConfig.ModuleState>();
        for (Module m : MODULES) {
            if (!config.modules.containsKey(m.id)) {
                config.modules.put(m.id, new HudConfig.ModuleState(m.defEnabled, m.defX, m.defY));
            }
        }
        // 새 디자인(자동 정렬)으로 바뀌면 위치를 기본값으로 돌린다
        if (config.layoutVersion < LAYOUT_VERSION) {
            for (Module m : MODULES) {
                HudConfig.ModuleState s = config.modules.get(m.id);
                s.x = m.defX;
                s.y = m.defY;
                s.anchor = null;
            }
            config.layoutVersion = LAYOUT_VERSION;
            config.style = Theme.MODERN;
            config.accent = 0;
        }
        if (config.favorites == null) config.favorites = new java.util.ArrayList<String>();
        save();
    }

    private static boolean baselineApplied;

    /** 매 틱 호출 (타이틀 화면 포함): 기본 최적화를 한 번 적용 */
    public static void tickBaseline() {
        if (perf == null || baselineApplied || !config.unlockFps) return;
        baselineApplied = true;
        try {
            perf.baseline();
        } catch (RuntimeException e) {
            System.err.println("[OptiClient] 기본 최적화 적용 실패: " + e);
        }
    }

    /** 게임 안에 있을 때 매 틱 호출: 부스트 설정이 바뀌었으면 적용/복구 */
    public static void tickBoost() {
        if (perf == null || config.pvpBoost == boostApplied) return;
        boostApplied = config.pvpBoost;
        try {
            perf.apply(boostApplied);
        } catch (RuntimeException e) {
            System.err.println("[OptiClient] FPS 부스트 적용 실패: " + e);
        }
    }

    public static void save() {
        if (configFile == null) return;
        Writer w = null;
        try {
            configFile.getParentFile().mkdirs();
            w = new OutputStreamWriter(new FileOutputStream(configFile), UTF8);
            GSON.toJson(config, w);
        } catch (Exception e) {
            System.err.println("[OptiClient] 설정 저장 실패: " + e);
        } finally {
            closeQuietly(w);
        }
    }

    public static HudConfig.ModuleState state(Module m) {
        HudConfig.ModuleState s = config.modules.get(m.id);
        if (s == null) {
            s = new HudConfig.ModuleState(m.defEnabled, m.defX, m.defY);
            config.modules.put(m.id, s);
        }
        return s;
    }

    public static void resetPositions() {
        for (Module m : MODULES) {
            HudConfig.ModuleState s = state(m);
            s.x = m.defX;
            s.y = m.defY;
            s.anchor = null;
        }
        save();
    }

    /** 음수 기본 위치(-1 오른쪽 아래, -2 오른쪽 위)를 실제 좌표로 바꾸고 화면 안으로 보정한다. */
    static int[] resolve(Module m, HudConfig.ModuleState s, int sw, int sh) {
        int w = Math.max(m.boxW, 1);
        int h = Math.max(m.boxH, 1);
        int x = s.x;
        int y = s.y;
        if (s.anchor != null && s.anchor.length() == 2) {
            // 다른 클라이언트에서 가져온 배치: 기준 위치 + 떨어진 거리
            char v = s.anchor.charAt(0), hz = s.anchor.charAt(1);
            int baseX = hz == 'r' ? sw - w : hz == 'c' ? (sw - w) / 2 : 0;
            int baseY = v == 'b' ? sh - h : v == 'c' ? (sh - h) / 2 : 0;
            x = baseX + x;
            y = baseY + y;
        } else if (x == Modules.AUTO && y == Modules.AUTO) {
            // 왼쪽 위부터 켜진 요소끼리 빈칸 없이 쌓고, 아래(채팅 자리)까지 내려가면 옆 줄로 넘긴다
            int bottom = Math.max(60, sh - 70);
            x = 4;
            y = 4;
            int colW = 0;
            for (Module o : MODULES) {
                HudConfig.ModuleState os = state(o);
                boolean auto = os.enabled && os.x == Modules.AUTO && os.y == Modules.AUTO;
                int oh = o == m ? h : o.boxH;
                if (!auto || oh <= 0) {
                    if (o == m) break;
                    continue;
                }
                if (y > 4 && y + oh > bottom) {
                    x += colW + 3;
                    y = 4;
                    colW = 0;
                }
                if (o == m) break;
                y += oh + 3;
                colW = Math.max(colW, o.boxW);
            }
        } else if (x == -1 && y == -1) {
            // 갑옷 HUD: 핫바 바로 오른쪽
            x = sw / 2 + 95;
            y = sh - 86;
        } else if (x == -4 && y == -4) {
            // 타깃 HUD: 크로스헤어 오른쪽 위 (아래쪽 갑옷 HUD 와 겹치지 않게)
            x = sw / 2 + 20;
            y = sh / 2 - h - 14;
        } else if (x == -6 && y == -6) {
            // 인벤토리: 핫바 위 가운데 (체력·배고픔 줄 위)
            x = (sw - w) / 2;
            y = sh - 58 - h;
        } else if (x == -5 && y == -5) {
            // 나침반: 화면 위 가운데
            x = (sw - w) / 2;
            y = 2;
        } else if (x == -2 && y == -2) {
            // 키 입력 표시: 오른쪽 아래 (오른쪽 위는 업적/튜토리얼 알림창에 가려짐)
            x = sw - 74;
            y = sh - 86;
        }
        x = Math.max(0, Math.min(x, sw - w));
        y = Math.max(0, Math.min(y, sh - h));
        return new int[]{x, y};
    }

    /** 기본 크로스헤어 대신 OptiClient 크로스헤어를 그리는지 (크로스헤어 기능 또는 리치 도우미가 켜져 있으면) */
    public static boolean customCrosshair() {
        return config.crosshair || config.reachAssist;
    }

    /** 게임 HUD에 켜진 모듈을 모두 그린다. */
    public static void renderHud(HudRenderer r, GameInfo g, int sw, int sh) {
        try {
            PvpAids.update(g);
            PvpAids.render(r, g, sw, sh);
        } catch (RuntimeException ignored) {
            PvpAids.aim = -1;
        }
        if (customCrosshair() && g.firstPerson()) {
            try {
                Crosshair.render(r, g, sw, sh);
            } catch (RuntimeException ignored) {
            }
        }
        for (Module m : MODULES) {
            HudConfig.ModuleState s = state(m);
            if (!s.enabled) continue;
            int[] p = resolve(m, s, sw, sh);
            try {
                renderModule(m, s, r, g, p[0], p[1], false);
            } catch (RuntimeException e) {
                // 한 모듈의 오류가 게임을 멈추지 않도록 한다
                m.lastW = m.lastH = m.boxW = m.boxH = 0;
            }
        }
        r.flush();
    }

    public static float scaleOf(HudConfig.ModuleState s) {
        return s.scale < 0.5f || s.scale > 2f ? 1f : s.scale;
    }

    /** 크기 배율을 적용해 그리고 boxW/boxH(화면상 크기)를 갱신한다 */
    static void renderModule(Module m, HudConfig.ModuleState s, HudRenderer r, GameInfo g, int x, int y, boolean editing) {
        // 모듈마다 정한 배경·그림자·글자색·이름 표시를 이 모듈을 그리는 동안만 적용
        boolean bg = config.background, sh = config.shadow;
        int tc = config.textColor;
        if (s.bg != 0) config.background = s.bg == 1;
        if (s.shadowMode != 0) config.shadow = s.shadowMode == 1;
        config.textColor = Theme.textColor(s.color);
        Theme.hideLabel = !s.label;
        try {
            renderScaled(m, s, r, g, x, y, editing);
        } finally {
            config.background = bg;
            config.shadow = sh;
            config.textColor = tc;
            Theme.hideLabel = false;
        }
    }

    private static void renderScaled(Module m, HudConfig.ModuleState s, HudRenderer r, GameInfo g, int x, int y, boolean editing) {
        float sc = scaleOf(s);
        if (Math.abs(sc - 1f) < 0.01f) {
            m.render(r, g, config, x, y, editing);
        } else {
            r.pushTransform(x, y, sc);
            try {
                m.render(r, g, config, 0, 0, editing);
            } finally {
                r.popTransform();
            }
        }
        m.boxW = Math.round(m.lastW * sc);
        m.boxH = Math.round(m.lastH * sc);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** "신속 II: 1:23" 형식. ticks < 0 이면 무한 */
    public static String effectLine(String name, int amplifier, int ticks) {
        String lvl = amplifier >= 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1);
        String time;
        if (ticks < 0 || ticks > 20 * 60 * 60 * 24) {
            time = "∞";
        } else {
            int sec = ticks / 20;
            time = (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
        }
        return name + " " + lvl + ": " + time;
    }

    /** 이 버전에서 반짝임 끄기를 쓸 수 있는지 (1.8.9 는 불가) */
    public static boolean glintSupported = true;

    /** 반짝임을 그리지 않을지: 반짝임 끄기 또는 PvP 부스트 중 */
    public static boolean glintOff() {
        return config.noGlint || config.pvpBoost;
    }

    /** 퍼스펙티브 시작: 지금 보는 방향에서 카메라를 떼어 낸다 */
    public static void startFreelook(float yaw, float pitch) {
        camYaw = yaw;
        camPitch = pitch;
        freelook = true;
    }

    /** 퍼스펙티브 중 마우스 움직임을 카메라에만 적용 (마인크래프트 회전 계수 0.15 와 같게) */
    public static void turnCamera(double dx, double dy) {
        camYaw += (float) dx * 0.15f;
        camPitch = Math.max(-90f, Math.min(90f, camPitch + (float) dy * 0.15f));
    }

    public static float zoomFov(float fov) {
        return zooming ? (float) (fov / Math.max(1.0, config.zoomFactor)) : fov;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }
}
