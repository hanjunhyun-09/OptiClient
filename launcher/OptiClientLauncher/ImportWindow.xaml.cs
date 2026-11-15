using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace OptiClientLauncher;

public partial class ImportWindow : Window
{
    private readonly Profile? _current;
    private readonly Action<string> _log;
    private readonly ObservableCollection<ImportSource> _sources = new();
    private readonly Dictionary<string, RadioButton> _versionChips = new();
    private bool _busy;

    /// <summary>새로 만든 프로필 (선택한 프로필에 넣었으면 null)</summary>
    public Profile? CreatedProfile { get; private set; }

    public ImportWindow(Profile? current, Action<string> log)
    {
        InitializeComponent();
        _current = current;
        _log = log;
        Sources.ItemsSource = _sources;
        foreach (var t in GameTarget.All)
        {
            var chip = new RadioButton { Content = t.Label, Tag = t.Version, GroupName = "version", Style = (Style)FindResource("Chip") };
            chip.Checked += (_, _) => UpdateState();
            _versionChips[t.Version] = chip;
            VersionChips.Children.Add(chip);
        }
        CurrentTarget.IsEnabled = current != null;
        if (current != null) CurrentText.Text = $"'{current.Name}' ({current.Target.Label}) 프로필에 넣어요.";
    }

    private ImportSource? Selected => Sources.SelectedItem as ImportSource;

    private async void Window_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            var found = await Task.Run(Importer.Scan);
            foreach (var s in found) _sources.Add(s);
            ScanText.Text = found.Count == 0 ? "가져올 수 있는 런처를 찾지 못했어요." : $"{found.Count}곳을 찾았어요";
            if (_sources.Count > 0 && Sources.SelectedIndex < 0) Sources.SelectedIndex = 0;
        }
        catch (Exception ex)
        {
            ScanText.Text = "찾는 중 오류: " + ex.Message;
        }
    }

    private void OpenPack_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = "Modrinth 모드팩 (*.mrpack)|*.mrpack", Title = "가져올 모드팩 선택" };
        if (dlg.ShowDialog(this) != true) return;
        try
        {
            var s = Importer.ReadPack(dlg.FileName);
            _sources.Insert(0, s);
            Sources.SelectedItem = s;
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "모드팩", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void Sources_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var s = Selected;
        ImportButton.IsEnabled = s != null && !_busy;
        if (s == null) return;
        NameBox.Text = s.Name;
        var version = s.Target?.Version ?? _current?.GameVersion ?? GameTarget.All[0].Version;
        _versionChips[version].IsChecked = true;
        UpdateState();
    }

    private void Target_Changed(object sender, RoutedEventArgs e) => UpdateState();

    private string TargetVersion =>
        CurrentTarget.IsChecked == true && _current != null
            ? _current.GameVersion
            : _versionChips.FirstOrDefault(kv => kv.Value.IsChecked == true).Key ?? GameTarget.All[0].Version;

    /// <summary>고른 원본·대상에 맞춰 모드 항목을 켜고 끄고 안내 문구를 바꾼다</summary>
    private void UpdateState()
    {
        if (!IsLoaded && Selected == null) return;
        NewPanel.Visibility = NewTarget.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;
        CurrentText.Visibility = CurrentTarget.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;
        var s = Selected;
        if (s == null) return;

        var target = GameTarget.Find(TargetVersion);
        var modsOk = s.CanImportMods && s.Target!.Version == target.Version;
        ModsCheck.IsEnabled = ConfigsCheck.IsEnabled = modsOk;
        ModsCheck.IsChecked = ConfigsCheck.IsChecked = modsOk;

        HudCheck.Visibility = s.Hud != null ? Visibility.Visible : Visibility.Collapsed;
        NoteText.Text = s.Hud != null
            ? $"{s.Launcher} '{s.Hud.Name}' 프로필에서 켠 HUD(FPS · CPS · 키 입력 · 갑옷 · 포션 · 좌표 · 리치 · 콤보 등)와 위치 · 크기, 줌 · 퍼스펙티브 · 토글 달리기 · 히트박스 · 크로스헤어 같은 기능을 OptiClient 에 똑같이 맞춰요."
              + (s.Launcher == "Badlion" ? " (Badlion 은 위치를 프로필에 저장하지 않아서 위치는 OptiClient 기본 배치예요)" : "")
              + " OptiClient 에 없는 모드(미니맵 · 웨이포인트 등)는 건너뛰어요."
            : !s.HasMods
            ? "모드 없이 게임 설정 · 서버 목록 · 리소스팩 · 셰이더만 가져와요."
            : s.ModCount == 0
                ? "모드가 없는 폴더예요. 게임 설정 · 서버 목록 · 리소스팩 · 셰이더를 가져와요."
                : s.Target == null
                    ? $"{s.Version ?? "버전 모름"} {s.LoaderLabel} 은(는) OptiClient 가 실행할 수 없는 조합이라 모드는 가져올 수 없어요. 게임 설정 · 서버 목록 · 리소스팩 · 셰이더는 가져올 수 있어요."
                    : !modsOk
                        ? $"모드는 {s.Target.Label} 용이라 {target.Label} 에는 넣을 수 없어요. 버전을 {s.Target.Version} 로 맞추면 모드까지 가져와요."
                        : $"모드 {s.ModCount}개와 모드 설정까지 가져와요. 이미 들어 있는 모드(Sodium 등 OptiClient 최적화 모드)는 겹치지 않게 건너뛰어요."
                          + (s.PackFile != null ? " 모드팩 파일은 Modrinth 에서 내려받아요." : "");
        if (s.HasMods && target.Version == "1.8.9" && s.Version != "1.8.9")
            NoteText.Text += " 키 배치는 1.8.9 형식으로 바꿔서 넣어요.";
        else if (s.Version == "1.8.9" && target.Version != "1.8.9")
            NoteText.Text += " 1.8.9 키 배치를 새 버전 형식으로 바꿔서 넣어요.";
    }

    private async void Import_Click(object sender, RoutedEventArgs e)
    {
        var s = Selected;
        if (s == null) return;
        var isNew = NewTarget.IsChecked == true || _current == null;
        var name = NameBox.Text.Trim();
        if (isNew && name.Length == 0) name = s.Name;

        var o = new ImportOptions
        {
            GameSettings = SettingsCheck.IsChecked == true,
            Graphics = GraphicsCheck.IsChecked == true,
            Servers = ServersCheck.IsChecked == true,
            ResourcePacks = PacksCheck.IsChecked == true,
            ShaderPacks = ShadersCheck.IsChecked == true,
            Mods = ModsCheck.IsEnabled && ModsCheck.IsChecked == true,
            Configs = ConfigsCheck.IsEnabled && ConfigsCheck.IsChecked == true,
            Worlds = WorldsCheck.IsChecked == true,
            ClientHud = HudCheck.IsChecked == true,
        };

        var target = isNew ? Presets.NewProfile(name, TargetVersion) : _current!;
        SetBusy(true);
        var progress = new Progress<string>(msg =>
        {
            StatusText.Text = msg;
            _log(msg);
        });
        try
        {
            _log($"가져오기 시작: {s.Launcher} '{s.Name}' → {(isNew ? "새 프로필" : "프로필")} '{target.Name}' ({target.Target.Label})");
            await Importer.RunAsync(s, target, isNew, o, progress, CancellationToken.None);
            _log("가져오기 완료. 플레이를 누르면 빠진 모드를 받아 설치해요.");
            if (isNew) CreatedProfile = target;
            SetBusy(false); // 바쁜 상태면 창 닫기를 막으므로 먼저 푼다
            DialogResult = true;
        }
        catch (Exception ex)
        {
            _log($"가져오기 실패: {ex.Message}");
            if (isNew && Directory.Exists(target.GameDir))
            {
                try { Directory.Delete(target.GameDir, true); } catch (Exception) { /* 지우지 못해도 무시 */ }
            }
            MessageBox.Show(this, "가져오지 못했어요.\n\n" + ex.Message + (ex is IOException ? "\n\n게임이 켜져 있으면 끈 뒤 다시 해 주세요." : ""),
                "가져오기", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private void SetBusy(bool busy)
    {
        _busy = busy;
        ImportButton.IsEnabled = !busy && Selected != null;
        CancelButton.IsEnabled = Sources.IsEnabled = !busy;
        if (busy) StatusText.Text = "가져오는 중...";
    }

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        if (_busy) e.Cancel = true; // 복사 중에는 닫지 않음
        base.OnClosing(e);
    }
}
