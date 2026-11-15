using System.Diagnostics;
using System.Windows;
using System.Windows.Navigation;

namespace OptiClientLauncher;

public partial class AccountWindow : Window
{
    private readonly LauncherSettings _settings;
    private CancellationTokenSource? _login;
    private MicrosoftAuth.DeviceCode? _code;

    public AccountWindow(LauncherSettings settings)
    {
        InitializeComponent();
        _settings = settings;
        ClientIdBox.Text = settings.MicrosoftClientId;
        OfflineName.Text = settings.Account?.IsOffline == true ? settings.Account.Name : "";
        Tabs.SelectedIndex = settings.Account?.Type switch { "offline" => 1, "microsoft" => 2, _ => 0 };
        ShowCurrent();
    }

    private void ShowCurrent()
    {
        var a = _settings.Account;
        CurrentName.Text = a?.Name ?? "로그인한 계정 없음";
        CurrentType.Text = a == null ? "정품 또는 오프라인 중에서 골라 주세요" : a.IsOfficial ? "공식 런처에 로그인된 계정으로 실행" : $"{a.Label} 계정";
        AvatarText.Text = a == null || a.Name.Length == 0 ? "?" : a.Name[..1].ToUpperInvariant();
    }

    private void UseOfficial_Click(object sender, RoutedEventArgs e)
    {
        _settings.Account = new SavedAccount { Type = "official", Name = "정품 계정" };
        AccountStore.Save(_settings);
        ShowCurrent();
        DialogResult = true;
    }

    private void UseOffline_Click(object sender, RoutedEventArgs e)
    {
        var name = OfflineName.Text.Trim();
        if (!AccountStore.IsValidOfflineName(name))
        {
            MessageBox.Show(this, "닉네임은 영문·숫자·밑줄(_) 3~16자로 정해 주세요.", "계정", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }
        _settings.Account = new SavedAccount { Type = "offline", Name = name, Uuid = AccountStore.OfflineUuid(name) };
        AccountStore.Save(_settings);
        ShowCurrent();
        DialogResult = true;
    }

    private async void MsLogin_Click(object sender, RoutedEventArgs e)
    {
        var clientId = ClientIdBox.Text.Trim();
        if (!Guid.TryParse(clientId, out _))
        {
            MsStatus.Text = "앱 클라이언트 ID 를 먼저 넣어 주세요 (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx 형식).";
            return;
        }
        _settings.MicrosoftClientId = clientId;
        AccountStore.Save(_settings);

        _login?.Cancel();
        _login = new CancellationTokenSource();
        var ct = _login.Token;
        MsLoginButton.IsEnabled = false;
        try
        {
            MsStatus.Text = "로그인 코드를 받는 중...";
            _code = await MicrosoftAuth.StartAsync(clientId, ct);
            UserCode.Text = _code.UserCode;
            CodePanel.Visibility = Visibility.Visible;
            OpenBrowser();
            MsStatus.Text = "브라우저에서 코드를 넣고 로그인하면 자동으로 이어져요...";

            var (access, refresh) = await MicrosoftAuth.WaitForLoginAsync(clientId, _code, ct);
            MsStatus.Text = "마인크래프트 계정 확인 중...";
            var account = await MicrosoftAuth.LoginMinecraftAsync(access, ct);
            _settings.Account = new SavedAccount
            {
                Type = "microsoft",
                Name = account.Name,
                Uuid = account.Uuid,
                Xuid = account.Xuid,
                ProtectedRefreshToken = AccountStore.Protect(refresh),
            };
            AccountStore.Save(_settings);
            CodePanel.Visibility = Visibility.Collapsed;
            MsStatus.Text = $"{account.Name} 으로 로그인했어요.";
            ShowCurrent();
            DialogResult = true;
        }
        catch (OperationCanceledException)
        {
            MsStatus.Text = "로그인을 취소했어요.";
        }
        catch (Exception ex)
        {
            MsStatus.Text = ex.Message;
            CodePanel.Visibility = Visibility.Collapsed;
        }
        finally
        {
            MsLoginButton.IsEnabled = true;
        }
    }

    private void OpenBrowser()
    {
        if (_code == null) return;
        try { Clipboard.SetText(_code.UserCode); } catch { /* 클립보드가 잠겨 있으면 건너뜀 */ }
        Process.Start(new ProcessStartInfo($"https://www.microsoft.com/link?otc={_code.UserCode}") { UseShellExecute = true });
    }

    private void OpenBrowser_Click(object sender, RoutedEventArgs e) => OpenBrowser();

    private void CancelLogin_Click(object sender, RoutedEventArgs e)
    {
        _login?.Cancel();
        CodePanel.Visibility = Visibility.Collapsed;
    }

    private void Logout_Click(object sender, RoutedEventArgs e)
    {
        _settings.Account = null;
        AccountStore.Save(_settings);
        ShowCurrent();
    }

    private void Link_Click(object sender, RequestNavigateEventArgs e)
    {
        Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true });
        e.Handled = true;
    }

    protected override void OnClosed(EventArgs e)
    {
        _login?.Cancel();
        base.OnClosed(e);
    }
}
