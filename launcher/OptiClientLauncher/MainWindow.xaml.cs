using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using Path = System.IO.Path;
using Rectangle = System.Windows.Shapes.Rectangle;
using System.Windows.Threading;
using Microsoft.Win32;

namespace OptiClientLauncher;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<Profile> _profiles;
    private readonly Dictionary<string, RadioButton> _versionPills = new();
    private bool _switchingProfile;
    private bool _busy;
    private LauncherSettings _settings = AccountStore.Load();
    private Profile? _watched;

    public MainWindow()
    {
        InitializeComponent();
        _profiles = ProfileStore.Load();
        ProfileList.ItemsSource = _profiles;
        BuildVersionPills();
        ProfileList.SelectedIndex = 0;
        UpdateProfileCount();
        UpdateAccountChip();
        Log($"데이터 폴더: {Paths.Root}");
        if (!Directory.Exists(Paths.BundledHudDir))
            Log("(안내) hud 폴더가 없습니다. 프로젝트 루트에서 build-all.ps1 을 실행하면 HUD 모드가 함께 복사됩니다.");
    }

    private Profile? Current => ProfileList.SelectedItem as Profile;

    private void Log(string msg)
    {
        LogBox.AppendText($"[{DateTime.Now:HH:mm:ss}] {msg}\n");
        LogBox.ScrollToEnd();
    }

    private void Save() => ProfileStore.Save(_profiles);

    // =====================================================================
    // 애니메이션
    // =====================================================================

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        _loadingSettings = true;
        CloseOfficialCheck.IsChecked = _settings.CloseOfficialLauncher;
        _loadingSettings = false;

        // 등장: 왼쪽 → 위 → 아래 순서로 미끄러지며 나타남
        AnimateIn(Sidebar, 0, x: -30);
        AnimateIn(Hero, 120, y: 16);
        AnimateIn(TabsPanel, 220, y: 20);

        StartBlobs();
        StartParticles();
        StartPlayPulse();
        StartBusyBar();

        Updater.CleanUp();
        VersionText.Text = $"현재 {Updater.CurrentLabel}" + (Updater.IsDevBuild ? " (개발 빌드)" : "");
        if (Updater.Enabled) _ = CheckUpdateAsync(quiet: true);
    }

    // ---------------- 자동 업데이트 ----------------

    private void CheckUpdate_Click(object sender, RoutedEventArgs e) => _ = CheckUpdateAsync(quiet: false);

    /// <summary>새 버전이 있으면 업데이트할지 묻는다 (quiet 면 없을 때·실패할 때 아무 말 안 함)</summary>
    private async Task CheckUpdateAsync(bool quiet)
    {
        if (Updater.Repo.Length == 0)
        {
            if (!quiet) ShowToast("업데이트 저장소가 아직 정해지지 않았어요.");
            return;
        }
        if (Updater.IsDevBuild && !quiet)
        {
            ShowToast("개발 빌드는 자동 업데이트하지 않아요.");
            return;
        }
        UpdateButton.IsEnabled = false;
        try
        {
            var r = await Updater.CheckAsync();
            if (r == null)
            {
                if (!quiet) ShowToast($"최신 버전이에요 ({Updater.CurrentLabel}).");
                return;
            }
            Log($"새 버전이 있어요: {r.Tag} (지금 {Updater.CurrentLabel})");
            var notes = r.Notes.Length > 600 ? r.Notes[..600] + "..." : r.Notes;
            var ask = MessageBox.Show(this,
                $"새 버전 {r.Tag} 이 나왔어요. (지금 {Updater.CurrentLabel})\n\n{notes}\n\n지금 업데이트할까요? 런처가 잠깐 꺼졌다 다시 켜져요.",
                "OptiClient 업데이트", MessageBoxButton.YesNo, MessageBoxImage.Information);
            if (ask != MessageBoxResult.Yes) return;
            if (_game is { HasExited: false })
            {
                MessageBox.Show(this, "게임이 켜져 있어요. 게임을 끈 뒤 설정 탭에서 '업데이트 확인'을 눌러 주세요.", "OptiClient 업데이트");
                return;
            }
            SetBusy(true, "업데이트 중...");
            await Updater.ApplyAsync(r, new Progress<string>(m =>
            {
                Log(m);
                StatusText.Text = m;
            }));
            Save();
            Application.Current.Shutdown();
        }
        catch (Exception ex)
        {
            Log($"업데이트 실패: {ex.Message}");
            if (!quiet) MessageBox.Show(this, "업데이트하지 못했어요.\n\n" + ex.Message, "OptiClient 업데이트", MessageBoxButton.OK, MessageBoxImage.Warning);
            SetBusy(false);
        }
        finally
        {
            UpdateButton.IsEnabled = true;
        }
    }

    private static void AnimateIn(FrameworkElement el, int delayMs, double x = 0, double y = 0)
    {
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        var begin = TimeSpan.FromMilliseconds(delayMs);
        var tt = new TranslateTransform(x, y);
        el.RenderTransform = tt;
        el.Opacity = 0;
        el.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(450)) { BeginTime = begin });
        if (x != 0) tt.BeginAnimation(TranslateTransform.XProperty, new DoubleAnimation(x, 0, TimeSpan.FromMilliseconds(550)) { BeginTime = begin, EasingFunction = ease });
        if (y != 0) tt.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(y, 0, TimeSpan.FromMilliseconds(550)) { BeginTime = begin, EasingFunction = ease });
    }

    /// <summary>흐릿한 빛 덩어리를 천천히 떠다니게 한다</summary>
    private void StartBlobs()
    {
        void Drift(UIElement el, double dx, double dy, double seconds)
        {
            var left = Canvas.GetLeft(el);
            var top = Canvas.GetTop(el);
            var ease = new SineEase { EasingMode = EasingMode.EaseInOut };
            el.BeginAnimation(Canvas.LeftProperty, new DoubleAnimation(left, left + dx, TimeSpan.FromSeconds(seconds))
                { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = ease });
            el.BeginAnimation(Canvas.TopProperty, new DoubleAnimation(top, top + dy, TimeSpan.FromSeconds(seconds * 1.3))
                { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = ease });
        }
        Drift(Blob1, 260, -140, 11);
        Drift(Blob2, -240, 160, 13);
        Drift(Blob3, 180, -220, 15);
    }

    private sealed class Particle
    {
        public required Rectangle Shape;
        public double X, Y, Speed, Phase;
    }

    private readonly List<Particle> _particles = new();
    private readonly Random _rnd = new();
    private DateTime _lastFrame = DateTime.Now;

    /// <summary>아래에서 위로 떠오르며 반짝이는 작은 사각형</summary>
    private void StartParticles()
    {
        Color[] palette = { Color.FromRgb(0xE5, 0x38, 0x3B), Color.FromRgb(0xFF, 0x7A, 0x45), Color.FromRgb(0xFF, 0xFF, 0xFF), Color.FromRgb(0xA7, 0x8B, 0xFA) };
        for (int i = 0; i < 46; i++)
        {
            double size = 2 + _rnd.Next(3);
            var rect = new Rectangle
            {
                Width = size,
                Height = size,
                Fill = new SolidColorBrush(palette[_rnd.Next(palette.Length)]),
                RadiusX = 1,
                RadiusY = 1,
            };
            Particles.Children.Add(rect);
            _particles.Add(new Particle
            {
                Shape = rect,
                X = _rnd.NextDouble(),
                Y = _rnd.NextDouble(),
                Speed = 0.02 + _rnd.NextDouble() * 0.05,
                Phase = _rnd.NextDouble() * Math.PI * 2,
            });
        }
        CompositionTarget.Rendering += OnRenderFrame;
    }

    private void OnRenderFrame(object? sender, EventArgs e)
    {
        var now = DateTime.Now;
        var dt = Math.Min(0.1, (now - _lastFrame).TotalSeconds);
        _lastFrame = now;
        double w = Particles.ActualWidth, h = Particles.ActualHeight;
        if (w <= 0 || h <= 0) return;
        double t = now.TimeOfDay.TotalSeconds;
        foreach (var p in _particles)
        {
            p.Y -= p.Speed * dt;
            if (p.Y < -0.02)
            {
                p.Y = 1.02;
                p.X = _rnd.NextDouble();
            }
            Canvas.SetLeft(p.Shape, (p.X + 0.012 * Math.Sin(t * 0.7 + p.Phase)) * w);
            Canvas.SetTop(p.Shape, p.Y * h);
            p.Shape.Opacity = 0.15 + 0.45 * (0.5 + 0.5 * Math.Sin(t * 2 + p.Phase));
        }
    }

    /// <summary>플레이 버튼 뒤 빛이 맥박처럼 퍼진다</summary>
    private void StartPlayPulse()
    {
        var ease = new SineEase { EasingMode = EasingMode.EaseInOut };
        var scale = new DoubleAnimation(0.96, 1.08, TimeSpan.FromSeconds(1.4)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = ease };
        GlowScale.BeginAnimation(ScaleTransform.ScaleXProperty, scale);
        GlowScale.BeginAnimation(ScaleTransform.ScaleYProperty, scale);
        PlayGlow.BeginAnimation(OpacityProperty, new DoubleAnimation(0.35, 0.75, TimeSpan.FromSeconds(1.4))
            { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = ease });
    }

    /// <summary>설치 중 진행 막대가 좌우로 흐른다</summary>
    private void StartBusyBar()
    {
        var anim = new DoubleAnimation(-160, 900, TimeSpan.FromSeconds(1.2)) { RepeatBehavior = RepeatBehavior.Forever };
        BusyMove.BeginAnimation(TranslateTransform.XProperty, anim);
    }

    private readonly DispatcherTimer _toastTimer = new() { Interval = TimeSpan.FromSeconds(3.2) };

    private void ShowToast(string text)
    {
        ToastText.Text = text;
        var ease = new BackEase { EasingMode = EasingMode.EaseOut, Amplitude = 0.4 };
        Toast.BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(250)));
        ToastMove.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(20, 0, TimeSpan.FromMilliseconds(400)) { EasingFunction = ease });
        _toastTimer.Stop();
        _toastTimer.Tick -= HideToast;
        _toastTimer.Tick += HideToast;
        _toastTimer.Start();
    }

    private void HideToast(object? sender, EventArgs e)
    {
        _toastTimer.Stop();
        Toast.BeginAnimation(OpacityProperty, new DoubleAnimation(0, TimeSpan.FromMilliseconds(300)));
        ToastMove.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(12, TimeSpan.FromMilliseconds(300)));
    }

    /// <summary>선택한 버전 색으로 히어로 배경을 물들인다</summary>
    private void TintHero(string version)
    {
        if (FindName("HeroTint") is not GradientStop stop) return;
        var c = VersionBrushConverter.ColorFor(version);
        stop.BeginAnimation(GradientStop.ColorProperty, new ColorAnimation(Color.FromArgb(0x66, c.R, c.G, c.B), TimeSpan.FromMilliseconds(500)));
    }

    // ---------------- 제목 표시줄 ----------------

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    // =====================================================================
    // 상태 표시
    // =====================================================================

    private void SetBusy(bool busy, string status = "")
    {
        _busy = busy;
        Busy.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        InstallButton.IsEnabled = PlayButton.IsEnabled = !busy;
        ProfileList.IsEnabled = !busy;
        if (status.Length > 0) StatusText.Text = status;
    }

    private void UpdateProfileCount() => ProfileCount.Text = $"{_profiles.Count}개";

    private void UpdateModCount()
    {
        if (Current == null) return;
        int on = Current.Mods.Count(m => m.Enabled);
        ModCountText.Text = $"모드 {on}/{Current.Mods.Count}개 켜짐";
    }

    private void Mods_CollectionChanged(object? sender, NotifyCollectionChangedEventArgs e) => UpdateModCount();

    // =====================================================================
    // 프로필
    // =====================================================================

    private void BuildVersionPills()
    {
        foreach (var t in GameTarget.All)
        {
            var pill = new RadioButton
            {
                Content = t.Label,
                Tag = t.Version,
                GroupName = "version",
                Style = (Style)FindResource("Pill"),
            };
            pill.Checked += VersionPill_Checked;
            _versionPills[t.Version] = pill;
            VersionPills.Children.Add(pill);
        }
    }

    private void ProfileList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (Current == null) return;
        if (_watched != null) _watched.Mods.CollectionChanged -= Mods_CollectionChanged;
        _watched = Current;
        _watched.Mods.CollectionChanged += Mods_CollectionChanged;

        _switchingProfile = true;
        if (_versionPills.TryGetValue(Current.GameVersion, out var pill)) pill.IsChecked = true;
        _switchingProfile = false;

        UpdateModCount();
        TintHero(Current.GameVersion);
        if (IsLoaded)
        {
            AnimateIn(Hero, 0, y: 10);
            AnimateIn(TabsPanel, 80, y: 12);
        }
    }

    private void NewProfile_Click(object sender, RoutedEventArgs e)
    {
        var p = Presets.NewProfile("새 프로필", GameTarget.All[0].Version);
        _profiles.Add(p);
        ProfileList.SelectedItem = p;
        UpdateProfileCount();
        Save();
        ShowToast("새 프로필을 만들었어요. 설정 탭에서 이름과 버전을 바꿀 수 있어요.");
    }

    private void ImportFrom_Click(object sender, RoutedEventArgs e)
    {
        if (_game is { HasExited: false })
        {
            ShowToast("게임을 끈 뒤에 가져와 주세요.");
            return;
        }
        var win = new ImportWindow(Current, Log) { Owner = this };
        if (win.ShowDialog() != true) return;
        if (win.CreatedProfile is { } p)
        {
            _profiles.Add(p);
            ProfileList.SelectedItem = p;
            UpdateProfileCount();
            ShowToast($"'{p.Name}' 프로필로 가져왔어요. 플레이를 누르면 설치돼요.");
        }
        else
        {
            ShowToast("선택한 프로필로 가져왔어요.");
        }
        Save();
        UpdateModCount();
    }

    private void CloneProfile_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        var src = Current;
        var p = new Profile
        {
            Name = src.Name + " (복사본)",
            GameVersion = src.GameVersion,
            MemoryMb = src.MemoryMb,
            ExtraJvmArgs = src.ExtraJvmArgs,
            ApplyPerformanceOptions = src.ApplyPerformanceOptions,
        };
        foreach (var m in src.Mods.Where(m => m.Source != ModSource.Local))
            p.Mods.Add(new ModEntry { ProjectId = m.ProjectId, Title = m.Title, Source = m.Source, IsDependency = m.IsDependency, Enabled = m.Enabled });
        _profiles.Add(p);
        ProfileList.SelectedItem = p;
        UpdateProfileCount();
        Save();
        Log($"'{src.Name}' 을 복제했습니다. (로컬 jar 모드는 복사되지 않습니다)");
        ShowToast($"'{src.Name}' 을 복제했어요.");
    }

    private void DeleteProfile_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        var p = Current;
        var r = MessageBox.Show(this,
            $"'{p.Name}' 프로필을 목록에서 지울까요?\n\n[예] 프로필 + 게임 폴더(월드·스크린샷·설정)까지 영구 삭제\n[아니요] 프로필만 지우고 게임 폴더는 남김\n\n게임 폴더: {p.GameDir}",
            "프로필 삭제", MessageBoxButton.YesNoCancel, MessageBoxImage.Warning);
        if (r == MessageBoxResult.Cancel) return;

        try { LauncherProfiles.Remove(p); } catch (Exception ex) { Log($"공식 런처 프로필 제거 실패: {ex.Message}"); }
        if (r == MessageBoxResult.Yes && Directory.Exists(p.GameDir))
        {
            Directory.Delete(p.GameDir, true);
            Log($"게임 폴더 삭제: {p.GameDir}");
        }
        _profiles.Remove(p);
        if (_profiles.Count == 0) _profiles.Add(Presets.NewProfile("새 프로필", GameTarget.All[0].Version));
        ProfileList.SelectedIndex = 0;
        UpdateProfileCount();
        Save();
    }

    private void VersionPill_Checked(object sender, RoutedEventArgs e)
    {
        if (_switchingProfile || Current == null || sender is not RadioButton { Tag: string version }) return;
        var p = Current;
        if (p.GameVersion == version) return;
        var t = GameTarget.Find(version);

        var r = MessageBox.Show(this,
            $"{p.Target.Label} → {t.Label} 로 바꿉니다.\n\n모드 목록을 새 버전용 최적화 프리셋으로 바꿀까요?\n[예] 프리셋으로 교체   [아니요] 현재 목록 유지 (새 버전용으로 다시 받음)",
            "버전 변경", MessageBoxButton.YesNoCancel, MessageBoxImage.Question);
        if (r == MessageBoxResult.Cancel)
        {
            _switchingProfile = true;
            _versionPills[p.GameVersion].IsChecked = true;
            _switchingProfile = false;
            return;
        }

        // 기존 버전용 jar 정리 (로컬 파일은 유지)
        foreach (var m in p.Mods.Where(m => m.Source != ModSource.Local))
        {
            Installer.DeleteModFile(p, m);
            m.FileName = null;
        }
        p.GameVersion = t.Version;
        if (r == MessageBoxResult.Yes)
        {
            var locals = p.Mods.Where(m => m.Source == ModSource.Local).ToList();
            p.Mods.Clear();
            p.Mods.Add(Presets.HudMod());
            foreach (var m in Presets.OptimizationMods(t)) p.Mods.Add(m);
            foreach (var m in locals) p.Mods.Add(m);
        }
        if (p.Mods.Any(m => m.Source == ModSource.Local))
            Log("(주의) 직접 추가한 jar 모드는 새 버전과 맞지 않을 수 있습니다.");
        Save();
        UpdateModCount();
        TintHero(t.Version);
        Log($"버전을 {t.Label} 로 바꿨습니다. '설치'를 누르면 새 버전용 모드를 받습니다.");
        ShowToast($"{t.Label} 로 바꿨어요.");
    }

    // =====================================================================
    // 모드
    // =====================================================================

    private void SearchMods_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        var p = Current;
        var win = new ModSearchWindow(p.Target, p.Mods.Select(m => m.ProjectId)) { Owner = this };
        if (win.ShowDialog() != true) return;
        int added = 0;
        foreach (var hit in win.Selected)
        {
            if (p.Mods.Any(m => m.ProjectId == hit.ProjectId || m.ProjectId == hit.Slug)) continue;
            p.Mods.Add(new ModEntry { ProjectId = hit.ProjectId, Title = hit.Title, Source = ModSource.Modrinth });
            Log($"추가: {hit.Title} (설치를 누르면 내려받습니다)");
            added++;
        }
        Save();
        if (added > 0) ShowToast($"모드 {added}개를 추가했어요. 플레이를 누르면 설치돼요.");
    }

    private void AddLocalMod_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        var p = Current;
        var dlg = new OpenFileDialog { Filter = "마인크래프트 모드 (*.jar)|*.jar", Multiselect = true, Title = "추가할 모드 jar 선택" };
        if (dlg.ShowDialog(this) != true) return;
        Directory.CreateDirectory(p.ModsDir);
        foreach (var file in dlg.FileNames)
        {
            var name = Path.GetFileName(file);
            File.Copy(file, Path.Combine(p.ModsDir, name), true);
            if (p.Mods.All(m => m.FileName != name))
                p.Mods.Add(new ModEntry { ProjectId = name, Title = Path.GetFileNameWithoutExtension(name), Source = ModSource.Local, FileName = name });
            Log($"로컬 모드 추가: {name}");
        }
        Save();
        ShowToast($"jar {dlg.FileNames.Length}개를 추가했어요.");
    }

    private void AddPreset_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        var p = Current;
        var added = 0;
        if (p.Mods.All(m => m.Source != ModSource.Bundled)) { p.Mods.Insert(0, Presets.HudMod()); added++; }
        foreach (var m in Presets.OptimizationMods(p.Target))
        {
            if (p.Mods.Any(x => x.Title == m.Title || x.ProjectId == m.ProjectId)) continue;
            p.Mods.Add(m);
            added++;
        }
        Save();
        Log(added == 0 ? "프리셋 모드가 이미 모두 들어 있습니다." : $"최적화 프리셋 모드 {added}개를 추가했습니다.");
        ShowToast(added == 0 ? "프리셋 모드가 이미 모두 들어 있어요." : $"최적화 모드 {added}개를 추가했어요.");
        if (p.Target.Version == "1.8.9")
            Log("(안내) 1.8.9 OptiFine 은 자동 설치가 불가능합니다. optifine.net 에서 받아 '파일 추가'로 넣으세요.");
    }

    private void RemoveMod_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null || ModList.SelectedItems.Count == 0) return;
        var p = Current;
        var selected = ModList.SelectedItems.Cast<ModEntry>().ToList();
        foreach (var m in selected)
        {
            Installer.DeleteModFile(p, m);
            p.Mods.Remove(m);
            Log($"삭제: {m.Title}");
        }
        Save();
        ShowToast($"모드 {selected.Count}개를 지웠어요.");
    }

    private void ModEnabled_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null || (sender as FrameworkElement)?.DataContext is not ModEntry m) return;
        try
        {
            Installer.ApplyEnabled(Current, m);
        }
        catch (IOException ex)
        {
            Log($"모드 파일을 바꾸지 못했습니다 (게임이 실행 중인가요?): {ex.Message}");
        }
        UpdateModCount();
        Save();
    }

    private void ApplyOptions_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        Presets.ApplyPerformanceOptions(Current, firstInstall: false);
        Log("options.txt 에 성능 옵션을 적용했습니다. (게임이 꺼져 있을 때 적용하세요)");
        ShowToast("성능 옵션을 적용했어요.");
    }

    private void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        Directory.CreateDirectory(Current.ModsDir);
        Process.Start(new ProcessStartInfo("explorer.exe", Current.GameDir) { UseShellExecute = true });
    }

    // =====================================================================
    // 설치 / 플레이
    // =====================================================================

    /// <summary>설치하고 실행할 버전 id 를 돌려준다 (실패하면 null)</summary>
    private async Task<string?> InstallCurrentAsync(bool forOfficialLauncher)
    {
        if (Current == null) return null;
        var p = Current;

        if (forOfficialLauncher && Installer.IsOfficialLauncherRunning())
        {
            // 켜진 런처는 프로필 파일을 덮어쓸 수 있어서, 닫았다가 다시 연다 (게임이 켜져 있으면 건드리지 않음)
            Log("공식 런처가 켜져 있어 설치 구성을 반영하려고 한 번 닫았다가 다시 열어요.");
            StatusText.Text = "공식 런처를 닫는 중...";
            if (!await Task.Run(() => Installer.CloseOfficialLauncher(TimeSpan.FromSeconds(10))))
            {
                MessageBox.Show(this,
                    "공식 런처가 닫히지 않았어요.\n공식 런처로 켠 게임이 아직 실행 중이면 게임을 끈 뒤, 공식 런처를 닫고 다시 눌러 주세요.",
                    "공식 런처 실행 중", MessageBoxButton.OK, MessageBoxImage.Warning);
                return null;
            }
        }

        SetBusy(true, $"'{p.Name}' 설치 중...");
        try
        {
            var installer = new Installer(new Progress<string>(msg =>
            {
                Log(msg);
                StatusText.Text = msg;
            }));
            var versionId = await installer.InstallAsync(p, CancellationToken.None);
            Save();
            UpdateModCount();
            StatusText.Text = "설치 완료";
            return versionId;
        }
        catch (Exception ex)
        {
            Log($"설치 실패: {ex.Message}");
            StatusText.Text = "설치 실패 — 로그 탭을 확인하세요";
            Tabs.SelectedIndex = 2;
            if (p.Loader == LoaderType.Forge)
                Log("Forge 자동 설치가 실패하면 https://files.minecraftforge.net 에서 1.8.9 설치 프로그램으로 직접 설치한 뒤 다시 시도하세요.");
            return null;
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async void Install_Click(object sender, RoutedEventArgs e)
    {
        if (await InstallCurrentAsync(forOfficialLauncher: false) != null && Current != null)
            ShowToast($"'{Current.Name}' 설치 완료!");
    }

    // ---------------- 계정 ----------------

    private void UpdateAccountChip()
    {
        var a = _settings.Account;
        AccountName.Text = a?.Name ?? "로그인하기";
        AccountType.Text = a == null ? "계정을 정해 주세요" : a.IsOfficial ? "공식 런처로 실행" : $"{a.Label} 계정";
        AccountAvatar.Text = a == null || a.Name.Length == 0 ? "?" : a.Name[..1].ToUpperInvariant();
    }

    private bool OpenAccountWindow()
    {
        var ok = new AccountWindow(_settings) { Owner = this }.ShowDialog() == true;
        _settings = AccountStore.Load();
        UpdateAccountChip();
        return ok;
    }

    private bool _loadingSettings;

    /// <summary>설정 탭의 "게임이 켜지면 공식 런처 닫기" 체크박스</summary>
    private void CloseOfficial_Changed(object sender, RoutedEventArgs e)
    {
        if (_loadingSettings) return;
        _settings.CloseOfficialLauncher = CloseOfficialCheck.IsChecked == true;
        AccountStore.Save(_settings);
    }

    private void Account_Click(object sender, System.Windows.Input.MouseButtonEventArgs e) => OpenAccountWindow();

    /// <summary>저장된 계정으로 게임용 계정 정보를 만든다 (Microsoft 는 토큰 새로 받기)</summary>
    private async Task<Account?> GetPlayAccountAsync()
    {
        var a = _settings.Account;
        if (a == null)
        {
            if (!OpenAccountWindow()) return null;
            a = _settings.Account;
            if (a == null) return null;
        }
        if (a.IsOffline) return new Account(a.Name, a.Uuid, "0", "0", IsOffline: true);

        StatusText.Text = "Microsoft 로그인 확인 중...";
        try
        {
            var refresh = AccountStore.Unprotect(a.ProtectedRefreshToken ?? throw new MicrosoftAuth.AuthException("저장된 로그인 정보가 없습니다."));
            var (access, newRefresh) = await MicrosoftAuth.RefreshAsync(_settings.MicrosoftClientId, refresh, CancellationToken.None);
            a.ProtectedRefreshToken = AccountStore.Protect(newRefresh);
            AccountStore.Save(_settings);
            return await MicrosoftAuth.LoginMinecraftAsync(access, CancellationToken.None);
        }
        catch (Exception ex)
        {
            Log($"로그인 실패: {ex.Message}");
            MessageBox.Show(this, ex.Message + "\n\n계정 화면에서 다시 로그인하거나 오프라인 닉네임을 쓰세요.", "로그인", MessageBoxButton.OK, MessageBoxImage.Warning);
            OpenAccountWindow();
            return null;
        }
    }

    // ---------------- 실행 ----------------

    private System.Diagnostics.Process? _game;

    /// <summary>플레이: 설치 → 로그인 → 공식 런처 없이 바로 실행</summary>
    private async void Play_Click(object sender, RoutedEventArgs e)
    {
        if (Current == null) return;
        if (_game is { HasExited: false })
        {
            ShowToast("게임이 이미 실행 중이에요.");
            return;
        }
        var p = Current;
        if (_settings.Account?.IsOfficial == true)
        {
            await PlayOfficialAsync();
            return;
        }
        var account = await GetPlayAccountAsync();
        if (account == null) return;
        var versionId = await InstallCurrentAsync(forOfficialLauncher: false);
        if (versionId == null) return;

        SetBusy(true, "게임 파일 확인 중... (처음에는 몇 분 걸릴 수 있어요)");
        try
        {
            var launcher = new GameLauncher(new Progress<string>(msg =>
            {
                Log(msg);
                StatusText.Text = msg;
            }));
            _game = await launcher.LaunchAsync(p, versionId, account, CancellationToken.None);
            ShowToast($"'{p.Name}' 실행! ({account.Name})");
            StatusText.Text = $"게임 실행 중 · {p.Name} · {account.Name}";
            PlayLabel.Text = "실행 중";
            _game.Exited += (_, _) => Dispatcher.Invoke(() =>
            {
                PlayLabel.Text = "플레이";
                StatusText.Text = "게임이 종료됐습니다.";
            });
            Log("게임 안에서: Right Shift = 메뉴, C = 줌, V = 달리기 토글");
        }
        catch (Exception ex)
        {
            Log($"실행 실패: {ex.Message}");
            StatusText.Text = "실행 실패 — 로그 탭을 확인하세요";
            Tabs.SelectedIndex = 2;
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async void OfficialLauncher_Click(object sender, RoutedEventArgs e) => await PlayOfficialAsync();

    /// <summary>
    /// 정품 실행: 공식 런처에 설치 구성을 등록(가장 최근 사용으로 표시)하고,
    /// 공식 런처를 열어 플레이 버튼까지 자동으로 누른다.
    /// </summary>
    private async Task PlayOfficialAsync()
    {
        if (Current == null) return;
        var p = Current;
        if (await InstallCurrentAsync(forOfficialLauncher: true) == null) return;

        SetBusy(true, "공식 런처로 실행 중...");
        try
        {
            var result = await OfficialAutoPlay.RunAsync(new Progress<string>(msg =>
            {
                Log(msg);
                StatusText.Text = msg;
            }), CancellationToken.None, closeLauncherAfter: _settings.CloseOfficialLauncher);

            switch (result)
            {
                case OfficialAutoPlay.Result.Started:
                    ShowToast($"'{p.Name}' 정품으로 실행!");
                    StatusText.Text = $"게임 실행 중 · {p.Name} · 정품";
                    Log("게임 안에서: Right Shift = 메뉴, C = 줌, V = 달리기 토글");
                    break;
                case OfficialAutoPlay.Result.ClickedNoGameYet:
                    StatusText.Text = "공식 런처에서 진행 상황을 확인하세요";
                    Log("플레이를 눌렀지만 아직 게임이 안 켜졌어요. 공식 런처 화면을 확인해 주세요 (로그인 필요, 다운로드 중 등).");
                    break;
                case OfficialAutoPlay.Result.ButtonNotFound:
                    StatusText.Text = "공식 런처에서 [플레이]를 직접 눌러 주세요";
                    Log($"공식 런처의 플레이 버튼을 찾지 못했어요. 자바 에디션 화면에서 'OptiClient · {p.Name}' 이 선택된 걸 확인하고 [플레이]를 눌러 주세요.");
                    ShowToast("공식 런처에서 플레이를 직접 눌러 주세요");
                    break;
                default:
                    StatusText.Text = "공식 런처를 찾지 못했어요";
                    Log("공식 마인크래프트 런처를 찾지 못했어요. Microsoft Store 또는 minecraft.net 에서 설치해 주세요.");
                    break;
            }
        }
        catch (Exception ex)
        {
            Log($"공식 런처 실행 실패: {ex.Message}");
            StatusText.Text = "실행 실패 — 로그 탭을 확인하세요";
        }
        finally
        {
            SetBusy(false);
        }
    }

    private void Window_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (_busy)
        {
            e.Cancel = MessageBox.Show(this, "설치 중입니다. 그래도 닫을까요?", "OptiClient", MessageBoxButton.YesNo) != MessageBoxResult.Yes;
            if (e.Cancel) return;
        }
        CompositionTarget.Rendering -= OnRenderFrame;
        Save();
    }
}
