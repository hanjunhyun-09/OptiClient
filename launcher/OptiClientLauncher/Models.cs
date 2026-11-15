using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text.Json.Serialization;

namespace OptiClientLauncher;

public enum LoaderType { Fabric, Forge }

public enum ModSource { Modrinth, Bundled, Local }

/// <summary>런처가 지원하는 게임 버전 + 로더 조합</summary>
public sealed record GameTarget(string Version, LoaderType Loader)
{
    public string Label => $"{Version} · {Loader}";

    public static readonly GameTarget[] All =
    {
        new("26.3", LoaderType.Fabric),
        new("1.21.11", LoaderType.Fabric),
        new("1.21.1", LoaderType.Fabric),
        new("1.20.1", LoaderType.Fabric),
        new("1.8.9", LoaderType.Forge),
    };

    public static GameTarget Find(string version) =>
        All.FirstOrDefault(t => t.Version == version) ?? All[0];

    public override string ToString() => Label;
}

public abstract class Observable : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler? PropertyChanged;

    protected void Set<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
    }

    protected void Raise(string name) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public sealed class ModEntry : Observable
{
    private bool _enabled = true;
    private string? _fileName;
    private string _status = "";

    /// <summary>Modrinth 프로젝트 ID 또는 slug (Local 이면 파일 이름)</summary>
    public string ProjectId { get; set; } = "";
    public string Title { get; set; } = "";
    public ModSource Source { get; set; }
    public bool IsDependency { get; set; }

    public bool Enabled { get => _enabled; set => Set(ref _enabled, value); }

    /// <summary>mods 폴더 안 실제 파일 이름 (설치 전이면 null)</summary>
    public string? FileName { get => _fileName; set { Set(ref _fileName, value); Raise(nameof(SourceLabel)); } }

    [JsonIgnore]
    public string Status { get => _status; set => Set(ref _status, value); }

    [JsonIgnore]
    public string SourceLabel => Source switch
    {
        ModSource.Modrinth => IsDependency ? "Modrinth (의존성)" : "Modrinth",
        ModSource.Bundled => "내장 HUD",
        _ => "로컬 파일",
    } + (FileName == null ? " · 미설치" : "");
}

public sealed class Profile : Observable
{
    private string _name = "새 프로필";
    private string _gameVersion = "1.21.1";
    private int _memoryMb = 4096;

    public string Id { get; set; } = Guid.NewGuid().ToString("N")[..12];

    public string Name { get => _name; set { Set(ref _name, value); Raise(nameof(Subtitle)); } }

    public string GameVersion
    {
        get => _gameVersion;
        set { Set(ref _gameVersion, value); Raise(nameof(Subtitle)); }
    }

    public int MemoryMb { get => _memoryMb; set => Set(ref _memoryMb, value); }

    /// <summary>처음 설치할 때 options.txt 에 성능 설정을 넣을지</summary>
    public bool ApplyPerformanceOptions { get; set; } = true;

    public string ExtraJvmArgs { get; set; } = "";

    /// <summary>마지막으로 맞춘 최적화 프리셋 버전 (Presets.Version 보다 낮으면 빠진 최적화 모드를 한 번 채워 넣는다)</summary>
    public int PresetVersion { get; set; }

    /// <summary>Java 런타임 (Mojang 구성요소 이름). 비우면 버전 기본값, 프리셋이 더 빠른 Java 를 정할 수 있음</summary>
    public string JavaRuntime { get; set; } = "";

    /// <summary>게임 창 크기 (0 이면 마인크래프트 기본값)</summary>
    public int WindowWidth { get; set; }
    public int WindowHeight { get; set; }

    public ObservableCollection<ModEntry> Mods { get; set; } = new();

    [JsonIgnore] public GameTarget Target => GameTarget.Find(GameVersion);
    [JsonIgnore] public LoaderType Loader => Target.Loader;
    [JsonIgnore] public string Subtitle => Target.Label;
    [JsonIgnore] public string GameDir => Path.Combine(Paths.ProfilesDir, Id);
    [JsonIgnore] public string ModsDir => Path.Combine(GameDir, "mods");
}
