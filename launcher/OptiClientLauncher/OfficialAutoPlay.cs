using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace OptiClientLauncher;

/// <summary>
/// 공식 마인크래프트 런처를 열고 [플레이] 버튼을 대신 눌러 준다.
/// 정품 로그인은 공식 런처가 그대로 처리하므로 계정 정보는 전혀 건드리지 않는다.
///
/// 공식 런처 화면은 크롬(CEF) 기반이라 버튼 정보를 읽을 수 없어서,
/// 창을 캡처해 초록색 플레이 버튼을 찾고 그 자리에 클릭 메시지만 보낸다
/// (실제 마우스 커서는 움직이지 않음).
/// </summary>
public static class OfficialAutoPlay
{
    public readonly record struct Point(int X, int Y);

    public enum Result { Started, ClickedNoGameYet, ButtonNotFound, LauncherNotFound }

    /// <summary>
    /// 공식 런처를 열고 플레이를 누른다. 게임(java) 프로세스가 뜨면 Started.
    /// </summary>
    public static Task<Result> RunAsync(IProgress<string> log, CancellationToken ct, bool closeLauncherAfter = false) =>
        Task.Factory.StartNew(() =>
        {
            var r = Run(log, ct, out var game);
            if (r == Result.Started && closeLauncherAfter && game != null) CloseLauncherWhenGameReady(game, log, ct);
            return r;
        }, ct, TaskCreationOptions.LongRunning, TaskScheduler.Default);

    /// <summary>
    /// 게임 창이 뜨고 5초 뒤 공식 런처를 닫는다.
    /// 새 공식 런처는 게임 중 창 닫기 요청을 무시하고 숨어 있다가 게임이 끝나면 다시 나타나므로 프로세스를 종료한다.
    /// (공식 런처를 종료해도 게임은 그대로 실행됨을 확인함)
    /// </summary>
    private static void CloseLauncherWhenGameReady(Process game, IProgress<string> log, CancellationToken ct)
    {
        try
        {
            log.Report("게임 창이 뜨면 공식 런처를 닫아요...");
            var until = DateTime.UtcNow.AddMinutes(3);
            while (DateTime.UtcNow < until)
            {
                ct.ThrowIfCancellationRequested();
                game.Refresh();
                if (game.HasExited) return; // 게임이 바로 꺼졌으면 공식 런처(오류 안내)를 그대로 둔다
                if (game.MainWindowHandle != IntPtr.Zero) break;
                Thread.Sleep(500);
            }
            Thread.Sleep(5000);
            game.Refresh();
            if (game.HasExited) return;
            var n = Installer.KillOfficialLauncher();
            if (n > 0) log.Report("공식 런처를 닫았어요. 게임은 계속 실행돼요.");
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception e)
        {
            log.Report("공식 런처를 닫지 못했어요: " + e.Message);
        }
    }

    private static Result Run(IProgress<string> log, CancellationToken ct, out Process? game)
    {
        // 다른 프로그램 창 좌표를 실제 픽셀로 다루기 위해 이 스레드만 DPI 인식으로 바꾼다
        game = null;
        var oldDpi = SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        try
        {
            var javaBefore = JavaPids();
            if (!Installer.StartOfficialLauncher()) return Result.LauncherNotFound;
            log.Report("공식 런처를 여는 중...");

            // 1) 런처 창이 뜰 때까지 (최대 60초)
            IntPtr top = IntPtr.Zero, content = IntPtr.Zero;
            var until = DateTime.UtcNow.AddSeconds(60);
            while (DateTime.UtcNow < until)
            {
                ct.ThrowIfCancellationRequested();
                top = FindLauncherWindow();
                if (top != IntPtr.Zero)
                {
                    content = FindChromeWidget(top);
                    if (content != IntPtr.Zero) break;
                }
                Thread.Sleep(400);
            }
            if (top == IntPtr.Zero || content == IntPtr.Zero) return Result.LauncherNotFound;
            if (IsIconic(top)) ShowWindow(top, SW_RESTORE);

            // 2) 플레이 버튼이 같은 자리에 두 번 연속 보이면 (화면 로딩 끝) 누른다 (최대 45초)
            log.Report("공식 런처에서 플레이 버튼을 찾는 중...");
            Point? last = null;
            Point? button = null;
            until = DateTime.UtcNow.AddSeconds(45);
            while (DateTime.UtcNow < until)
            {
                ct.ThrowIfCancellationRequested();
                Thread.Sleep(700);
                var found = FindPlayButton(top);
                if (found != null && last != null && Near(found.Value, last.Value))
                {
                    button = found;
                    break;
                }
                last = found;
            }
            if (button == null) return Result.ButtonNotFound;

            Click(top, content, button.Value);
            log.Report("플레이 버튼을 눌렀어요. 게임이 켜지길 기다리는 중...");

            // 3) 게임 프로세스 확인. 8초 안에 아무 반응이 없고 버튼이 그대로면 한 번만 다시 누른다
            var clickedAt = DateTime.UtcNow;
            var retried = false;
            until = DateTime.UtcNow.AddMinutes(5); // 처음이면 공식 런처가 파일을 받느라 오래 걸릴 수 있음
            while (DateTime.UtcNow < until)
            {
                ct.ThrowIfCancellationRequested();
                Thread.Sleep(750);
                var started = JavaPids().Except(javaBefore).ToList();
                if (started.Count > 0)
                {
                    game = TryGetProcess(started[0]);
                    return Result.Started;
                }
                if (!retried && DateTime.UtcNow - clickedAt > TimeSpan.FromSeconds(8))
                {
                    retried = true;
                    var again = FindPlayButton(top);
                    if (again != null && Near(again.Value, button.Value))
                    {
                        Click(top, content, again.Value);
                        log.Report("반응이 없어 플레이 버튼을 한 번 더 눌렀어요.");
                    }
                }
                if (!IsWindow(top)) break; // 런처가 게임을 띄우고 스스로 닫힌 경우 등
            }
            var late = JavaPids().Except(javaBefore).ToList();
            if (late.Count == 0) return Result.ClickedNoGameYet;
            game = TryGetProcess(late[0]);
            return Result.Started;
        }
        finally
        {
            if (oldDpi != IntPtr.Zero) SetThreadDpiAwarenessContext(oldDpi);
        }
    }

    private static Process? TryGetProcess(int pid)
    {
        try { return Process.GetProcessById(pid); } catch { return null; }
    }

    private static bool Near(Point a, Point b) => Math.Abs(a.X - b.X) <= 4 && Math.Abs(a.Y - b.Y) <= 4;

    private static HashSet<int> JavaPids()
    {
        var set = new HashSet<int>();
        foreach (var name in new[] { "javaw", "java" })
            foreach (var p in Process.GetProcessesByName(name))
            {
                set.Add(p.Id);
                p.Dispose();
            }
        return set;
    }

    // ---------------- 창 찾기 ----------------

    private static IntPtr FindLauncherWindow() =>
        LauncherWindows().FirstOrDefault(IsWindowVisible);

    /// <summary>
    /// 공식 런처의 최상위 창들 (게임 실행 중 숨겨진 창 포함).
    /// 창 클래스 MCLWindow, 또는 제목이 "Minecraft Launcher" 인 런처 프로세스의 창.
    /// </summary>
    private static List<IntPtr> LauncherWindows()
    {
        var pids = new HashSet<uint>();
        foreach (var name in new[] { "Minecraft", "MinecraftLauncher" })
            foreach (var p in Process.GetProcessesByName(name))
            {
                pids.Add((uint)p.Id);
                p.Dispose();
            }

        var list = new List<IntPtr>();
        if (pids.Count == 0) return list;
        EnumWindows((h, _) =>
        {
            GetWindowThreadProcessId(h, out var pid);
            if (!pids.Contains(pid)) return true;
            var cls = new StringBuilder(64);
            GetClassName(h, cls, cls.Capacity);
            var title = new StringBuilder(128);
            GetWindowText(h, title, title.Capacity);
            if (cls.ToString() == "MCLWindow" || title.ToString().Contains("Minecraft Launcher", StringComparison.OrdinalIgnoreCase))
                list.Add(h);
            return true;
        }, IntPtr.Zero);
        return list;
    }

    public static bool HasLauncherWindow() => LauncherWindows().Count > 0;

    /// <summary>공식 런처 창에 닫기 요청을 보낸다 (창의 X 버튼과 같음, 강제 종료 아님)</summary>
    public static int RequestCloseLauncher()
    {
        var wins = LauncherWindows();
        foreach (var h in wins) PostMessage(h, WM_CLOSE, IntPtr.Zero, IntPtr.Zero);
        return wins.Count;
    }

    /// <summary>실제 마우스 입력을 받는 크롬 화면 창 (CefBrowserWindow 아래 Chrome_WidgetWin_*)</summary>
    private static IntPtr FindChromeWidget(IntPtr top)
    {
        var result = IntPtr.Zero;
        EnumChildWindows(top, (h, _) =>
        {
            var sb = new StringBuilder(128);
            GetClassName(h, sb, sb.Capacity);
            if (sb.ToString().StartsWith("Chrome_WidgetWin", StringComparison.Ordinal) && IsWindowVisible(h))
            {
                result = h;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return result;
    }

    // ---------------- 버튼 찾기 ----------------

    // 공식 런처의 플레이 버튼 색 (#3C8527)
    private const int GreenR = 60, GreenG = 133, GreenB = 39, Tol = 16;

    /// <summary>창을 캡처해 가장 큰 초록 버튼의 가운데(창 기준 좌표)를 찾는다</summary>
    internal static Point? FindPlayButton(IntPtr top)
    {
        if (!GetWindowRect(top, out var r)) return null;
        int w = r.R - r.L, h = r.B - r.T;
        if (w < 200 || h < 200) return null;
        var px = Capture(top, w, h);
        return px == null ? null : FindPlayButton(px, w * 4, w, h);
    }

    /// <summary>PrintWindow 로 창 내용을 32비트 BGRA(위→아래) 바이트로 받는다</summary>
    private static byte[]? Capture(IntPtr top, int w, int h)
    {
        var mem = CreateCompatibleDC(IntPtr.Zero);
        if (mem == IntPtr.Zero) return null;
        try
        {
            var bi = new BITMAPINFOHEADER
            {
                biSize = Marshal.SizeOf<BITMAPINFOHEADER>(), biWidth = w, biHeight = -h,
                biPlanes = 1, biBitCount = 32, biCompression = 0,
            };
            var dib = CreateDIBSection(mem, ref bi, 0, out var bits, IntPtr.Zero, 0);
            if (dib == IntPtr.Zero) return null;
            var old = SelectObject(mem, dib);
            try
            {
                if (!PrintWindow(top, mem, PW_RENDERFULLCONTENT)) return null;
                GdiFlush();
                var px = new byte[w * h * 4];
                Marshal.Copy(bits, px, 0, px.Length);
                return px;
            }
            finally
            {
                SelectObject(mem, old);
                DeleteObject(dib);
            }
        }
        finally
        {
            DeleteDC(mem);
        }
    }

    public static Point? FindPlayButton(byte[] px, int stride, int w, int h)
    {
        // 버튼은 넓은 초록 띠: 한 줄에 창 너비의 10% 이상 이어진 초록 픽셀
        var minRun = Math.Max(90, w / 10);
        int bandTop = -1, bandRows = 0, bestRows = 0;
        Point? best = null;
        (int start, int len) bandRun = default;

        for (var y = h / 3; y <= h; y++)
        {
            var row = y < h ? LongestRun(px, stride, y, w) : default;
            if (row.len >= minRun)
            {
                if (bandTop < 0 || Math.Abs(row.start - bandRun.start) > 6)
                {
                    bandTop = y;
                    bandRows = 0;
                    bandRun = row;
                }
                bandRows++;
                continue;
            }
            if (bandTop >= 0)
            {
                // 높이 20~120픽셀인 띠만 버튼으로 본다
                if (bandRows >= 20 && bandRows <= 120 && bandRows > bestRows)
                {
                    bestRows = bandRows;
                    best = new Point(bandRun.start + bandRun.len / 2, bandTop + bandRows / 2);
                }
                bandTop = -1;
                bandRows = 0;
            }
        }
        return best;
    }

    /// <summary>
    /// 한 줄에서 가장 긴 초록 구간. 버튼 가운데 흰 글씨("플레이") 획 정도의 짧은 끊김(maxGap 이하)은 이어진 것으로 본다.
    /// </summary>
    private static (int start, int len) LongestRun(byte[] px, int stride, int y, int w)
    {
        var maxGap = Math.Max(14, w / 32); // 글씨 획 폭 (1296px 창에서 40px)
        int bestStart = 0, bestLen = 0, start = -1, lastGreen = -1;
        var o = y * stride;
        for (var x = 0; x < w; x++, o += 4)
        {
            int b = px[o], g = px[o + 1], r = px[o + 2];
            if (Math.Abs(r - GreenR) > Tol || Math.Abs(g - GreenG) > Tol || Math.Abs(b - GreenB) > Tol) continue;
            if (start < 0 || x - lastGreen > maxGap) start = x;
            lastGreen = x;
            var len = x - start + 1;
            if (len > bestLen) { bestLen = len; bestStart = start; }
        }
        return (bestStart, bestLen);
    }

    // ---------------- 클릭 ----------------

    private static void Click(IntPtr top, IntPtr content, Point inWindow)
    {
        GetWindowRect(top, out var r);
        var pt = new POINT { X = r.L + inWindow.X, Y = r.T + inWindow.Y };
        ScreenToClient(content, ref pt);
        var lp = (IntPtr)((pt.Y << 16) | (pt.X & 0xFFFF));
        PostMessage(content, WM_MOUSEMOVE, IntPtr.Zero, lp);
        Thread.Sleep(80);
        PostMessage(content, WM_LBUTTONDOWN, (IntPtr)MK_LBUTTON, lp);
        Thread.Sleep(60);
        PostMessage(content, WM_LBUTTONUP, IntPtr.Zero, lp);
    }

    // ---------------- Win32 ----------------

    private const int WM_CLOSE = 0x0010, WM_MOUSEMOVE = 0x0200, WM_LBUTTONDOWN = 0x0201, WM_LBUTTONUP = 0x0202, MK_LBUTTON = 1;
    private const uint PW_RENDERFULLCONTENT = 2;
    private const int SW_RESTORE = 9;
    private static readonly IntPtr DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = new(-4);

    [StructLayout(LayoutKind.Sequential)] private struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] private struct POINT { public int X, Y; }
    private delegate bool EnumProc(IntPtr h, IntPtr l);

    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] private static extern bool ScreenToClient(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] private static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr h, int msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] private static extern bool EnumChildWindows(IntPtr parent, EnumProc cb, IntPtr l);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc cb, IntPtr l);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] private static extern bool IsWindow(IntPtr h);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] private static extern IntPtr SetThreadDpiAwarenessContext(IntPtr ctx);

    [StructLayout(LayoutKind.Sequential)]
    private struct BITMAPINFOHEADER
    {
        public int biSize, biWidth, biHeight;
        public short biPlanes, biBitCount;
        public int biCompression, biSizeImage, biXPelsPerMeter, biYPelsPerMeter, biClrUsed, biClrImportant;
    }

    [DllImport("gdi32.dll")] private static extern IntPtr CreateCompatibleDC(IntPtr hdc);
    [DllImport("gdi32.dll")] private static extern bool DeleteDC(IntPtr hdc);
    [DllImport("gdi32.dll")] private static extern IntPtr CreateDIBSection(IntPtr hdc, ref BITMAPINFOHEADER bi, uint usage, out IntPtr bits, IntPtr section, uint offset);
    [DllImport("gdi32.dll")] private static extern IntPtr SelectObject(IntPtr hdc, IntPtr obj);
    [DllImport("gdi32.dll")] private static extern bool DeleteObject(IntPtr obj);
    [DllImport("gdi32.dll")] private static extern bool GdiFlush();
}
