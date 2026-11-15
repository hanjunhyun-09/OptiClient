using System.Globalization;
using System.Windows.Data;
using System.Windows.Media;

namespace OptiClientLauncher;

/// <summary>버전마다 다른 배지 색</summary>
public sealed class VersionBrushConverter : IValueConverter
{
    public static Color ColorFor(string? version) => version switch
    {
        "26.3" => Color.FromRgb(0xE5, 0x38, 0x3B),
        "1.21.11" => Color.FromRgb(0xFF, 0x7A, 0x45),
        "1.21.1" => Color.FromRgb(0xFF, 0x5C, 0x9A),
        "1.20.1" => Color.FromRgb(0xA7, 0x8B, 0xFA),
        "1.8.9" => Color.FromRgb(0x4C, 0xC9, 0xF0),
        _ => Color.FromRgb(0x8E, 0x93, 0xA2),
    };

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var c = ColorFor(value as string);
        if (parameter as string == "soft") c = Color.FromArgb(0x33, c.R, c.G, c.B);
        var b = new SolidColorBrush(c);
        b.Freeze();
        return b;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}

/// <summary>모드 아이콘 URL → 이미지 (인터넷에서 비동기로 받고, 작게 디코드)</summary>
public sealed class IconConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is not string url || !Uri.TryCreate(url, UriKind.Absolute, out var uri)) return null;
        try
        {
            var img = new System.Windows.Media.Imaging.BitmapImage();
            img.BeginInit();
            img.UriSource = uri;
            img.DecodePixelWidth = 104;
            img.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
            img.EndInit();
            return img;
        }
        catch
        {
            return null; // 이미지를 못 받으면 첫 글자만 보인다
        }
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
