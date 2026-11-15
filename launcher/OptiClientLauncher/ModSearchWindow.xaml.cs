using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;

namespace OptiClientLauncher;

public partial class ModSearchWindow : Window
{
    private static readonly (string Label, string? Category)[] Categories =
    {
        ("전체", null),
        ("최적화", "optimization"),
        ("유틸리티", "utility"),
        ("꾸미기", "decoration"),
        ("게임플레이", "game-mechanics"),
        ("모험", "adventure"),
        ("라이브러리", "library"),
    };

    private readonly GameTarget _target;
    private readonly HashSet<string> _installed;
    private readonly ObservableCollection<ModrinthHit> _results = new();
    private readonly Dictionary<string, ModrinthHit> _cart = new();
    private readonly DispatcherTimer _debounce = new() { Interval = TimeSpan.FromMilliseconds(350) };
    private string? _category;
    private int _offset;
    private int _total;
    private CancellationTokenSource? _cts;

    /// <summary>한꺼번에 추가할 모드</summary>
    public List<ModrinthHit> Selected { get; private set; } = new();

    public ModSearchWindow(GameTarget target, IEnumerable<string> installedIds)
    {
        InitializeComponent();
        _target = target;
        _installed = new HashSet<string>(installedIds);
        Title = $"Modrinth 모드 검색 — {target.Label}";
        Results.ItemsSource = _results;

        foreach (var (label, category) in Categories)
        {
            var chip = new RadioButton { Content = label, GroupName = "cat", Style = (Style)FindResource("Chip"), IsChecked = category == null };
            chip.Checked += async (_, _) =>
            {
                _category = category;
                await SearchAsync(reset: true);
            };
            CategoryChips.Children.Add(chip);
        }

        _debounce.Tick += async (_, _) =>
        {
            _debounce.Stop();
            await SearchAsync(reset: true);
        };
        Loaded += async (_, _) =>
        {
            QueryBox.Focus();
            await SearchAsync(reset: true); // 처음엔 인기 모드
        };
    }

    private async Task SearchAsync(bool reset)
    {
        _cts?.Cancel();
        _cts = new CancellationTokenSource();
        var ct = _cts.Token;
        if (reset)
        {
            _offset = 0;
            _results.Clear();
        }
        InfoText.Text = "검색 중...";
        try
        {
            var (hits, total) = await Modrinth.SearchAsync(QueryBox.Text.Trim(), _target, _offset, _category, _installed, ct);
            if (ct.IsCancellationRequested) return;
            foreach (var h in hits)
            {
                if (_cart.ContainsKey(h.ProjectId)) h.Picked = true; // 다른 검색에서 담은 것 유지
                _results.Add(h);
            }
            _offset += hits.Count;
            _total = total;
            InfoText.Text = _results.Count == 0 ? "결과가 없습니다." : $"{_target.Label} 용 모드 {_total:N0}개 중 {_results.Count}개 표시";
            MoreButton.IsEnabled = _results.Count < _total;
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            InfoText.Text = $"검색 실패: {ex.Message}";
        }
    }

    private void QueryBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        _debounce.Stop();
        _debounce.Start(); // 입력을 멈추면 자동 검색
    }

    private async void QueryBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        _debounce.Stop();
        await SearchAsync(reset: true);
    }

    private async void More_Click(object sender, RoutedEventArgs e) => await SearchAsync(reset: false);

    private void Pick_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not ModrinthHit hit || hit.AlreadyInProfile) return;
        hit.Picked = !hit.Picked;
        if (hit.Picked) _cart[hit.ProjectId] = hit;
        else _cart.Remove(hit.ProjectId);
        UpdateCart();
    }

    private void Clear_Click(object sender, RoutedEventArgs e)
    {
        foreach (var h in _cart.Values) h.Picked = false;
        _cart.Clear();
        UpdateCart();
    }

    private void UpdateCart()
    {
        CartTitle.Text = $"담은 모드 {_cart.Count}개";
        CartNames.Text = _cart.Count == 0
            ? "＋ 담기를 눌러 여러 모드를 고른 뒤 한꺼번에 추가하세요"
            : string.Join(", ", _cart.Values.Select(h => h.Title));
        AddButton.Content = _cart.Count == 0 ? "담은 모드 추가" : $"{_cart.Count}개 한꺼번에 추가";
        AddButton.IsEnabled = _cart.Count > 0;
    }

    private void Add_Click(object sender, RoutedEventArgs e)
    {
        Selected = _cart.Values.ToList();
        DialogResult = Selected.Count > 0;
    }
}
