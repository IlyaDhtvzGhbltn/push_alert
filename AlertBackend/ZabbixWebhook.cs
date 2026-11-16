using System.Security.Cryptography;
using System.Text;

namespace AlertBackend;

public class WebhookSettings
{
    public string ZabbixToken { get; set; } = "";
}

public record ZabbixEvent(string? EventId, string? EventValue, string? Severity, string? Name, string? Host, string? Message);

public static class ZabbixWebhook
{
    public static bool IsAuthorized(HttpRequest request, string token)
    {
        if (string.IsNullOrEmpty(token)) return false;
        var header = request.Headers.Authorization.ToString();
        const string prefix = "Bearer ";
        if (!header.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return false;
        return CryptographicOperations.FixedTimeEquals(
            Encoding.UTF8.GetBytes(header[prefix.Length..].Trim()),
            Encoding.UTF8.GetBytes(token));
    }

    public static AlertDto? ToAlert(ZabbixEvent e)
    {
        if (string.IsNullOrWhiteSpace(e.EventId) || string.IsNullOrWhiteSpace(e.Name)) return null;

        var severity = e.EventValue == "0" ? "resolved" : MapSeverity(e.Severity);
        return new AlertDto(
            $"zabbix-{e.EventId.Trim()}",
            severity,
            e.Name.Trim(),
            string.IsNullOrWhiteSpace(e.Message) ? null : e.Message.Trim(),
            string.IsNullOrWhiteSpace(e.Host) ? null : e.Host.Trim(),
            DateTimeOffset.UtcNow.ToUnixTimeSeconds());
    }

    private static string MapSeverity(string? nseverity) => int.TryParse(nseverity, out var n) ? n switch
    {
        >= 4 => "critical",
        >= 2 => "warning",
        _ => "info",
    } : "critical";
}
